package com.devarena.gamification;

import com.devarena.common.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way XP enters the system. Duplicate awards are impossible: the
 * (user_id, source_type, source_id) unique constraint makes a second insert a no-op.
 */
@Service
public class XpService {
    public enum Source { MISSION, PRACTICE_QUESTION, BATTLE_ANSWER, BATTLE_WIN, INTERVIEW, CODE_PRACTICE, DESIGN_PRACTICE, GYM }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public XpService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true if XP was granted now, false if this source was already rewarded. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean award(UUID userId, ZoneId zone, Source source, String sourceId, int amount, String reason) {
        if (amount <= 0) return false;
        int inserted = jdbc.update("""
                insert into xp_transactions(id, user_id, amount, source_type, source_id, reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id, source_type, source_id) do nothing
                """, Ids.newId(), userId, amount, source.name(), sourceId, reason, Timestamp.from(clock.instant()));
        if (inserted == 0) return false;
        recordActivity(userId, zone, amount, 0, 0);
        return true;
    }

    /** Adds to the user's activity row for their current local date. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordActivity(UUID userId, ZoneId zone, int xp, int missions, int questions) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        jdbc.update("""
                insert into daily_activity(user_id, activity_date, xp_earned, missions_completed, questions_answered)
                values (?, ?, ?, ?, ?)
                on conflict (user_id, activity_date) do update set
                    xp_earned = daily_activity.xp_earned + excluded.xp_earned,
                    missions_completed = daily_activity.missions_completed + excluded.missions_completed,
                    questions_answered = daily_activity.questions_answered + excluded.questions_answered
                """, userId, today, xp, missions, questions);
    }

    public long totalXp(UUID userId) {
        Long v = jdbc.queryForObject("select coalesce(sum(amount), 0) from xp_transactions where user_id = ?", Long.class, userId);
        return v == null ? 0 : v;
    }
}
