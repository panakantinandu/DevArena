package com.devarena.gamification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AchievementService {
    public record AchievementView(String code, String name, String description, String icon, int threshold,
                                  long progress, Instant unlockedAt) {}

    private final JdbcTemplate jdbc;
    private final StreakService streaks;
    private final Clock clock;

    public AchievementService(JdbcTemplate jdbc, StreakService streaks, Clock clock) {
        this.jdbc = jdbc;
        this.streaks = streaks;
        this.clock = clock;
    }

    /** Unlocks any achievements whose criteria are now met. Returns the newly unlocked codes. */
    @Transactional
    public List<String> evaluate(UUID userId, java.time.ZoneId zone, int dailyMinimum) {
        Map<String, Long> progress = progress(userId, zone, dailyMinimum);
        List<String> unlocked = new ArrayList<>();
        List<Map<String, Object>> defs = jdbc.queryForList("""
                select a.id, a.code, a.criteria_type, a.threshold from achievements a
                where not exists (select 1 from user_achievements ua where ua.user_id = ? and ua.achievement_id = a.id)
                """, userId);
        for (Map<String, Object> d : defs) {
            long value = progress.getOrDefault((String) d.get("criteria_type"), 0L);
            if (value >= ((Number) d.get("threshold")).longValue()) {
                int n = jdbc.update("""
                        insert into user_achievements(user_id, achievement_id, unlocked_at) values (?, ?, ?)
                        on conflict do nothing
                        """, userId, d.get("id"), Timestamp.from(clock.instant()));
                if (n > 0) unlocked.add((String) d.get("code"));
            }
        }
        return unlocked;
    }

    public List<AchievementView> list(UUID userId, java.time.ZoneId zone, int dailyMinimum) {
        Map<String, Long> progress = progress(userId, zone, dailyMinimum);
        return jdbc.query("""
                select a.code, a.name, a.description, a.icon, a.criteria_type, a.threshold, ua.unlocked_at
                from achievements a
                left join user_achievements ua on ua.achievement_id = a.id and ua.user_id = ?
                order by a.criteria_type, a.threshold
                """, (rs, i) -> {
                    Timestamp t = rs.getTimestamp("unlocked_at");
                    return new AchievementView(rs.getString("code"), rs.getString("name"), rs.getString("description"),
                            rs.getString("icon"), rs.getInt("threshold"),
                            progress.getOrDefault(rs.getString("criteria_type"), 0L), t == null ? null : t.toInstant());
                }, userId);
    }

    private Map<String, Long> progress(UUID userId, java.time.ZoneId zone, int dailyMinimum) {
        long missions = count("select count(*) from mission_completions where user_id = ?", userId);
        long wins = count("select count(*) from quiz_battles where winner_id = ? and status = 'COMPLETED'", userId);
        long interviews = count("""
                select count(*) from interview_sessions
                where status = 'COMPLETED' and (interviewer_id = ? or candidate_id = ?)
                """, userId, userId);
        long correct = count("select count(*) from question_attempts where user_id = ? and correct", userId);
        long streak = streaks.streak(userId, zone, dailyMinimum).longest();
        return Map.of("MISSIONS_COMPLETED", missions, "BATTLE_WINS", wins, "INTERVIEWS_COMPLETED", interviews,
                "CORRECT_ANSWERS", correct, "STREAK", streak);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
