package com.devarena.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devarena.battles.BattleStatus;
import com.devarena.common.ApiException;
import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.gamification.LevelService;
import com.devarena.interviews.InterviewStatus;
import com.devarena.missions.MissionPlanner;
import com.devarena.missions.MissionPlanner.Kind;
import com.devarena.missions.MissionPlanner.PoolQuestion;
import com.devarena.missions.MissionPlanner.TopicStat;
import com.devarena.progress.MasteryService;
import com.devarena.progress.MasteryService.AttemptSample;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainRulesTest {

    // ------------------------------------------------------------------ streaks

    @Test
    void streakCountsConsecutiveLocalDaysAndSurvivesUntilEndOfToday() throws Exception {
        LocalDate today = LocalDate.of(2026, 3, 10);
        List<LocalDate> days = List.of(today.minusDays(3), today.minusDays(2), today.minusDays(1));
        Object s = streak(days, today);
        assertThat(field(s, "current")).isEqualTo(3); // today not done yet, streak still alive
        assertThat(field(s, "todayCounted")).isEqualTo(false);

        Object broken = streak(List.of(today.minusDays(3)), today);
        assertThat(field(broken, "current")).isEqualTo(0);
        assertThat(field(broken, "longest")).isEqualTo(1);
    }

    @Test
    void dstChangeDayCountsAsOneDay() throws Exception {
        // 2026-03-29 is 23 hours long in Europe/Berlin. Local dates still step by one.
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDate dstDay = LocalDate.of(2026, 3, 29);
        assertThat(ChronoUnit.HOURS.between(dstDay.atStartOfDay(berlin), dstDay.plusDays(1).atStartOfDay(berlin))).isEqualTo(23);
        Object s = streak(List.of(dstDay.minusDays(1), dstDay, dstDay.plusDays(1)), dstDay.plusDays(1));
        assertThat(field(s, "current")).isEqualTo(3);
        // Late-evening activity in Berlin is still that Berlin date, not the next UTC date.
        ZonedDateTime lateEvening = ZonedDateTime.of(2026, 3, 29, 23, 30, 0, 0, berlin);
        assertThat(lateEvening.toLocalDate()).isEqualTo(dstDay);
        assertThat(lateEvening.withZoneSameInstant(ZoneId.of("UTC")).toLocalDate()).isEqualTo(dstDay);
    }

    private static Object streak(List<LocalDate> days, LocalDate today) throws Exception {
        Method m = Class.forName("com.devarena.gamification.StreakService").getDeclaredMethod("compute", List.class, LocalDate.class);
        m.setAccessible(true);
        return m.invoke(null, days, today);
    }

    private static Object field(Object record, String name) throws Exception {
        return record.getClass().getMethod(name).invoke(record);
    }

    // ------------------------------------------------------------------ mastery

    @Test
    void masteryRewardsAccuracyDifficultyAndVolume() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        List<AttemptSample> fewCorrect = samples(2, true, Difficulty.EASY, now);
        List<AttemptSample> manyCorrect = samples(10, true, Difficulty.EASY, now);
        List<AttemptSample> manyHalf = new ArrayList<>(samples(5, true, Difficulty.EASY, now));
        manyHalf.addAll(samples(5, false, Difficulty.EASY, now));
        double few = MasteryService.compute(fewCorrect, now);
        double many = MasteryService.compute(manyCorrect, now);
        double half = MasteryService.compute(manyHalf, now);
        assertThat(many).isGreaterThan(few);   // more evidence, more confidence
        assertThat(many).isGreaterThan(half);  // accuracy matters
        assertThat(many).isBetween(0.0, 1.0);
        assertThat(MasteryService.compute(samples(6, true, Difficulty.HARD, now), now))
                .isGreaterThan(MasteryService.compute(samples(6, true, Difficulty.EASY, now), now));
    }

    @Test
    void oldAttemptsCountLessThanRecentOnes() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        List<AttemptSample> oldWrongRecentRight = new ArrayList<>(samples(5, false, Difficulty.MEDIUM, now.minus(90, ChronoUnit.DAYS)));
        oldWrongRecentRight.addAll(samples(5, true, Difficulty.MEDIUM, now));
        List<AttemptSample> oldRightRecentWrong = new ArrayList<>(samples(5, true, Difficulty.MEDIUM, now.minus(90, ChronoUnit.DAYS)));
        oldRightRecentWrong.addAll(samples(5, false, Difficulty.MEDIUM, now));
        assertThat(MasteryService.compute(oldWrongRecentRight, now)).isGreaterThan(MasteryService.compute(oldRightRecentWrong, now));
    }

    private static List<AttemptSample> samples(int n, boolean correct, Difficulty d, Instant at) {
        List<AttemptSample> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new AttemptSample(UUID.randomUUID(), d, correct, "PRACTICE", at.minusSeconds(i * 3600L * 30)));
        return out;
    }

    // ------------------------------------------------------------------ battle scoring and state machines

    @Test
    void battlePointsAreBasePlusSpeedBonus() throws Exception {
        Method points = Class.forName("com.devarena.battles.BattleService").getDeclaredMethod("points", boolean.class, long.class, long.class);
        points.setAccessible(true);
        assertThat(points.invoke(null, false, 100L, 20_000L)).isEqualTo(0);
        assertThat(points.invoke(null, true, 0L, 20_000L)).isEqualTo(125);
        assertThat(points.invoke(null, true, 10_000L, 20_000L)).isEqualTo(113); // 100 + round(12.5)
        assertThat(points.invoke(null, true, 20_000L, 20_000L)).isEqualTo(100);
    }

    @Test
    void battleTransitionsRejectSkippingStates() {
        assertThat(BattleStatus.WAITING.canMoveTo(BattleStatus.IN_PROGRESS)).isFalse();
        assertThat(BattleStatus.READY.canMoveTo(BattleStatus.IN_PROGRESS)).isTrue();
        assertThat(BattleStatus.COMPLETED.canMoveTo(BattleStatus.IN_PROGRESS)).isFalse();
        assertThat(BattleStatus.CANCELLED.canMoveTo(BattleStatus.WAITING)).isFalse();
        var b = new com.devarena.battles.QuizBattle();
        assertThatThrownBy(() -> b.transitionTo(BattleStatus.COMPLETED)).isInstanceOf(ApiException.class);
    }

    @Test
    void interviewTransitionsFollowTheFlow() {
        assertThat(InterviewStatus.CREATED.canMoveTo(InterviewStatus.ACTIVE)).isFalse();
        assertThat(InterviewStatus.LOBBY.canMoveTo(InterviewStatus.ACTIVE)).isTrue();
        assertThat(InterviewStatus.ACTIVE.canMoveTo(InterviewStatus.FEEDBACK)).isTrue();
        assertThat(InterviewStatus.FEEDBACK.canMoveTo(InterviewStatus.CANCELLED)).isFalse();
        assertThat(InterviewStatus.COMPLETED.canMoveTo(InterviewStatus.ACTIVE)).isFalse();
    }

    @Test
    void levelsDependOnlyOnXp() {
        LevelService levels = new LevelService(new LevelService.LevelProperties(null));
        assertThat(levels.forXp(0).title()).isEqualTo("Java Apprentice");
        assertThat(levels.forXp(299).level()).isEqualTo(1);
        assertThat(levels.forXp(300).level()).isEqualTo(2);
        assertThat(levels.forXp(1_000_000).nextLevelXp()).isNull();
    }

    // ------------------------------------------------------------------ mission planner

    @Test
    void plannerIsDeterministicAndPrioritisesWeakAreas() {
        MissionPlanner.Input input = plannerInput(Map.of("java-oop", 0.95, "sql-joins", 0.10), 60, 2, List.of());
        List<MissionPlanner.Planned> a = MissionPlanner.plan(input);
        List<MissionPlanner.Planned> b = MissionPlanner.plan(input);
        assertThat(a).isEqualTo(b);
        assertThat(a.getFirst().category()).isEqualTo(Category.DSA);
        // With a tight budget the weak Databases area beats the strong Java area.
        MissionPlanner.Input tight = plannerInput(Map.of("java-oop", 0.95, "java-collections", 0.95, "java-exceptions", 0.95,
                "java-generics", 0.95, "java-streams", 0.95, "sql-joins", 0.10, "normalization", 0.1, "sql-indexes", 0.1), 20, 2, List.of());
        List<Category> cats = MissionPlanner.plan(tight).stream().map(MissionPlanner.Planned::category).toList();
        assertThat(cats).contains(Category.DATABASES).doesNotContain(Category.JAVA);
    }

    @Test
    void plannerAddsRevisionForRecentMistakesAndRespectsDailyMinimum() {
        List<UUID> mistakes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<MissionPlanner.Planned> plan = MissionPlanner.plan(plannerInput(Map.of(), 15, 4, mistakes));
        assertThat(plan.stream().anyMatch(MissionPlanner.Planned::revision)).isTrue();
        assertThat(plan.size()).isGreaterThanOrEqualTo(4);
        assertThat(plan.stream().filter(p -> p.kind() == Kind.QUESTION_SET).allMatch(p -> !p.questionIds().isEmpty())).isTrue();
    }

    private static MissionPlanner.Input plannerInput(Map<String, Double> mastery, int minutes, int minimum, List<UUID> mistakes) {
        Map<String, TopicStat> topics = new HashMap<>();
        Map<String, List<PoolQuestion>> pool = new HashMap<>();
        Map<String, Category> cats = Map.ofEntries(Map.entry("arrays", Category.DSA), Map.entry("hashing", Category.DSA),
                Map.entry("java-oop", Category.JAVA), Map.entry("java-collections", Category.JAVA),
                Map.entry("java-exceptions", Category.JAVA), Map.entry("java-generics", Category.JAVA),
                Map.entry("java-streams", Category.JAVA), Map.entry("spring-di", Category.SPRING),
                Map.entry("sql-joins", Category.DATABASES), Map.entry("normalization", Category.DATABASES),
                Map.entry("sql-indexes", Category.DATABASES), Map.entry("system-design-fundamentals", Category.SYSTEM_DESIGN));
        cats.forEach((slug, c) -> {
            topics.put(slug, new TopicStat(c, mastery.get(slug), null));
            List<PoolQuestion> qs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                qs.add(new PoolQuestion(new UUID(slug.hashCode(), i), slug, Difficulty.values()[i % 3], null));
            }
            pool.put(slug, qs);
        });
        return new MissionPlanner.Input(1, "INTERMEDIATE", minutes, minimum, 42L, topics, pool, mistakes, List.of(), List.of(),
                Map.of());
    }
}
