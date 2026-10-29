package com.devarena.missions;

import com.devarena.common.ApiException;
import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.common.Ids;
import com.devarena.design.DesignProblem;
import com.devarena.design.DesignProblemRepository;
import com.devarena.gamification.AchievementService;
import com.devarena.gamification.XpService;
import com.devarena.interviews.InterviewQuestion;
import com.devarena.interviews.InterviewQuestionRepository;
import com.devarena.missions.MissionDtos.DesignStudyItem;
import com.devarena.missions.MissionDtos.InterviewStudyItem;
import com.devarena.missions.MissionDtos.MissionCard;
import com.devarena.missions.MissionDtos.MissionDetail;
import com.devarena.missions.MissionDtos.QuestionReview;
import com.devarena.missions.MissionDtos.SubmitRequest;
import com.devarena.missions.MissionDtos.SubmitResult;
import com.devarena.missions.MissionDtos.TodayResponse;
import com.devarena.missions.MissionPlanner.Planned;
import com.devarena.progress.MasteryService;
import com.devarena.questions.Question;
import com.devarena.questions.QuestionAttempt;
import com.devarena.questions.QuestionService;
import com.devarena.questions.QuestionService.Graded;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MissionService {
    private final MissionAssignmentRepository assignments;
    private final QuestionService questionService;
    private final InterviewQuestionRepository interviewQuestions;
    private final DesignProblemRepository designProblems;
    private final UserService users;
    private final XpService xp;
    private final MasteryService mastery;
    private final AchievementService achievements;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate newTx;

    public MissionService(MissionAssignmentRepository assignments, QuestionService questionService,
                          InterviewQuestionRepository interviewQuestions, DesignProblemRepository designProblems,
                          UserService users, XpService xp, MasteryService mastery, AchievementService achievements,
                          JdbcTemplate jdbc, ObjectMapper mapper, Clock clock, PlatformTransactionManager txManager) {
        this.assignments = assignments;
        this.questionService = questionService;
        this.interviewQuestions = interviewQuestions;
        this.designProblems = designProblems;
        this.users = users;
        this.xp = xp;
        this.mastery = mastery;
        this.achievements = achievements;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ today

    public TodayResponse today(UUID userId) {
        UserSettings s = users.settings(userId);
        ZoneId zone = s.zone();
        LocalDate date = LocalDate.now(clock.withZone(zone));
        int day = roadmapDay(s.getRoadmapStartDate(), date);

        List<MissionAssignment> list = assignments.findByUserIdAndAssignedDateOrderBySlot(userId, date);
        if (list.isEmpty()) {
            try {
                newTx.executeWithoutResult(st -> generate(userId, date, day, s));
            } catch (DataIntegrityViolationException raced) {
                // Another request generated today's plan at the same moment; use theirs.
            }
            list = assignments.findByUserIdAndAssignedDateOrderBySlot(userId, date);
        }
        Map<UUID, Map<String, Object>> done = completions(list.stream().map(MissionAssignment::getId).toList());
        List<MissionCard> cards = list.stream().map(a -> card(a, done.get(a.getId()))).toList();
        int completed = (int) cards.stream().filter(MissionCard::completed).count();
        return new TodayResponse(date, day, RoadmapPlan.phaseFor(day).label, s.getDailyMissionMinimum(), completed, cards);
    }

    static int roadmapDay(LocalDate start, LocalDate today) {
        long d = ChronoUnit.DAYS.between(start, today) + 1;
        return (int) Math.max(1, Math.min(RoadmapPlan.TOTAL_DAYS, d));
    }

    private void generate(UUID userId, LocalDate date, int day, UserSettings s) {
        List<Planned> plan = MissionPlanner.plan(buildInput(userId, date, day, s));
        Map<String, UUID> topicIds = new HashMap<>();
        jdbc.query("select slug, id from topics", rs -> { topicIds.put(rs.getString(1), rs.getObject(2, UUID.class)); });
        for (Planned p : plan) {
            MissionAssignment a = new MissionAssignment();
            a.setId(Ids.newId());
            a.setUserId(userId);
            a.setAssignedDate(date);
            a.setRoadmapDay(day);
            a.setSlot(p.slot());
            a.setMissionType(p.kind());
            a.setCategory(p.category());
            a.setTopicId(p.topicSlug() == null ? null : topicIds.get(p.topicSlug()));
            a.setTitle(p.title());
            a.setDifficulty(p.difficulty());
            a.setEstimatedMinutes(p.minutes());
            a.setXpReward(p.xp());
            a.setRevision(p.revision());
            Map<String, Object> payload = new LinkedHashMap<>();
            if (!p.questionIds().isEmpty()) payload.put("questionIds", p.questionIds());
            if (!p.interviewQuestionIds().isEmpty()) payload.put("interviewQuestionIds", p.interviewQuestionIds());
            if (p.designProblemId() != null) payload.put("designProblemId", p.designProblemId());
            a.setPayload(mapper.writeValueAsString(payload));
            a.setCreatedAt(clock.instant());
            assignments.save(a);
        }
        assignments.flush();
    }

    private MissionPlanner.Input buildInput(UUID userId, LocalDate date, int day, UserSettings s) {
        Map<String, MissionPlanner.TopicStat> topics = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        jdbc.query("""
                select t.slug, t.name, t.category, sp.mastery, sp.last_practiced_at
                from topics t left join skill_progress sp on sp.topic_id = t.id and sp.user_id = ?
                """, rs -> {
            Timestamp last = rs.getTimestamp(5);
            Object m = rs.getObject(4);
            topics.put(rs.getString(1), new MissionPlanner.TopicStat(Category.valueOf(rs.getString(3)),
                    m == null ? null : ((Number) m).doubleValue(), last == null ? null : last.toInstant()));
            names.put(rs.getString(1), rs.getString(2));
        }, userId);

        Map<String, List<MissionPlanner.PoolQuestion>> byTopic = new HashMap<>();
        jdbc.query("""
                select q.id, t.slug, q.difficulty, max(a.created_at)
                from questions q join topics t on t.id = q.topic_id
                left join question_attempts a on a.question_id = q.id and a.user_id = ?
                where q.status = 'ACTIVE'
                group by q.id, t.slug, q.difficulty
                """, rs -> {
            Timestamp last = rs.getTimestamp(4);
            byTopic.computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(new MissionPlanner.PoolQuestion(
                    rs.getObject(1, UUID.class), rs.getString(2), Difficulty.valueOf(rs.getString(3)),
                    last == null ? null : last.toInstant()));
        }, userId);

        List<UUID> mistakes = jdbc.queryForList("""
                select x.question_id from (
                    select distinct on (a.question_id) a.question_id, a.correct, a.created_at
                    from question_attempts a join questions q on q.id = a.question_id and q.status = 'ACTIVE'
                    where a.user_id = ? and a.created_at > ?
                    order by a.question_id, a.created_at desc
                ) x where not x.correct order by x.created_at desc limit 5
                """, UUID.class, userId, Timestamp.from(clock.instant().minus(Duration.ofDays(14))));

        List<MissionPlanner.PoolInterviewQuestion> iq = jdbc.query("""
                select id, interview_type, difficulty from interview_questions
                where coding_problem_id is null and design_problem_id is null order by slug
                """, (rs, i) -> new MissionPlanner.PoolInterviewQuestion(rs.getObject(1, UUID.class), rs.getString(2),
                Difficulty.valueOf(rs.getString(3))));

        List<MissionPlanner.PoolDesign> designs = jdbc.query("select id, title, difficulty from design_problems order by slug",
                (rs, i) -> new MissionPlanner.PoolDesign(rs.getObject(1, UUID.class), rs.getString(2),
                        Difficulty.valueOf(rs.getString(3))));

        long seed = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits() ^ (date.toEpochDay() * 0x9E3779B97F4A7C15L);
        return new MissionPlanner.Input(day, s.getPrepLevel(), s.getDailyStudyMinutes(), s.getDailyMissionMinimum(), seed,
                topics, byTopic, mistakes, iq, designs, names);
    }

    // ------------------------------------------------------------------ detail & submit

    @Transactional(readOnly = true)
    public MissionDetail detail(UUID userId, UUID missionId) {
        MissionAssignment a = owned(userId, missionId);
        return buildDetail(a, completions(List.of(a.getId())).get(a.getId()));
    }

    @Transactional
    public SubmitResult submit(UUID userId, UUID missionId, SubmitRequest req) {
        MissionAssignment a = owned(userId, missionId);
        UserSettings s = users.settings(userId);
        Map<String, Object> existing = completions(List.of(a.getId())).get(a.getId());
        if (existing != null) {
            // Already completed: return the review, never award again.
            return new SubmitResult(false, 0, (int) existing.get("correct"), (int) existing.get("total"), List.of(),
                    buildDetail(a, existing));
        }
        LocalDate today = LocalDate.now(clock.withZone(s.zone()));
        if (!a.getAssignedDate().equals(today)) {
            throw ApiException.conflict("MISSION_EXPIRED", "This mission was for " + a.getAssignedDate() + " and can only be reviewed now");
        }

        JsonNode payload = mapper.readTree(a.getPayload());
        int correct = 0;
        int total = 0;
        Map<String, Object> response = new LinkedHashMap<>();
        List<Graded> graded = new ArrayList<>();
        switch (a.getMissionType()) {
            case QUESTION_SET -> {
                List<UUID> ids = uuids(payload.get("questionIds"));
                Map<UUID, UUID> answers = req.answers() == null ? Map.of() : req.answers();
                if (!answers.keySet().containsAll(ids)) {
                    throw ApiException.badRequest("INCOMPLETE", "Answer every question before submitting");
                }
                for (Question q : questionService.loadOrdered(ids)) {
                    Graded g = questionService.grade(q, answers.get(q.getId()));
                    graded.add(g);
                    if (g.correct()) correct++;
                }
                total = graded.size();
                Map<String, String> stored = new LinkedHashMap<>();
                graded.forEach(g -> stored.put(g.question().getId().toString(), String.valueOf(g.selectedOptionId())));
                response.put("answers", stored);
            }
            case INTERVIEW_STUDY, DESIGN_STUDY -> {
                int min = a.getMissionType() == MissionPlanner.Kind.DESIGN_STUDY ? 150 : 60;
                if (req.notes() == null || req.notes().strip().length() < min) {
                    throw ApiException.badRequest("NOTES_TOO_SHORT", "Write at least " + min + " characters before revealing the guide");
                }
                if (req.selfRating() == null) throw ApiException.badRequest("RATING_REQUIRED", "Rate your answer from 1 to 5");
                response.put("notes", req.notes().strip());
                response.put("selfRating", req.selfRating());
            }
        }

        int inserted = jdbc.update("""
                insert into mission_completions(id, assignment_id, user_id, correct_count, total_count, response, completed_at)
                values (?, ?, ?, ?, ?, ?::jsonb, ?) on conflict (assignment_id) do nothing
                """, Ids.newId(), a.getId(), userId, correct, total, mapper.writeValueAsString(response),
                Timestamp.from(clock.instant()));
        if (inserted == 0) {
            Map<String, Object> raced = completions(List.of(a.getId())).get(a.getId());
            return new SubmitResult(false, 0, (int) raced.get("correct"), (int) raced.get("total"), List.of(), buildDetail(a, raced));
        }

        if (!graded.isEmpty()) {
            questionService.record(userId, graded, QuestionAttempt.Context.MISSION, a.getId());
            mastery.recompute(userId, graded.stream().map(g -> g.question().getTopic().getId()).collect(Collectors.toSet()));
        }
        int award = xpFor(a, correct, total);
        boolean granted = xp.award(userId, s.zone(), XpService.Source.MISSION, a.getId().toString(), award,
                "Mission: " + a.getTitle());
        xp.recordActivity(userId, s.zone(), 0, 1, total);
        List<String> unlocked = achievements.evaluate(userId, s.zone(), s.getDailyMissionMinimum());
        Map<String, Object> done = completions(List.of(a.getId())).get(a.getId());
        return new SubmitResult(true, granted ? award : 0, correct, total, unlocked, buildDetail(a, done));
    }

    /** Question sets pay 50% for finishing plus 50% scaled by accuracy. Study missions pay in full. */
    static int xpFor(MissionAssignment a, int correct, int total) {
        if (a.getMissionType() != MissionPlanner.Kind.QUESTION_SET || total == 0) return a.getXpReward();
        return (int) Math.round(a.getXpReward() * (0.5 + 0.5 * correct / (double) total));
    }

    private MissionDetail buildDetail(MissionAssignment a, Map<String, Object> completion) {
        JsonNode payload = mapper.readTree(a.getPayload());
        boolean done = completion != null;
        MissionCard card = card(a, completion);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = done ? (Map<String, Object>) completion.get("response") : null;

        switch (a.getMissionType()) {
            case QUESTION_SET -> {
                List<Question> qs = questionService.loadOrdered(uuids(payload.get("questionIds")));
                var views = qs.stream().map(q -> QuestionService.toView(q, false)).toList();
                List<QuestionReview> review = null;
                if (done) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> answers = (Map<String, String>) response.getOrDefault("answers", Map.of());
                    review = qs.stream().map(q -> {
                        String mine = answers.get(q.getId().toString());
                        UUID yours = mine == null || "null".equals(mine) ? null : UUID.fromString(mine);
                        UUID right = q.correctOption().getId();
                        return new QuestionReview(q.getId(), right, yours, right.equals(yours), q.getExplanation());
                    }).toList();
                }
                return new MissionDetail(card, views, review, null, null, response);
            }
            case INTERVIEW_STUDY -> {
                List<InterviewStudyItem> items = interviewQuestions.findAllById(uuids(payload.get("interviewQuestionIds")))
                        .stream().map(q -> studyItem(q, done)).toList();
                return new MissionDetail(card, null, null, items, null, response);
            }
            case DESIGN_STUDY -> {
                DesignProblem d = designProblems.findById(UUID.fromString(payload.get("designProblemId").asString()))
                        .orElseThrow(() -> ApiException.notFound("Design problem"));
                DesignStudyItem item = new DesignStudyItem(d.getId(), d.getSlug(), d.getTitle(), d.getSummary(),
                        d.getFunctionalReqs(), d.getNonFunctionalReqs(),
                        done ? d.getDiscussionTopics() : null, done ? d.getFollowUps() : null);
                return new MissionDetail(card, null, null, null, item, response);
            }
            default -> throw new IllegalStateException();
        }
    }

    private static InterviewStudyItem studyItem(InterviewQuestion q, boolean reveal) {
        return new InterviewStudyItem(q.getId(), q.getTitle(), q.getPrompt(), q.getInterviewType().label(),
                reveal ? q.getExpectedConcepts() : null, reveal ? q.getAnswerOutline() : null,
                reveal ? q.getCommonMistakes() : null, reveal ? q.getFollowUps() : null);
    }

    private MissionCard card(MissionAssignment a, Map<String, Object> completion) {
        String topicSlug = a.getTopicId() == null ? null
                : jdbc.queryForObject("select slug from topics where id = ?", String.class, a.getTopicId());
        boolean done = completion != null;
        return new MissionCard(a.getId(), a.getSlot(), a.getMissionType().name(), a.getCategory(),
                a.getCategory().label(), topicSlug, a.getTitle(), a.getDifficulty(), a.getEstimatedMinutes(),
                a.getXpReward(), a.isRevision(), done, done ? (Integer) completion.get("correct") : null,
                done ? (Integer) completion.get("total") : null,
                done ? ((Timestamp) completion.get("completedAt")).toInstant() : null);
    }

    private Map<UUID, Map<String, Object>> completions(List<UUID> assignmentIds) {
        Map<UUID, Map<String, Object>> out = new HashMap<>();
        if (assignmentIds.isEmpty()) return out;
        String in = String.join(",", assignmentIds.stream().map(x -> "?").toList());
        jdbc.query("select assignment_id, correct_count, total_count, response::text, completed_at from mission_completions"
                + " where assignment_id in (" + in + ")", rs -> {
            Map<String, Object> m = new HashMap<>();
            m.put("correct", rs.getInt(2));
            m.put("total", rs.getInt(3));
            String json = rs.getString(4);
            m.put("response", json == null ? Map.of() : mapper.readValue(json, Map.class));
            m.put("completedAt", rs.getTimestamp(5));
            out.put(rs.getObject(1, UUID.class), m);
        }, assignmentIds.toArray());
        return out;
    }

    private MissionAssignment owned(UUID userId, UUID missionId) {
        MissionAssignment a = assignments.findById(missionId).orElseThrow(() -> ApiException.notFound("Mission"));
        // Same 404 for "not yours" as for "doesn't exist", so IDs can't be probed.
        if (!a.getUserId().equals(userId)) throw ApiException.notFound("Mission");
        return a;
    }

    private static List<UUID> uuids(JsonNode arr) {
        List<UUID> out = new ArrayList<>();
        if (arr != null) arr.forEach(n -> out.add(UUID.fromString(n.asString())));
        return out;
    }
}
