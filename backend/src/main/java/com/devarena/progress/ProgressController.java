package com.devarena.progress;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.common.Category;
import com.devarena.gamification.LevelService;
import com.devarena.gamification.StreakService;
import com.devarena.gamification.XpService;
import com.devarena.missions.RoadmapPlan;
import com.devarena.users.User;
import com.devarena.users.UserDtos;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProgressController {
    public record DayActivity(LocalDate date, int xp, int missions, int questions) {}

    public record SkillArea(Category category, String label, Double mastery, int attempts) {}

    public record TopicStat(String slug, String name, Category category, int attempts, int correct, Double accuracy,
                            Double mastery) {}

    public record WeekDifficulty(LocalDate weekStart, Double easy, Double medium, Double hard) {}

    public record InterviewScore(UUID sessionId, Instant at, String type, BigDecimal overall) {}

    public record FeedItem(String kind, PublicUser user, String text, Instant at) {}

    public record Welcome(String displayName, int roadmapDay, String phase, int completedToday, int dailyMinimum,
                          int currentStreak, int longestStreak, long totalXp, LevelService.LevelInfo level) {}

    public record Dashboard(Welcome welcome, List<DayActivity> week, List<SkillArea> skills, List<FeedItem> friendActivity) {}

    public record Analytics(List<DayActivity> last90Days, List<SkillArea> skills, List<TopicStat> topics,
                            List<TopicStat> mostMissed, List<WeekDifficulty> difficultyByWeek,
                            List<InterviewScore> interviewScores, Map<String, Object> battles, Map<String, Object> missions,
                            StreakService.Streak streak, int estimatedStudyMinutes30d) {}

    private static final List<Category> DASHBOARD_AREAS = List.of(Category.DSA, Category.JAVA, Category.SPRING,
            Category.DATABASES, Category.DISTRIBUTED, Category.SYSTEM_DESIGN, Category.FRONTEND, Category.BEHAVIORAL);

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final XpService xp;
    private final LevelService levels;
    private final StreakService streaks;
    private final Clock clock;

    public ProgressController(JdbcTemplate jdbc, UserService users, XpService xp, LevelService levels,
                              StreakService streaks, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.xp = xp;
        this.levels = levels;
        this.streaks = streaks;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal AppUserPrincipal me) {
        User u = users.require(me.id());
        UserSettings s = users.settings(me.id());
        ZoneId zone = s.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        long total = xp.totalXp(me.id());
        var streak = streaks.streak(me.id(), zone, s.getDailyMissionMinimum());
        int day = (int) Math.max(1, Math.min(RoadmapPlan.TOTAL_DAYS, ChronoUnit.DAYS.between(s.getRoadmapStartDate(), today) + 1));
        Integer doneToday = jdbc.queryForObject("""
                select count(*) from mission_completions c join mission_assignments a on a.id = c.assignment_id
                where a.user_id = ? and a.assigned_date = ?
                """, Integer.class, me.id(), today);
        Welcome w = new Welcome(u.getDisplayName(), day, RoadmapPlan.phaseFor(day).label, doneToday == null ? 0 : doneToday,
                s.getDailyMissionMinimum(), streak.current(), streak.longest(), total, levels.forXp(total));
        List<SkillArea> skills = skills(me.id()).stream().filter(a -> DASHBOARD_AREAS.contains(a.category())).toList();
        return new Dashboard(w, activity(me.id(), today.minusDays(6), today), skills, friendFeed(me.id()));
    }

    @GetMapping("/progress")
    public Analytics analytics(@AuthenticationPrincipal AppUserPrincipal me) {
        UserSettings s = users.settings(me.id());
        LocalDate today = LocalDate.now(clock.withZone(s.zone()));
        List<TopicStat> topics = jdbc.query("""
                select t.slug, t.name, t.category,
                       count(a.id) as attempts, count(a.id) filter (where a.correct) as correct, sp.mastery
                from topics t
                left join questions q on q.topic_id = t.id
                left join question_attempts a on a.question_id = q.id and a.user_id = ?
                left join skill_progress sp on sp.topic_id = t.id and sp.user_id = ?
                group by t.slug, t.name, t.category, sp.mastery, t.sort_order
                order by t.sort_order
                """, (rs, i) -> {
            int attempts = rs.getInt(4);
            Object m = rs.getObject(6);
            return new TopicStat(rs.getString(1), rs.getString(2), Category.valueOf(rs.getString(3)), attempts, rs.getInt(5),
                    attempts == 0 ? null : rs.getInt(5) / (double) attempts, m == null ? null : ((Number) m).doubleValue());
        }, me.id(), me.id());

        List<TopicStat> missed = jdbc.query("""
                select t.slug, t.name, t.category, count(*) as wrong
                from question_attempts a join questions q on q.id = a.question_id join topics t on t.id = q.topic_id
                where a.user_id = ? and not a.correct and a.created_at > ?
                group by t.slug, t.name, t.category order by wrong desc limit 5
                """, (rs, i) -> new TopicStat(rs.getString(1), rs.getString(2), Category.valueOf(rs.getString(3)),
                rs.getInt(4), 0, null, null), me.id(), Timestamp.from(clock.instant().minus(30, ChronoUnit.DAYS)));

        List<WeekDifficulty> byWeek = new ArrayList<>();
        Map<LocalDate, Map<String, Double>> weekly = new LinkedHashMap<>();
        jdbc.query("""
                select date_trunc('week', a.created_at at time zone ?)::date as wk, q.difficulty,
                       avg(case when a.correct then 1.0 else 0.0 end)
                from question_attempts a join questions q on q.id = a.question_id
                where a.user_id = ? and a.created_at > ?
                group by wk, q.difficulty order by wk
                """, rs -> {
            weekly.computeIfAbsent(rs.getObject(1, LocalDate.class), k -> new HashMap<>()).put(rs.getString(2), rs.getDouble(3));
        }, s.getTimeZone(), me.id(), Timestamp.from(clock.instant().minus(56, ChronoUnit.DAYS)));
        weekly.forEach((wk, m) -> byWeek.add(new WeekDifficulty(wk, m.get("EASY"), m.get("MEDIUM"), m.get("HARD"))));

        List<InterviewScore> scores = jdbc.query("""
                select s.id, e.created_at, s.interview_type, e.overall from interview_evaluations e
                join interview_sessions s on s.id = e.session_id
                where s.candidate_id = ? order by e.created_at
                """, (rs, i) -> new InterviewScore(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                rs.getString(3), rs.getBigDecimal(4)), me.id());

        Map<String, Object> battles = jdbc.queryForMap("""
                select count(*) filter (where b.status = 'COMPLETED') as played,
                       count(*) filter (where b.winner_id = ?) as won,
                       coalesce(round(avg(p.score) filter (where b.status = 'COMPLETED')), 0) as avg_score
                from quiz_battle_participants p join quiz_battles b on b.id = p.battle_id where p.user_id = ?
                """, me.id(), me.id());
        Map<String, Object> missions = jdbc.queryForMap("""
                select count(*) as assigned, count(c.id) as completed,
                       coalesce(sum(a.estimated_minutes) filter (where c.completed_at > ?), 0) as minutes30
                from mission_assignments a left join mission_completions c on c.assignment_id = a.id where a.user_id = ?
                """, Timestamp.from(clock.instant().minus(30, ChronoUnit.DAYS)), me.id());
        int minutes = ((Number) missions.get("minutes30")).intValue();

        return new Analytics(activity(me.id(), today.minusDays(89), today), skills(me.id()), topics, missed, byWeek, scores,
                battles, missions, streaks.streak(me.id(), s.zone(), s.getDailyMissionMinimum()), minutes);
    }

    private List<DayActivity> activity(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, DayActivity> rows = new HashMap<>();
        jdbc.query("""
                select activity_date, xp_earned, missions_completed, questions_answered from daily_activity
                where user_id = ? and activity_date between ? and ?
                """, rs -> {
            LocalDate d = rs.getObject(1, LocalDate.class);
            rows.put(d, new DayActivity(d, rs.getInt(2), rs.getInt(3), rs.getInt(4)));
        }, userId, from, to);
        List<DayActivity> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(rows.getOrDefault(d, new DayActivity(d, 0, 0, 0)));
        }
        return out;
    }

    /** Category mastery = mean mastery of the practised topics in that category. */
    private List<SkillArea> skills(UUID userId) {
        Map<Category, double[]> acc = new HashMap<>();
        jdbc.query("""
                select t.category, sp.mastery, sp.attempts from skill_progress sp join topics t on t.id = sp.topic_id
                where sp.user_id = ?
                """, rs -> {
            double[] a = acc.computeIfAbsent(Category.valueOf(rs.getString(1)), k -> new double[3]);
            a[0] += rs.getDouble(2);
            a[1]++;
            a[2] += rs.getInt(3);
        }, userId);
        List<SkillArea> out = new ArrayList<>();
        for (Category c : Category.values()) {
            double[] a = acc.get(c);
            out.add(new SkillArea(c, c.label(), a == null ? null : Math.round(a[0] / a[1] * 1000) / 1000.0,
                    a == null ? 0 : (int) a[2]));
        }
        return out;
    }

    /** Friends' recent public activity. Interview content and notes are never included. */
    private List<FeedItem> friendFeed(UUID me) {
        return jdbc.query("""
                with friends as (
                    select case when requester_id = ? then addressee_id else requester_id end as id
                    from friendships where status = 'ACCEPTED' and (requester_id = ? or addressee_id = ?)
                )
                select * from (
                    select 'MISSION' as kind, c.user_id as uid, a.title as text, c.completed_at as at
                    from mission_completions c join mission_assignments a on a.id = c.assignment_id
                    where c.user_id in (select id from friends)
                    union all
                    select 'BATTLE_WIN', b.winner_id, b.category, b.ended_at from quiz_battles b
                    where b.status = 'COMPLETED' and b.winner_id in (select id from friends)
                    union all
                    select 'ACHIEVEMENT', ua.user_id, ac.name, ua.unlocked_at from user_achievements ua
                    join achievements ac on ac.id = ua.achievement_id where ua.user_id in (select id from friends)
                    union all
                    select 'INTERVIEW', s.candidate_id, s.interview_type, s.updated_at from interview_sessions s
                    where s.status = 'COMPLETED' and s.candidate_id in (select id from friends)
                ) feed order by at desc limit 12
                """, (rs, i) -> {
            UUID uid = rs.getObject(2, UUID.class);
            PublicUser user = users.require(uid) == null ? null : UserDtos.toPublic(users.require(uid));
            return new FeedItem(rs.getString(1), user, rs.getString(3), rs.getTimestamp(4).toInstant());
        }, me, me, me);
    }
}
