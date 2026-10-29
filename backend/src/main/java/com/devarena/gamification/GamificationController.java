package com.devarena.gamification;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.common.ApiException;
import com.devarena.gamification.AchievementService.AchievementView;
import com.devarena.gamification.LevelService.Level;
import com.devarena.gamification.LevelService.LevelInfo;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {
    public enum Period { WEEK, MONTH, ALL }

    public enum Metric { XP, QUIZ_WINS, INTERVIEWS, CODING }

    public record Me(LevelInfo level, List<Level> levels, StreakService.Streak streak, List<AchievementView> achievements) {}

    public record Row(int rank, PublicUser user, long value, boolean you) {}

    public record Leaderboard(Metric metric, Period period, String scope, List<Row> rows) {}

    private final XpService xp;
    private final LevelService levels;
    private final StreakService streaks;
    private final AchievementService achievements;
    private final UserService users;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GamificationController(XpService xp, LevelService levels, StreakService streaks, AchievementService achievements,
                                  UserService users, JdbcTemplate jdbc, Clock clock) {
        this.xp = xp;
        this.levels = levels;
        this.streaks = streaks;
        this.achievements = achievements;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal AppUserPrincipal me) {
        UserSettings s = users.settings(me.id());
        return new Me(levels.forXp(xp.totalXp(me.id())), levels.all(),
                streaks.streak(me.id(), s.zone(), s.getDailyMissionMinimum()),
                achievements.list(me.id(), s.zone(), s.getDailyMissionMinimum()));
    }

    /**
     * Leaderboards are scoped to you and your friends by default; scope=all ranks every user.
     * Only aggregate counts are exposed, never interview notes or scores.
     */
    @GetMapping("/leaderboard")
    public Leaderboard leaderboard(@AuthenticationPrincipal AppUserPrincipal me,
                                   @RequestParam(defaultValue = "XP") Metric metric,
                                   @RequestParam(defaultValue = "WEEK") Period period,
                                   @RequestParam(defaultValue = "friends") String scope) {
        if (!scope.equals("friends") && !scope.equals("all")) throw ApiException.badRequest("BAD_SCOPE", "Unknown scope");
        Timestamp since = Timestamp.from(periodStart(period, users.settings(me.id()).zone()));
        String valueSql = switch (metric) {
            case XP -> "(select coalesce(sum(x.amount), 0) from xp_transactions x where x.user_id = u.id and x.created_at >= ?)";
            case QUIZ_WINS -> "(select count(*) from quiz_battles b where b.winner_id = u.id and b.status = 'COMPLETED' and b.ended_at >= ?)";
            case INTERVIEWS -> """
                    (select count(*) from interview_sessions s where s.status = 'COMPLETED'
                       and (s.interviewer_id = u.id or s.candidate_id = u.id) and s.updated_at >= ?)""";
            case CODING -> """
                    (select count(distinct c.problem_id) from code_submissions c
                       where c.user_id = u.id and c.problem_id is not null and c.created_at >= ?)""";
        };
        String scopeSql = scope.equals("all") ? "" : """
                 where u.id = ? or exists (select 1 from friendships f where f.status = 'ACCEPTED'
                   and ((f.requester_id = ? and f.addressee_id = u.id) or (f.addressee_id = ? and f.requester_id = u.id)))
                """;
        List<Object> args = new ArrayList<>();
        args.add(since);
        if (!scope.equals("all")) {
            args.add(me.id());
            args.add(me.id());
            args.add(me.id());
        }
        List<Object[]> raw = jdbc.query("select u.id, u.username, u.display_name, u.avatar_url, " + valueSql
                + " as v from users u" + scopeSql + " order by v desc, u.username limit 100",
                (rs, i) -> new Object[]{new PublicUser(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4)), rs.getLong(5)}, args.toArray());
        List<Row> rows = new ArrayList<>();
        long prev = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < raw.size(); i++) {
            long v = (Long) raw.get(i)[1];
            if (v != prev) rank = i + 1; // ties share a rank
            prev = v;
            PublicUser u = (PublicUser) raw.get(i)[0];
            rows.add(new Row(rank, u, v, u.id().equals(me.id())));
        }
        return new Leaderboard(metric, period, scope, rows);
    }

    /** Week starts on Monday and month on the 1st, both in the viewer's own time zone. */
    Instant periodStart(Period p, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return switch (p) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
            case MONTH -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
            case ALL -> Instant.EPOCH;
        };
    }
}
