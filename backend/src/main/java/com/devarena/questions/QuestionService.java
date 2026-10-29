package com.devarena.questions;

import com.devarena.common.ApiException;
import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.common.Ids;
import com.devarena.common.PageResponse;
import com.devarena.gamification.AchievementService;
import com.devarena.gamification.XpService;
import com.devarena.progress.MasteryService;
import com.devarena.questions.QuestionDtos.AnswerResult;
import com.devarena.questions.QuestionDtos.AttemptView;
import com.devarena.questions.QuestionDtos.OptionView;
import com.devarena.questions.QuestionDtos.QuestionFilter;
import com.devarena.questions.QuestionDtos.QuestionSummary;
import com.devarena.questions.QuestionDtos.QuestionView;
import com.devarena.questions.QuestionDtos.TopicRef;
import com.devarena.questions.QuestionDtos.TopicSummary;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
    static final int PRACTICE_XP = 5;

    /** One graded answer, used by practice, missions and battles alike. */
    public record Graded(Question question, UUID selectedOptionId, boolean correct) {}

    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final QuestionAttemptRepository attempts;
    private final JdbcTemplate jdbc;
    private final XpService xp;
    private final MasteryService mastery;
    private final AchievementService achievements;
    private final UserService users;
    private final Clock clock;

    public QuestionService(QuestionRepository questions, TopicRepository topics, QuestionAttemptRepository attempts,
                           JdbcTemplate jdbc, XpService xp, MasteryService mastery, AchievementService achievements,
                           UserService users, Clock clock) {
        this.questions = questions;
        this.topics = topics;
        this.attempts = attempts;
        this.jdbc = jdbc;
        this.xp = xp;
        this.mastery = mastery;
        this.achievements = achievements;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TopicSummary> topics(UUID userId) {
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.query("select topic_id, count(*) from questions where status = 'ACTIVE' group by topic_id",
                rs -> { counts.put(rs.getObject(1, UUID.class), rs.getInt(2)); });
        Map<UUID, Double> masteries = new HashMap<>();
        jdbc.query("select topic_id, mastery from skill_progress where user_id = ?",
                rs -> { masteries.put(rs.getObject(1, UUID.class), rs.getDouble(2)); }, userId);
        return topics.findAllByOrderBySortOrderAsc().stream()
                .map(t -> new TopicSummary(t.getId(), t.getSlug(), t.getName(), t.getCategory(),
                        t.getCategory().label(), t.getDescription(), counts.getOrDefault(t.getId(), 0),
                        masteries.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> list(UUID userId, QuestionFilter f) {
        StringBuilder where = new StringBuilder(" where q.status = 'ACTIVE'");
        List<Object> args = new ArrayList<>();
        args.add(userId); // bookmark join
        args.add(userId); // latest attempt join
        if (f.category() != null) { where.append(" and t.category = ?"); args.add(f.category().name()); }
        if (f.topic() != null && !f.topic().isBlank()) { where.append(" and t.slug = ?"); args.add(f.topic()); }
        if (f.difficulty() != null) { where.append(" and q.difficulty = ?"); args.add(f.difficulty().name()); }
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" and q.prompt ilike ?");
            args.add("%" + f.search().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (f.bookmarkedOnly()) where.append(" and b.question_id is not null");
        if (f.mistakesOnly()) where.append(" and la.correct = false");

        String from = """
                 from questions q
                 join topics t on t.id = q.topic_id
                 left join bookmarks b on b.question_id = q.id and b.user_id = ?
                 left join lateral (
                     select a.correct, a.created_at from question_attempts a
                     where a.question_id = q.id and a.user_id = ? order by a.created_at desc limit 1
                 ) la on true
                """;
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(f.size());
        pageArgs.add((long) f.page() * f.size());
        List<QuestionSummary> items = jdbc.query("""
                select q.id, q.slug, q.difficulty, q.prompt, t.id as tid, t.slug as tslug, t.name as tname, t.category,
                       b.question_id is not null as bookmarked, la.correct as last_correct, la.created_at as last_at
                """ + from + where + " order by t.sort_order, q.difficulty, q.slug limit ? offset ?",
                (rs, i) -> {
                    Timestamp lastAt = rs.getTimestamp("last_at");
                    Object lastCorrect = rs.getObject("last_correct");
                    return new QuestionSummary(rs.getObject("id", UUID.class), rs.getString("slug"),
                            new TopicRef(rs.getObject("tid", UUID.class), rs.getString("tslug"), rs.getString("tname"),
                                    Category.valueOf(rs.getString("category"))),
                            Difficulty.valueOf(rs.getString("difficulty")), rs.getString("prompt"),
                            rs.getBoolean("bookmarked"), (Boolean) lastCorrect, lastAt == null ? null : lastAt.toInstant());
                }, pageArgs.toArray());
        long totalItems = total == null ? 0 : total;
        return new PageResponse<>(items, f.page(), f.size(), totalItems, (int) Math.ceil(totalItems / (double) f.size()));
    }

    @Transactional(readOnly = true)
    public QuestionView view(UUID userId, UUID questionId) {
        Question q = questions.findOneWithOptions(questionId)
                .filter(x -> x.getStatus() == Question.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Question"));
        return toView(q, isBookmarked(userId, questionId));
    }

    public static QuestionView toView(Question q, boolean bookmarked) {
        return new QuestionView(q.getId(), q.getSlug(), TopicRef.of(q.getTopic()), q.getDifficulty(), q.getPrompt(),
                q.getCodeSnippet(), q.getOptions().stream().map(o -> new OptionView(o.getId(), o.getBody())).toList(),
                bookmarked);
    }

    @Transactional
    public AnswerResult answerPractice(UUID userId, UUID questionId, UUID optionId) {
        Question q = questions.findOneWithOptions(questionId)
                .filter(x -> x.getStatus() == Question.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Question"));
        Graded g = grade(q, optionId);
        UserSettings settings = users.settings(userId);
        record(userId, List.of(g), QuestionAttempt.Context.PRACTICE, null);
        xp.recordActivity(userId, settings.zone(), 0, 0, 1);
        int awarded = 0;
        // XP only for the first correct answer to each question, ever.
        if (g.correct() && xp.award(userId, settings.zone(), XpService.Source.PRACTICE_QUESTION, q.getId().toString(),
                PRACTICE_XP, "Practice: " + q.getTopic().getName())) {
            awarded = PRACTICE_XP;
        }
        mastery.recompute(userId, Set.of(q.getTopic().getId()));
        achievements.evaluate(userId, settings.zone(), settings.getDailyMissionMinimum());
        return new AnswerResult(g.correct(), q.correctOption().getId(), q.getExplanation(), awarded);
    }

    /** Validates that the option belongs to the question and grades it server-side. */
    public Graded grade(Question q, UUID optionId) {
        if (optionId == null) return new Graded(q, null, false);
        boolean belongs = q.getOptions().stream().anyMatch(o -> o.getId().equals(optionId));
        if (!belongs) throw ApiException.badRequest("INVALID_OPTION", "That answer doesn't belong to this question");
        return new Graded(q, optionId, q.correctOption().getId().equals(optionId));
    }

    /** Persists attempts. Callers own the transaction. */
    public void record(UUID userId, List<Graded> graded, QuestionAttempt.Context context, UUID contextId) {
        List<QuestionAttempt> rows = new ArrayList<>();
        for (Graded g : graded) {
            QuestionAttempt a = new QuestionAttempt();
            a.setId(Ids.newId());
            a.setUserId(userId);
            a.setQuestionId(g.question().getId());
            a.setSelectedOptionId(g.selectedOptionId());
            a.setCorrect(g.correct());
            a.setContext(context);
            a.setContextId(contextId);
            a.setCreatedAt(clock.instant());
            rows.add(a);
        }
        attempts.saveAll(rows);
    }

    @Transactional
    public boolean toggleBookmark(UUID userId, UUID questionId) {
        if (!questions.existsById(questionId)) throw ApiException.notFound("Question");
        int removed = jdbc.update("delete from bookmarks where user_id = ? and question_id = ?", userId, questionId);
        if (removed > 0) return false;
        jdbc.update("insert into bookmarks(user_id, question_id, created_at) values (?, ?, ?) on conflict do nothing",
                userId, questionId, Timestamp.from(clock.instant()));
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptView> history(UUID userId, int page, int size) {
        Long total = jdbc.queryForObject("select count(*) from question_attempts where user_id = ?", Long.class, userId);
        List<AttemptView> items = jdbc.query("""
                select a.question_id, q.prompt, t.name, q.difficulty, a.correct, a.context, a.created_at
                from question_attempts a join questions q on q.id = a.question_id join topics t on t.id = q.topic_id
                where a.user_id = ? order by a.created_at desc limit ? offset ?
                """, (rs, i) -> new AttemptView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                Difficulty.valueOf(rs.getString(4)), rs.getBoolean(5), rs.getString(6), rs.getTimestamp(7).toInstant()),
                userId, size, (long) page * size);
        long t = total == null ? 0 : total;
        return new PageResponse<>(items, page, size, t, (int) Math.ceil(t / (double) size));
    }

    /** Loads questions keeping the requested order. */
    @Transactional(readOnly = true)
    public List<Question> loadOrdered(List<UUID> ids) {
        Map<UUID, Question> byId = new HashMap<>();
        for (Question q : questions.findWithOptions(new LinkedHashSet<>(ids))) byId.put(q.getId(), q);
        List<Question> out = new ArrayList<>();
        for (UUID id : ids) {
            Question q = byId.get(id);
            if (q != null) out.add(q);
        }
        return out;
    }

    private boolean isBookmarked(UUID userId, UUID questionId) {
        Integer n = jdbc.queryForObject("select count(*) from bookmarks where user_id = ? and question_id = ?",
                Integer.class, userId, questionId);
        return n != null && n > 0;
    }
}
