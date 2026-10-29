package com.devarena.design;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.common.ApiException;
import com.devarena.common.Difficulty;
import com.devarena.common.Ids;
import com.devarena.gamification.XpService;
import com.devarena.interviews.InterviewSession;
import com.devarena.interviews.InterviewSessionRepository;
import com.devarena.users.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * System design practice. A document is a simple box-and-arrow diagram plus notes,
 * owned by one user, either solo or attached to an interview session.
 */
@RestController
@RequestMapping("/api/design")
public class DesignController {
    static final int MAX_NODES = 150;
    static final int MAX_EDGES = 300;
    static final int XP_DESIGN = 30;

    public record ProblemSummary(UUID id, String slug, String title, Difficulty difficulty, String summary,
                                 boolean submitted) {}

    public record ProblemDetail(UUID id, String slug, String title, Difficulty difficulty, String summary,
                                List<String> functionalReqs, List<String> nonFunctionalReqs,
                                // Solo practice only, and only after you submit.
                                List<String> discussionTopics, List<String> followUps) {}

    public record DocumentView(UUID id, UUID problemId, UUID sessionId, JsonNode diagram, String notes,
                               Instant submittedAt, Instant updatedAt, long version) {}

    public record SaveRequest(@NotNull UUID problemId, UUID sessionId, @NotNull JsonNode diagram,
                              @Size(max = 20_000) String notes) {}

    private final DesignProblemRepository problems;
    private final InterviewSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final XpService xp;
    private final UserService users;
    private final Clock clock;

    public DesignController(DesignProblemRepository problems, InterviewSessionRepository sessions, JdbcTemplate jdbc,
                            ObjectMapper mapper, XpService xp, UserService users, Clock clock) {
        this.problems = problems;
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.xp = xp;
        this.users = users;
        this.clock = clock;
    }

