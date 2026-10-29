package com.devarena.progress;

import com.devarena.common.Difficulty;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic topic mastery in [0, 1], recomputed from the last 50 attempts per topic.
 *
 * <pre>
 * weight(attempt) = difficultyWeight            EASY 1.0, MEDIUM 1.5, HARD 2.0
 *                 x recency                     0.5 ^ (ageDays / 21)   (21-day half-life)
 *                 x context                     practice/mission 1.0, battle 0.8 (time pressure invites guessing)
 *                 x independence                0.5 if the same question was attempted in the previous 24h
 *                                               (a quick retry after seeing the explanation is weaker evidence)
 * accuracy   = sum(weight x correct) / sum(weight)
 * confidence = 1 - e^(-sum(weight) / 5)         few attempts means low confidence
 * mastery    = accuracy x confidence
 * </pre>
 *
 * This is a study aid for choosing what to practise next, not a validated measure of
 * interview readiness.
 */
@Service
public class MasteryService {
    static final int WINDOW = 50;
    static final double HALF_LIFE_DAYS = 21.0;
    static final double CONFIDENCE_SCALE = 5.0;

    public record AttemptSample(UUID questionId, Difficulty difficulty, boolean correct, String context, Instant at) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MasteryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void recompute(UUID userId, Collection<UUID> topicIds) {
        Instant now = clock.instant();
        for (UUID topicId : topicIds) {
            List<AttemptSample> samples = jdbc.query("""
                    select a.question_id, q.difficulty, a.correct, a.context, a.created_at
                    from question_attempts a join questions q on q.id = a.question_id
                    where a.user_id = ? and q.topic_id = ?
                    order by a.created_at desc limit ?
                    """, (rs, i) -> new AttemptSample(rs.getObject(1, UUID.class), Difficulty.valueOf(rs.getString(2)),
                    rs.getBoolean(3), rs.getString(4), rs.getTimestamp(5).toInstant()), userId, topicId, WINDOW);
            if (samples.isEmpty()) continue;
            double mastery = compute(samples, now);
            int correct = (int) samples.stream().filter(AttemptSample::correct).count();
            jdbc.update("""
                    insert into skill_progress(user_id, topic_id, attempts, correct, mastery, last_practiced_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (user_id, topic_id) do update set attempts = excluded.attempts, correct = excluded.correct,
                        mastery = excluded.mastery, last_practiced_at = excluded.last_practiced_at, updated_at = excluded.updated_at
                    """, userId, topicId, samples.size(), correct, mastery,
                    Timestamp.from(samples.getFirst().at()), Timestamp.from(now));
        }
    }

    /** Pure scoring function; samples may be in any order. */
    public static double compute(List<AttemptSample> samples, Instant now) {
        List<AttemptSample> sorted = new ArrayList<>(samples);
        sorted.sort((a, b) -> a.at().compareTo(b.at()));
        Map<UUID, Instant> lastSeen = new HashMap<>();
        double weightSum = 0;
        double correctSum = 0;
        for (AttemptSample s : sorted) {
            double ageDays = Math.max(0, Duration.between(s.at(), now).toMinutes() / 1440.0);
            double w = s.difficulty().weight() * Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            if ("BATTLE".equals(s.context())) w *= 0.8;
            Instant prev = lastSeen.put(s.questionId(), s.at());
            if (prev != null && Duration.between(prev, s.at()).toHours() < 24) w *= 0.5;
            weightSum += w;
            if (s.correct()) correctSum += w;
        }
        if (weightSum == 0) return 0;
        double accuracy = correctSum / weightSum;
        double confidence = 1 - Math.exp(-weightSum / CONFIDENCE_SCALE);
        return Math.round(accuracy * confidence * 10_000) / 10_000.0;
    }
}
