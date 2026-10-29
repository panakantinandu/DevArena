package com.devarena.coding;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.common.ApiException;
import com.devarena.common.Difficulty;
import com.devarena.common.Ids;
import com.devarena.gamification.XpService;
import com.devarena.interviews.InterviewDtos.SubmitCodeRequest;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import jakarta.validation.Valid;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Solo coding practice: browse problems, write code, save submissions. */
@RestController
@RequestMapping("/api/coding")
public class CodingController {
    static final int XP_FIRST_SUBMISSION = 15;

    public record ProblemSummary(String slug, String title, String topic, Difficulty difficulty, int submissions) {}

    public record ProblemDetail(String slug, String title, String topic, Difficulty difficulty, String statement,
                                String examples, String constraints, String starterCode,
                                // Revealed only after your first submission.
                                String solutionOutline) {}

    public record Submission(UUID id, String language, String code, String note, Instant createdAt) {}

    public record SubmitResult(UUID id, int xpAwarded, String solutionOutline) {}

    private final JdbcTemplate jdbc;
    private final XpService xp;
    private final UserService users;
    private final CodeExecutionService execution;
    private final Clock clock;

    public CodingController(JdbcTemplate jdbc, XpService xp, UserService users, CodeExecutionService execution, Clock clock) {
        this.jdbc = jdbc;
        this.xp = xp;
        this.users = users;
        this.execution = execution;
        this.clock = clock;
    }

    @GetMapping("/execution")
    public Map<String, Object> execution() {
        return Map.of("available", execution.isAvailable(), "reason", execution.unavailableReason());
    }

    @GetMapping("/problems")
    public List<ProblemSummary> problems(@AuthenticationPrincipal AppUserPrincipal me) {
        return jdbc.query("""
                select p.slug, p.title, t.name, p.difficulty,
                       (select count(*) from code_submissions s where s.problem_id = p.id and s.user_id = ?
                          and s.interview_session_id is null) as subs
                from coding_problems p join topics t on t.id = p.topic_id
                order by p.difficulty, p.title
                """, (rs, i) -> new ProblemSummary(rs.getString(1), rs.getString(2), rs.getString(3),
                Difficulty.valueOf(rs.getString(4)), rs.getInt(5)), me.id());
    }

    @GetMapping("/problems/{slug}")
    public ProblemDetail problem(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String slug) {
        Map<String, Object> p = problemRow(slug);
        boolean submitted = submissionCount(me.id(), (UUID) p.get("id")) > 0;
        return detail(p, submitted);
    }

    @GetMapping("/problems/{slug}/submissions")
    public List<Submission> submissions(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String slug) {
        UUID problemId = (UUID) problemRow(slug).get("id");
        return jdbc.query("""
                select id, language, code, note, created_at from code_submissions
                where user_id = ? and problem_id = ? and interview_session_id is null order by created_at desc limit 20
                """, (rs, i) -> new Submission(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()), me.id(), problemId);
    }

    @PostMapping("/problems/{slug}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SubmitResult submit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String slug,
                               @Valid @RequestBody SubmitCodeRequest req) {
        Map<String, Object> p = problemRow(slug);
        UUID id = Ids.newId();
        jdbc.update("""
                insert into code_submissions(id, user_id, problem_id, language, code, note, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, me.id(), p.get("id"), req.language(), req.code(), req.note(), Timestamp.from(clock.instant()));
        UserSettings s = users.settings(me.id());
        boolean awarded = xp.award(me.id(), s.zone(), XpService.Source.CODE_PRACTICE, p.get("id").toString(),
                XP_FIRST_SUBMISSION, "Coding practice: " + p.get("title"));
        return new SubmitResult(id, awarded ? XP_FIRST_SUBMISSION : 0, (String) p.get("solution_outline"));
    }

    private Map<String, Object> problemRow(String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.*, t.name as topic_name from coding_problems p join topics t on t.id = p.topic_id where p.slug = ?
                """, slug);
        if (rows.isEmpty()) throw ApiException.notFound("Problem");
        return rows.getFirst();
    }

    private int submissionCount(UUID userId, UUID problemId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from code_submissions where user_id = ? and problem_id = ? and interview_session_id is null
                """, Integer.class, userId, problemId);
        return n == null ? 0 : n;
    }

    private static ProblemDetail detail(Map<String, Object> p, boolean reveal) {
        return new ProblemDetail((String) p.get("slug"), (String) p.get("title"), (String) p.get("topic_name"),
                Difficulty.valueOf((String) p.get("difficulty")), (String) p.get("statement"), (String) p.get("examples"),
                (String) p.get("constraints_text"), (String) p.get("starter_code"),
                reveal ? (String) p.get("solution_outline") : null);
    }
}