    @GetMapping("/problems")
    @Transactional(readOnly = true)
    public List<ProblemSummary> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return problems.findAllByOrderByDifficultyAscTitleAsc().stream()
                .map(p -> new ProblemSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.getSummary(),
                        submittedSolo(me.id(), p.getId())))
                .toList();
    }

    @GetMapping("/problems/{slug}")
    @Transactional(readOnly = true)
    public ProblemDetail problem(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String slug) {
        DesignProblem p = problems.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Design problem"));
        boolean reveal = submittedSolo(me.id(), p.getId());
        return new ProblemDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.getSummary(),
                p.getFunctionalReqs(), p.getNonFunctionalReqs(), reveal ? p.getDiscussionTopics() : null,
                reveal ? p.getFollowUps() : null);
    }

    /** Your document for a problem (solo, or within a session you're the candidate of). */
    @GetMapping("/documents")
    @Transactional(readOnly = true)
    public DocumentView mine(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam UUID problemId,
                             @RequestParam(required = false) UUID sessionId) {
        if (sessionId != null) requireParticipant(me.id(), sessionId);
        return find(me.id(), problemId, sessionId);
    }

    /** The interviewer reads the candidate's diagram for the session. */
    @GetMapping("/sessions/{sessionId}/candidate-document")
    @Transactional(readOnly = true)
    public DocumentView candidateDocument(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID sessionId,
                                          @RequestParam UUID problemId) {
        InterviewSession s = requireParticipant(me.id(), sessionId);
        if (!s.isInterviewer(me.id()) || s.getCandidateId() == null) throw ApiException.forbidden("Only the interviewer can view this");
        return find(s.getCandidateId(), problemId, sessionId);
    }

    @PutMapping("/documents")
    @Transactional
    public DocumentView save(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody SaveRequest req) {
        if (!problems.existsById(req.problemId())) throw ApiException.notFound("Design problem");
        if (req.sessionId() != null) {
            InterviewSession s = requireParticipant(me.id(), req.sessionId());
            if (!s.isCandidate(me.id())) throw ApiException.forbidden("Only the candidate draws in an interview");
        }
        validateDiagram(req.diagram());
        String diagram = mapper.writeValueAsString(req.diagram());
        Timestamp now = Timestamp.from(clock.instant());
        int updated = jdbc.update("""
                update design_documents set diagram = ?::jsonb, notes = ?, updated_at = ?, version = version + 1
                where user_id = ? and problem_id = ? and interview_session_id is not distinct from ?
                """, diagram, req.notes() == null ? "" : req.notes(), now, me.id(), req.problemId(), req.sessionId());
        if (updated == 0) {
            jdbc.update("""
                    insert into design_documents(id, user_id, problem_id, interview_session_id, diagram, notes, created_at, updated_at)
                    values (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """, Ids.newId(), me.id(), req.problemId(), req.sessionId(), diagram,
                    req.notes() == null ? "" : req.notes(), now, now);
        }
        return find(me.id(), req.problemId(), req.sessionId());
    }

    @PostMapping("/documents/submit")
    @Transactional
    public Map<String, Object> submit(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam UUID problemId,
                                      @RequestParam(required = false) UUID sessionId) {
        DocumentView doc = find(me.id(), problemId, sessionId);
        if (doc == null) throw ApiException.badRequest("NOTHING_TO_SUBMIT", "Save your design first");
        if (doc.diagram().path("nodes").size() < 2 || (doc.notes() == null || doc.notes().strip().length() < 50)) {
            throw ApiException.badRequest("TOO_THIN", "Add at least two components and a few sentences of notes");
        }
        jdbc.update("update design_documents set submitted_at = coalesce(submitted_at, ?) where id = ?",
                Timestamp.from(clock.instant()), doc.id());
        int awarded = 0;
        if (sessionId == null && xp.award(me.id(), users.settings(me.id()).zone(), XpService.Source.DESIGN_PRACTICE,
                problemId.toString(), XP_DESIGN, "System design practice")) {
            awarded = XP_DESIGN;
        }
        return Map.of("xpAwarded", awarded);
    }

    private void validateDiagram(JsonNode d) {
        if (!d.isObject() || !d.path("nodes").isArray() || !d.path("edges").isArray()) {
            throw ApiException.badRequest("BAD_DIAGRAM", "Diagram must contain nodes and edges");
        }
        if (d.path("nodes").size() > MAX_NODES || d.path("edges").size() > MAX_EDGES) {
            throw ApiException.badRequest("DIAGRAM_TOO_LARGE", "That diagram is too large");
        }
        for (JsonNode n : d.path("nodes")) {
            if (!n.path("id").isString() || n.path("label").asString("").length() > 120) {
                throw ApiException.badRequest("BAD_DIAGRAM", "Each component needs an id and a short label");
            }
        }
    }

    private DocumentView find(UUID userId, UUID problemId, UUID sessionId) {
        List<DocumentView> rows = jdbc.query("""
                select id, problem_id, interview_session_id, diagram::text, notes, submitted_at, updated_at, version
                from design_documents where user_id = ? and problem_id = ? and interview_session_id is not distinct from ?
                """, (rs, i) -> {
            Timestamp sub = rs.getTimestamp(6);
            return new DocumentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                    mapper.readTree(rs.getString(4)), rs.getString(5), sub == null ? null : sub.toInstant(),
                    rs.getTimestamp(7).toInstant(), rs.getLong(8));
        }, userId, problemId, sessionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean submittedSolo(UUID userId, UUID problemId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from design_documents
                where user_id = ? and problem_id = ? and interview_session_id is null and submitted_at is not null
                """, Integer.class, userId, problemId);
        return n != null && n > 0;
    }

    private InterviewSession requireParticipant(UUID userId, UUID sessionId) {
        InterviewSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Interview"));
        if (!s.isParticipant(userId)) throw ApiException.notFound("Interview");
        return s;
    }
}
