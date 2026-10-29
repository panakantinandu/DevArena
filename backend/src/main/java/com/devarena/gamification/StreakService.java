package com.devarena.gamification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * A day counts toward the streak when the user completes at least their configured
 * minimum number of missions on that <em>local</em> calendar date. Dates are computed in
 * the user's time zone with java.time, so DST transitions (23h/25h days) don't break
 * or double-count a day.
 */
@Service
public class StreakService {
    public record Streak(int current, int longest, boolean todayCounted) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StreakService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Streak streak(UUID userId, ZoneId zone, int minimumMissions) {
        List<LocalDate> days = jdbc.queryForList("""
                select activity_date from daily_activity
                where user_id = ? and missions_completed >= ?
                order by activity_date
                """, LocalDate.class, userId, minimumMissions);
        return compute(days, LocalDate.now(clock.withZone(zone)));
    }

    /** Pure function so the streak rules can be unit tested directly. */
    static Streak compute(List<LocalDate> qualifyingDaysAscending, LocalDate today) {
        Set<LocalDate> set = new HashSet<>(qualifyingDaysAscending);
        boolean todayCounted = set.contains(today);
        // A streak survives until the end of today: if today isn't done yet, count from yesterday.
        LocalDate cursor = todayCounted ? today : today.minusDays(1);
        int current = 0;
        while (set.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : qualifyingDaysAscending) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }
        return new Streak(current, Math.max(longest, current), todayCounted);
    }
}
