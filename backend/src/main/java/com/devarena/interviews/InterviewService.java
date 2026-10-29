package com.devarena.interviews;

import com.devarena.coding.CodeSyncService;
import com.devarena.coding.CodingProblem;
import com.devarena.common.AfterCommit;
import com.devarena.common.ApiException;
import com.devarena.common.Ids;
import com.devarena.design.DesignProblem;
import com.devarena.gamification.AchievementService;
import com.devarena.gamification.XpService;
import com.devarena.interviews.InterviewDtos.CandidateQuestion;
import com.devarena.interviews.InterviewDtos.CodeSnapshot;
import com.devarena.interviews.InterviewDtos.CodingBrief;
import com.devarena.interviews.InterviewDtos.CreateRequest;
import com.devarena.interviews.InterviewDtos.DesignBrief;
import com.devarena.interviews.InterviewDtos.Evaluation;
import com.devarena.interviews.InterviewDtos.EvaluationRequest;
import com.devarena.interviews.InterviewDtos.GuideQuestion;
import com.devarena.interviews.InterviewDtos.HistoryItem;
import com.devarena.interviews.InterviewDtos.InterviewerView;
import com.devarena.interviews.InterviewDtos.NoteRequest;
import com.devarena.interviews.InterviewDtos.Report;
import com.devarena.interviews.InterviewDtos.ReportQuestion;
import com.devarena.interviews.InterviewDtos.Role;
import com.devarena.interviews.InterviewDtos.RubricCriterion;
import com.devarena.interviews.InterviewDtos.SessionView;
import com.devarena.interviews.InterviewDtos.SubmitCodeRequest;
import com.devarena.realtime.RealtimeHub;
import com.devarena.realtime.RoomAccess;
import com.devarena.users.UserDtos;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserRepository;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService implements RoomAccess {
    static final int XP_INTERVIEW = 40;

    /** Fixed, documented rubric. Weights sum to 1. */
    public static final List<RubricCriterion> RUBRIC = List.of(
            new RubricCriterion("technical", "Technical correctness", 0.30, "Are the answers and code correct?"),
            new RubricCriterion("depth", "Depth of understanding", 0.20, "Do they know why, not just what?"),
            new RubricCriterion("problemSolving", "Problem-solving approach", 0.20, "Clarify, plan, iterate, test."),
            new RubricCriterion("communication", "Communication", 0.15, "Clear thinking out loud, structured answers."),
            new RubricCriterion("followUpHandling", "Handling follow-ups", 0.15, "Adapts when the question changes."));

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final UserRepository userRepo;
    private final UserService users;
    private final CodeSyncService codeSync;
    private final RealtimeHub hub;
    private final XpService xp;
    private final AchievementService achievements;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InterviewService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
                            UserRepository userRepo, UserService users, CodeSyncService codeSync, RealtimeHub hub,
                            XpService xp, AchievementService achievements, JdbcTemplate jdbc, Clock clock) {
        this.sessions = sessions;
        this.questions = questions;
        this.userRepo = userRepo;
        this.users = users;
        this.codeSync = codeSync;
        this.hub = hub;
        this.xp = xp;
        this.achievements = achievements;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String prefix() { return "interview"; }

    @Override
    public boolean canSubscribe(UUID userId, UUID sessionId) {
        return sessions.findById(sessionId).map(s -> s.isParticipant(userId)).orElse(false);
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public SessionView create(UUID interviewerId, CreateRequest req) {
        InterviewSession s = newSession(interviewerId, null, req.type(), req.difficulty(), req.durationMinutes(),
                req.questionCount());
        return view(interviewerId, s);
    }

    private InterviewSession newSession(UUID interviewerId, UUID candidateId, InterviewType type,
                                        com.devarena.common.Difficulty difficulty, int minutes, int count) {
        List<InterviewQuestion> pool = new ArrayList<>(questions.findByInterviewTypeAndDifficultyOrderBySlug(type, difficulty));
        if (pool.size() < count) {
            // Fill with the same type at other difficulties rather than failing.
            for (InterviewQuestion q : questions.findByInterviewTypeOrderBySlug(type)) {
                if (!pool.contains(q)) pool.add(q);
            }
        }
        if (pool.isEmpty()) throw ApiException.badRequest("NO_QUESTIONS", "No questions exist for that interview type yet");
        List<InterviewQuestion> first = pool.subList(0, Math.min(pool.size(), Math.max(count, 1)));
        List<InterviewQuestion> chosen = new ArrayList<>(first);
        Collections.shuffle(chosen);

        InterviewSession s = new InterviewSession();
        s.setId(Ids.newId());
        s.setRoomCode(uniqueCode());
        s.setInterviewType(type);
        s.setDifficulty(difficulty);
        s.setDurationMinutes(minutes);
        s.setInterviewerId(interviewerId);
        if (candidateId != null) {
            s.setCandidateId(candidateId);
            s.transitionTo(InterviewStatus.LOBBY);
        }
        sessions.saveAndFlush(s);
        for (int i = 0; i < chosen.size(); i++) {
            jdbc.update("insert into interview_session_questions(id, session_id, interview_question_id, position) values (?, ?, ?, ?)",
                    Ids.newId(), s.getId(), chosen.get(i).getId(), i);
        }
        return s;
    }

    private String uniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = Ids.roomCode(8);
            if (sessions.findByRoomCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not allocate an interview code");
    }

    @Transactional
    public SessionView join(UUID userId, String code) {
        InterviewSession s = sessions.findByRoomCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Interview room"));
        if (s.isParticipant(userId)) return view(userId, s);
        if (s.getCandidateId() != null) throw ApiException.conflict("ROOM_TAKEN", "This interview already has a candidate");
        if (s.getStatus() != InterviewStatus.CREATED) throw ApiException.conflict("NOT_JOINABLE", "This interview can't be joined");
        s.setCandidateId(userId);
        s.transitionTo(InterviewStatus.LOBBY);
        sessions.saveAndFlush(s);
        signal(s, "PLAYER_JOINED");
        return view(userId, s);
    }

    @Transactional
    public SessionView start(UUID userId, UUID id) {
        InterviewSession s = asInterviewer(userId, id);
        s.transitionTo(InterviewStatus.ACTIVE);
        s.setStartedAt(clock.instant());
        s.setCurrentIndex(0);
        markAsked(s.getId(), 0);
        sessions.saveAndFlush(s);
        signal(s, "INTERVIEW_STARTED");
        return view(userId, s);
    }

    @Transactional
    public SessionView next(UUID userId, UUID id) {
        InterviewSession s = asInterviewer(userId, id);
        if (s.getStatus() != InterviewStatus.ACTIVE) throw ApiException.conflict("NOT_ACTIVE", "The interview isn't active");
        int count = questionCount(s.getId());
        if (s.getCurrentIndex() + 1 >= count) throw ApiException.conflict("NO_MORE_QUESTIONS", "That was the last question");
        s.setCurrentIndex(s.getCurrentIndex() + 1);
        markAsked(s.getId(), s.getCurrentIndex());
        sessions.saveAndFlush(s);
        signal(s, "QUESTION_CHANGED");
        return view(userId, s);
    }

    @Transactional
    public SessionView finish(UUID userId, UUID id) {
        InterviewSession s = asInterviewer(userId, id);
        s.transitionTo(InterviewStatus.FEEDBACK);
        s.setEndedAt(clock.instant());
        sessions.saveAndFlush(s);
        codeSync.close(s.getId());
        signal(s, "INTERVIEW_FINISHED");
        return view(userId, s);
    }

    @Transactional
    public SessionView cancel(UUID userId, UUID id) {
        InterviewSession s = asInterviewer(userId, id);
        s.transitionTo(InterviewStatus.CANCELLED);
        s.setEndedAt(clock.instant());
        sessions.saveAndFlush(s);
        signal(s, "INTERVIEW_CANCELLED");
        return view(userId, s);
    }

    /** Starts a new session with the same format and the roles swapped. */
    @Transactional
    public SessionView rematch(UUID userId, UUID id) {
        InterviewSession old = asParticipant(userId, id);
        if (old.getCandidateId() == null) throw ApiException.conflict("NO_PARTNER", "Nobody joined that interview");
        if (old.getStatus() != InterviewStatus.COMPLETED && old.getStatus() != InterviewStatus.CANCELLED) {
            throw ApiException.conflict("STILL_RUNNING", "Finish this interview first");
        }
        int count = questionCount(old.getId());
        InterviewSession s = newSession(old.getCandidateId(), old.getInterviewerId(), old.getInterviewType(),
                old.getDifficulty(), old.getDurationMinutes(), count);
        UUID other = old.isInterviewer(userId) ? old.getCandidateId() : old.getInterviewerId();
        AfterCommit.run(() -> hub.sendToUser(other, "INTERVIEW_INVITE", Map.of("sessionId", s.getId(), "roomCode", s.getRoomCode(),
                "from", UserDtos.toPublic(users.require(userId)), "typeLabel", s.getInterviewType().label())));
        return view(userId, s);
    }

    @Transactional(readOnly = true)
    public void invite(UUID userId, UUID id, UUID friendId) {
        InterviewSession s = asInterviewer(userId, id);
        Integer friends = jdbc.queryForObject("""
                select count(*) from friendships where status = 'ACCEPTED'
                  and ((requester_id = ? and addressee_id = ?) or (requester_id = ? and addressee_id = ?))
                """, Integer.class, userId, friendId, friendId, userId);
        if (friends == null || friends == 0) throw ApiException.forbidden("You can only invite friends");
        AfterCommit.run(() -> hub.sendToUser(friendId, "INTERVIEW_INVITE", Map.of("sessionId", s.getId(), "roomCode", s.getRoomCode(),
                "from", UserDtos.toPublic(users.require(userId)), "typeLabel", s.getInterviewType().label())));
    }

    // ------------------------------------------------------------------ notes & evaluation (interviewer only)

    @Transactional
    public void saveNote(UUID userId, UUID id, NoteRequest req) {
        InterviewSession s = asInterviewer(userId, id);
        if (s.getStatus() == InterviewStatus.CANCELLED) throw ApiException.conflict("CANCELLED", "This interview was cancelled");
        if (req.sessionQuestionId() != null) {
            Integer belongs = jdbc.queryForObject("select count(*) from interview_session_questions where id = ? and session_id = ?",
                    Integer.class, req.sessionQuestionId(), id);
            if (belongs == null || belongs == 0) throw ApiException.badRequest("BAD_QUESTION", "Question isn't part of this interview");
        }
        Timestamp now = Timestamp.from(clock.instant());
        // NULL question id means the general note; NULLs aren't equal in the unique index, so update first.
        int updated = jdbc.update("""
                update interview_notes set body = ?, updated_at = ?
                where session_id = ? and author_id = ? and session_question_id is not distinct from ?
                """, req.body(), now, id, userId, req.sessionQuestionId());
        if (updated == 0) {
            jdbc.update("""
                    insert into interview_notes(id, session_id, author_id, session_question_id, body, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, Ids.newId(), id, userId, req.sessionQuestionId(), req.body(), now, now);
        }
    }

    @Transactional
    public Report evaluate(UUID userId, UUID id, EvaluationRequest req) {
        InterviewSession s = asInterviewer(userId, id);
        if (s.getStatus() != InterviewStatus.FEEDBACK) {
            throw ApiException.conflict("NOT_IN_FEEDBACK", "Finish the interview before submitting the evaluation");
        }
        BigDecimal overall = overall(req);
        jdbc.update("""
                insert into interview_evaluations(id, session_id, evaluator_id, technical, depth, problem_solving, communication,
                    follow_up_handling, overall, strengths, improvements, summary, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Ids.newId(), id, userId, req.technical(), req.depth(), req.problemSolving(), req.communication(),
                req.followUpHandling(), overall, req.strengths().strip(), req.improvements().strip(),
                req.summary() == null ? null : req.summary().strip(), Timestamp.from(clock.instant()));
        s.transitionTo(InterviewStatus.COMPLETED);
        sessions.saveAndFlush(s);
        for (UUID participant : List.of(s.getInterviewerId(), s.getCandidateId())) {
            UserSettings st = users.settings(participant);
            xp.award(participant, st.zone(), XpService.Source.INTERVIEW, s.getId().toString(), XP_INTERVIEW,
                    "Peer interview: " + s.getInterviewType().label());
            achievements.evaluate(participant, st.zone(), st.getDailyMissionMinimum());
        }
        signal(s, "INTERVIEW_COMPLETED");
        return report(userId, id);
    }

    /** Weighted mean of the rubric scores, rounded to two decimals. Purely arithmetic. */
    static BigDecimal overall(EvaluationRequest r) {
        double v = r.technical() * 0.30 + r.depth() * 0.20 + r.problemSolving() * 0.20
                + r.communication() * 0.15 + r.followUpHandling() * 0.15;
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    static String band(BigDecimal overall) {
        double v = overall.doubleValue();
        if (v >= 4.5) return "Excellent";
        if (v >= 3.5) return "Solid";
        if (v >= 2.5) return "Developing";
        return "Needs work";
    }

    // ------------------------------------------------------------------ code

    @Transactional(readOnly = true)
    public CodeSnapshot code(UUID userId, UUID id) {
        InterviewSession s = asParticipant(userId, id);
        return codeSync.snapshot(s.getId(), starterCode(s));
    }

    @Transactional
    public void submitCode(UUID userId, UUID id, SubmitCodeRequest req) {
        InterviewSession s = asParticipant(userId, id);
        if (!s.isCandidate(userId)) throw ApiException.forbidden("Only the candidate submits code");
        if (s.getStatus() != InterviewStatus.ACTIVE) throw ApiException.conflict("NOT_ACTIVE", "The interview isn't active");
        UUID problemId = currentCodingProblemId(s);
        jdbc.update("""
                insert into code_submissions(id, user_id, problem_id, interview_session_id, language, code, note, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, Ids.newId(), userId, problemId, id, req.language(), req.code(), req.note(), Timestamp.from(clock.instant()));
        signal(s, "CODE_SUBMITTED");
    }

    private String starterCode(InterviewSession s) {
        List<String> rows = jdbc.queryForList("""
                select cp.starter_code from interview_session_questions sq
                join interview_questions iq on iq.id = sq.interview_question_id
                join coding_problems cp on cp.id = iq.coding_problem_id
                where sq.session_id = ? and sq.position = ?
                """, String.class, s.getId(), s.getCurrentIndex());
        return rows.isEmpty() ? "" : rows.getFirst();
    }

    private UUID currentCodingProblemId(InterviewSession s) {
        List<UUID> rows = jdbc.queryForList("""
                select iq.coding_problem_id from interview_session_questions sq
                join interview_questions iq on iq.id = sq.interview_question_id
                where sq.session_id = ? and sq.position = ?
                """, UUID.class, s.getId(), s.getCurrentIndex());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public SessionView view(UUID userId, UUID id) {
        return view(userId, asParticipant(userId, id));
    }

    @Transactional(readOnly = true)
    public InterviewerView interviewerView(UUID userId, UUID id) {
        InterviewSession s = asInterviewer(userId, id);
        Map<UUID, String> notes = new HashMap<>();
        String[] general = {null};
        jdbc.query("select session_question_id, body from interview_notes where session_id = ? and author_id = ?", rs -> {
            UUID q = rs.getObject(1, UUID.class);
            if (q == null) general[0] = rs.getString(2);
            else notes.put(q, rs.getString(2));
        }, id, userId);

        List<GuideQuestion> guide = new ArrayList<>();
        for (Map<String, Object> row : sessionQuestions(id)) {
            InterviewQuestion q = questions.findById((UUID) row.get("interview_question_id")).orElseThrow();
            UUID sqId = (UUID) row.get("id");
            CodingProblem cp = q.getCodingProblem();
            DesignProblem dp = q.getDesignProblem();
            Timestamp asked = (Timestamp) row.get("asked_at");
            guide.add(new GuideQuestion(sqId, (Integer) row.get("position"), q.getTitle(), q.getPrompt(),
                    q.getExpectedConcepts(), q.getAnswerOutline(), q.getCommonMistakes(), q.getFollowUps(),
                    q.getRubricNotes(), cp == null ? null : cp.getSolutionOutline(),
                    dp == null ? null : dp.getDiscussionTopics(), dp == null ? null : dp.getFollowUps(),
                    notes.get(sqId), asked == null ? null : asked.toInstant(), codingBrief(cp), designBrief(dp)));
        }
        return new InterviewerView(view(userId, s), guide, general[0], RUBRIC);
    }

    @Transactional(readOnly = true)
    public Report report(UUID userId, UUID id) {
        InterviewSession s = asParticipant(userId, id);
        if (s.getStatus() != InterviewStatus.COMPLETED) throw ApiException.conflict("NOT_COMPLETED", "The report is ready once feedback is submitted");
        Map<String, Object> e = jdbc.queryForMap("select * from interview_evaluations where session_id = ?", id);
        PublicUser evaluator = userRepo.findById((UUID) e.get("evaluator_id")).map(UserDtos::toPublic).orElse(null);
        BigDecimal overall = (BigDecimal) e.get("overall");
        Evaluation ev = new Evaluation((Integer) e.get("technical"), (Integer) e.get("depth"), (Integer) e.get("problem_solving"),
                (Integer) e.get("communication"), (Integer) e.get("follow_up_handling"), overall, band(overall),
                (String) e.get("strengths"), (String) e.get("improvements"), (String) e.get("summary"), evaluator,
                ((Timestamp) e.get("created_at")).toInstant());
        // After the interview the candidate may see the expected concepts to learn from. Private notes stay private.
        List<ReportQuestion> qs = new ArrayList<>();
        for (Map<String, Object> row : sessionQuestions(id)) {
            if (row.get("asked_at") == null) continue;
            InterviewQuestion q = questions.findById((UUID) row.get("interview_question_id")).orElseThrow();
            qs.add(new ReportQuestion((Integer) row.get("position"), q.getTitle(), q.getPrompt(), q.getExpectedConcepts()));
        }
        CodeSnapshot code = codeSync.snapshot(id, "");
        Integer submissions = jdbc.queryForObject("select count(*) from code_submissions where interview_session_id = ?",
                Integer.class, id);
        return new Report(view(userId, s), ev, qs, code.code(), code.language(), submissions == null ? 0 : submissions);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(UUID userId) {
        List<HistoryItem> out = new ArrayList<>();
        for (InterviewSession s : sessions.findMine(userId, PageRequest.of(0, 50))) {
            UUID other = s.isInterviewer(userId) ? s.getCandidateId() : s.getInterviewerId();
            List<BigDecimal> overall = jdbc.queryForList("select overall from interview_evaluations where session_id = ?",
                    BigDecimal.class, s.getId());
            out.add(new HistoryItem(s.getId(), s.getInterviewType(), s.getInterviewType().label(), s.getDifficulty(),
                    s.getStatus(), s.isInterviewer(userId) ? Role.INTERVIEWER : Role.CANDIDATE,
                    other == null ? null : userRepo.findById(other).map(UserDtos::toPublic).orElse(null),
                    overall.isEmpty() ? null : overall.getFirst(), s.getCreatedAt(), s.getEndedAt()));
        }
        return out;
    }

    /** Candidate-safe view; the same shape for both roles. */
    private SessionView view(UUID userId, InterviewSession s) {
        List<CandidateQuestion> asked = new ArrayList<>();
        int count = 0;
        for (Map<String, Object> row : sessionQuestions(s.getId())) {
            count++;
            // Candidates only ever see questions that have been asked.
            if (row.get("asked_at") == null) continue;
            InterviewQuestion q = questions.findById((UUID) row.get("interview_question_id")).orElseThrow();
            asked.add(new CandidateQuestion((UUID) row.get("id"), (Integer) row.get("position"), q.getTitle(), q.getPrompt(),
                    codingBrief(q.getCodingProblem()), designBrief(q.getDesignProblem())));
        }
        CandidateQuestion current = asked.stream().filter(q -> q.position() == s.getCurrentIndex()).findFirst().orElse(null);
        Integer evaluated = jdbc.queryForObject("select count(*) from interview_evaluations where session_id = ?",
                Integer.class, s.getId());
        Instant endsAt = s.getStartedAt() == null ? null : s.getStartedAt().plusSeconds(s.getDurationMinutes() * 60L);
        return new SessionView(s.getId(), s.getRoomCode(), s.getInterviewType(), s.getInterviewType().label(),
                s.getDifficulty(), s.getDurationMinutes(), s.getStatus(),
                s.isInterviewer(userId) ? Role.INTERVIEWER : Role.CANDIDATE, publicUser(s.getInterviewerId()),
                s.getCandidateId() == null ? null : publicUser(s.getCandidateId()), s.getCurrentIndex(), count,
                s.getStartedAt(), endsAt, s.getEndedAt(), current, asked, evaluated != null && evaluated > 0,
                List.copyOf(hub.subscribers("interview:" + s.getId())));
    }

    private static CodingBrief codingBrief(CodingProblem cp) {
        return cp == null ? null : new CodingBrief(cp.getSlug(), cp.getTitle(), cp.getStatement(), cp.getExamples(),
                cp.getConstraintsText(), cp.getStarterCode());
    }

    private static DesignBrief designBrief(DesignProblem dp) {
        return dp == null ? null : new DesignBrief(dp.getId(), dp.getSlug(), dp.getTitle(), dp.getSummary(),
                dp.getFunctionalReqs(), dp.getNonFunctionalReqs());
    }

    private List<Map<String, Object>> sessionQuestions(UUID sessionId) {
        return jdbc.queryForList("""
                select id, interview_question_id, position, asked_at from interview_session_questions
                where session_id = ? order by position
                """, sessionId);
    }

    private int questionCount(UUID sessionId) {
        Integer n = jdbc.queryForObject("select count(*) from interview_session_questions where session_id = ?", Integer.class, sessionId);
        return n == null ? 0 : n;
    }

    private void markAsked(UUID sessionId, int position) {
        jdbc.update("update interview_session_questions set asked_at = coalesce(asked_at, ?) where session_id = ? and position = ?",
                Timestamp.from(clock.instant()), sessionId, position);
    }

    private PublicUser publicUser(UUID id) {
        return userRepo.findById(id).map(UserDtos::toPublic).orElse(null);
    }

    /** Notifies the room that something changed. Carries no role-specific data; clients refetch. */
    private void signal(InterviewSession s, String event) {
        Map<String, Object> data = Map.of("sessionId", s.getId(), "status", s.getStatus(), "currentIndex", s.getCurrentIndex());
        // After commit, so a client refetching on this event sees the new state.
        AfterCommit.run(() -> hub.publish("interview:" + s.getId(), event, data));
    }

    private InterviewSession asParticipant(UUID userId, UUID id) {
        InterviewSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Interview"));
        if (!s.isParticipant(userId)) throw ApiException.notFound("Interview");
        return s;
    }

    private InterviewSession asInterviewer(UUID userId, UUID id) {
        InterviewSession s = asParticipant(userId, id);
        if (!s.isInterviewer(userId)) throw ApiException.forbidden("Only the interviewer can do this");
        return s;
    }
}
