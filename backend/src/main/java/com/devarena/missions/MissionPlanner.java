package com.devarena.missions;

import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.missions.RoadmapPlan.Phase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic daily mission planner. Same inputs always give the same plan; there
 * is no randomness and no AI. Rules, in order:
 *
 * <ol>
 *   <li>A DSA question set on the roadmap's topic of the day is always assigned.</li>
 *   <li>If there are recent mistakes, a revision set of those questions comes next.</li>
 *   <li>The remaining categories are ordered weakest-first by category mastery (unpractised
 *       categories count as 0.25) and added while they fit the daily time budget.</li>
 *   <li>At least {@code dailyMinimum} missions are assigned, even past the budget,
 *       so the streak target is always achievable.</li>
 *   <li>Within a category, the weakest focus topic is chosen; ties go to the least recently
 *       practised, then to roadmap order rotated by the day number (topic variety).</li>
 *   <li>Difficulty follows topic mastery: below 0.35 EASY, below 0.7 MEDIUM, otherwise HARD.
 *       Phase 3 never goes below MEDIUM.</li>
 *   <li>Questions closest to the target difficulty win, unseen ones first, then those seen
 *       longest ago; remaining ties are broken by a stable hash of (seed, questionId).</li>
 * </ol>
 */
public final class MissionPlanner {

    public enum Kind { QUESTION_SET, INTERVIEW_STUDY, DESIGN_STUDY }

    public record PoolQuestion(UUID id, String topicSlug, Difficulty difficulty, Instant lastAttemptAt) {}

    public record PoolInterviewQuestion(UUID id, String type, Difficulty difficulty) {}

    public record PoolDesign(UUID id, String title, Difficulty difficulty) {}

    public record TopicStat(Category category, Double mastery, Instant lastPracticedAt) {}

    public record Input(
            int roadmapDay,
            String prepLevel,
            int dailyMinutes,
            int dailyMinimum,
            long seed,
            Map<String, TopicStat> topics,
            Map<String, List<PoolQuestion>> questionsByTopic,
            List<UUID> recentMistakes,
            List<PoolInterviewQuestion> interviewQuestions,
            List<PoolDesign> designProblems,
            Map<String, String> topicNames) {}

    public record Planned(int slot, Kind kind, Category category, String topicSlug, String title, Difficulty difficulty,
                          int minutes, int xp, boolean revision, List<UUID> questionIds, List<UUID> interviewQuestionIds,
                          UUID designProblemId) {}

    static final int SLOT_DSA = 0, SLOT_REVISION = 1, SLOT_JAVA = 2, SLOT_SPRING = 3, SLOT_DB = 4,
            SLOT_DESIGN = 5, SLOT_QUIZ = 6, SLOT_INTERVIEW = 7;

    private MissionPlanner() {}

    public static List<Planned> plan(Input in) {
        Phase phase = RoadmapPlan.phaseFor(in.roadmapDay());
        List<Planned> chosen = new ArrayList<>();

        // 1. DSA curriculum topic of the day.
        List<String> dsaTopics = RoadmapPlan.focus(phase, Category.DSA);
        String dsaTopic = dsaTopics.get((in.roadmapDay() - 1) % dsaTopics.size());
        Planned dsa = questionSet(in, phase, SLOT_DSA, Category.DSA, dsaTopic, false);
        if (dsa != null) chosen.add(dsa);

        // 2. Revision of recent mistakes.
        if (in.recentMistakes().size() >= 2) {
            List<UUID> ids = in.recentMistakes().stream().limit(5).toList();
            chosen.add(new Planned(SLOT_REVISION, Kind.QUESTION_SET, Category.DSA, null, "Revise recent mistakes",
                    Difficulty.MEDIUM, ids.size() * 2, 20, true, ids, List.of(), null));
        }

        // 3. Other categories, weakest first.
        List<Planned> candidates = new ArrayList<>();
        addIfPresent(candidates, questionSet(in, phase, SLOT_JAVA, Category.JAVA, weakestTopic(in, phase, Category.JAVA), false));
        addIfPresent(candidates, questionSet(in, phase, SLOT_SPRING, Category.SPRING, weakestTopic(in, phase, Category.SPRING), false));
        addIfPresent(candidates, questionSet(in, phase, SLOT_DB, Category.DATABASES, weakestTopic(in, phase, Category.DATABASES), false));
        addIfPresent(candidates, designSlot(in, phase));
        addIfPresent(candidates, mixedQuiz(in, phase));
        addIfPresent(candidates, interviewStudy(in, phase));

        candidates.sort(Comparator
                .comparingDouble((Planned p) -> priority(in, p, phase))
                .thenComparingInt(Planned::slot));

        int used = chosen.stream().mapToInt(Planned::minutes).sum();
        for (Planned p : candidates) {
            boolean fits = used + p.minutes() <= in.dailyMinutes();
            boolean needed = chosen.size() < in.dailyMinimum();
            if (fits || needed) {
                chosen.add(p);
                used += p.minutes();
            }
        }
        chosen.sort(Comparator.comparingInt(Planned::slot));
        return chosen;
    }

    /** Lower sorts first. Interview practice is mandatory-ish in phase 3. */
    private static double priority(Input in, Planned p, Phase phase) {
        if (phase == Phase.INTERVIEW_READY && p.kind() == Kind.INTERVIEW_STUDY) return -1;
        if (p.slot() == SLOT_QUIZ) return 0.6; // a mixed quiz is a nice extra, not a priority
        return categoryMastery(in, p.category());
    }

    static double categoryMastery(Input in, Category c) {
        double sum = 0;
        int n = 0;
        for (TopicStat t : in.topics().values()) {
            if (t.category() == c && t.mastery() != null) {
                sum += t.mastery();
                n++;
            }
        }
        return n == 0 ? 0.25 : sum / n;
    }

    static String weakestTopic(Input in, Phase phase, Category category) {
        List<String> focus = RoadmapPlan.focus(phase, category);
        if (focus.isEmpty()) return null;
        int rotation = in.roadmapDay() % focus.size();
        List<String> rotated = new ArrayList<>();
        for (int i = 0; i < focus.size(); i++) rotated.add(focus.get((i + rotation) % focus.size()));
        return rotated.stream().min(Comparator
                .comparingDouble((String s) -> mastery(in, s) == null ? 0.25 : mastery(in, s))
                .thenComparing(s -> lastPracticed(in, s), Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    static Difficulty targetDifficulty(Input in, Phase phase, String topicSlug) {
        Double m = mastery(in, topicSlug);
        Difficulty d;
        if (m == null) {
            d = switch (in.prepLevel()) {
                case "ADVANCED" -> Difficulty.MEDIUM;
                case "BEGINNER" -> Difficulty.EASY;
                default -> phase == Phase.FUNDAMENTALS ? Difficulty.EASY : Difficulty.MEDIUM;
            };
        } else if (m < 0.35) {
            d = Difficulty.EASY;
        } else if (m < 0.7) {
            d = Difficulty.MEDIUM;
        } else {
            d = Difficulty.HARD;
        }
        if (phase == Phase.INTERVIEW_READY && d == Difficulty.EASY) d = Difficulty.MEDIUM;
        return d;
    }

    private static Planned questionSet(Input in, Phase phase, int slot, Category category, String topicSlug, boolean revision) {
        if (topicSlug == null) return null;
        Difficulty target = targetDifficulty(in, phase, topicSlug);
        int count = phase == Phase.FUNDAMENTALS ? 4 : 5;
        List<UUID> ids = pickQuestions(in.questionsByTopic().getOrDefault(topicSlug, List.of()), target, count, in.seed());
        if (ids.isEmpty()) return null;
        String name = in.topicNames().getOrDefault(topicSlug, topicSlug);
        return new Planned(slot, Kind.QUESTION_SET, category, topicSlug, name, target, ids.size() * 2, xpFor(target),
                revision, ids, List.of(), null);
    }

    private static Planned designSlot(Input in, Phase phase) {
        boolean study = phase == Phase.INTERVIEW_READY || (phase == Phase.ADVANCED && in.roadmapDay() % 2 == 1);
        if (phase == Phase.ADVANCED && !study) {
            String topic = weakestTopic(in, phase, Category.DISTRIBUTED);
            return questionSet(in, phase, SLOT_DESIGN, Category.DISTRIBUTED, topic, false);
        }
        if (study && !in.designProblems().isEmpty()) {
            PoolDesign d = in.designProblems().get((in.roadmapDay() / 2) % in.designProblems().size());
            return new Planned(SLOT_DESIGN, Kind.DESIGN_STUDY, Category.SYSTEM_DESIGN, null, "Design: " + d.title(),
                    d.difficulty(), 25, 40, false, List.of(), List.of(), d.id());
        }
        return questionSet(in, phase, SLOT_DESIGN, Category.SYSTEM_DESIGN, "system-design-fundamentals", false);
    }

    private static Planned mixedQuiz(Input in, Phase phase) {
        // Five questions from the five weakest practised-or-focus topics across all categories.
        List<String> weakest = in.topics().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, TopicStat> e) ->
                        e.getValue().mastery() == null ? 0.25 : e.getValue().mastery()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .filter(s -> !in.questionsByTopic().getOrDefault(s, List.of()).isEmpty())
                .limit(5).toList();
        List<UUID> ids = new ArrayList<>();
        for (String topic : weakest) {
            List<UUID> one = pickQuestions(in.questionsByTopic().get(topic), targetDifficulty(in, phase, topic), 1,
                    in.seed() ^ 0x5bd1e995L);
            ids.addAll(one);
        }
        if (ids.size() < 3) return null;
        return new Planned(SLOT_QUIZ, Kind.QUESTION_SET, Category.DSA, null, "Mixed technical quiz", Difficulty.MEDIUM,
                ids.size() * 2, 30, false, ids, List.of(), null);
    }

    private static Planned interviewStudy(Input in, Phase phase) {
        List<String> types = List.of("JAVA_BACKEND", "SPRING_BOOT", "SQL", "DISTRIBUTED_SYSTEMS", "BEHAVIORAL", "FULL_STACK", "AI_CONCEPTS");
        String type = types.get(in.roadmapDay() % types.size());
        List<PoolInterviewQuestion> pool = in.interviewQuestions().stream().filter(q -> q.type().equals(type)).toList();
        if (pool.isEmpty()) pool = in.interviewQuestions().stream()
                .filter(q -> !q.type().equals("DSA_CODING") && !q.type().equals("SYSTEM_DESIGN")).toList();
        if (pool.isEmpty()) return null;
        PoolInterviewQuestion q = pool.stream()
                .min(Comparator.comparingLong(p -> mix(in.seed(), p.id()))).orElseThrow();
        return new Planned(SLOT_INTERVIEW, Kind.INTERVIEW_STUDY, categoryForInterviewType(q.type()),
                null, "Interview drill", q.difficulty(), 15, 30, false, List.of(), List.of(q.id()), null);
    }

    static Category categoryForInterviewType(String type) {
        return switch (type) {
            case "SPRING_BOOT" -> Category.SPRING;
            case "SQL" -> Category.DATABASES;
            case "DISTRIBUTED_SYSTEMS" -> Category.DISTRIBUTED;
            case "SYSTEM_DESIGN" -> Category.SYSTEM_DESIGN;
            case "FULL_STACK" -> Category.FRONTEND;
            case "BEHAVIORAL" -> Category.BEHAVIORAL;
            case "AI_CONCEPTS" -> Category.AI_CONCEPTS;
            case "DSA_CODING" -> Category.DSA;
            default -> Category.JAVA;
        };
    }

    static List<UUID> pickQuestions(List<PoolQuestion> pool, Difficulty target, int count, long seed) {
        return pool.stream()
                .sorted(Comparator
                        .comparingInt((PoolQuestion q) -> Math.abs(q.difficulty().ordinal() - target.ordinal()))
                        .thenComparing(PoolQuestion::lastAttemptAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(q -> mix(seed, q.id())))
                .limit(count)
                .map(PoolQuestion::id)
                .toList();
    }

    private static int xpFor(Difficulty d) {
        return switch (d) {
            case EASY -> 20;
            case MEDIUM -> 30;
            case HARD -> 40;
        };
    }

    private static Double mastery(Input in, String slug) {
        TopicStat t = in.topics().get(slug);
        return t == null ? null : t.mastery();
    }

    private static Instant lastPracticed(Input in, String slug) {
        TopicStat t = in.topics().get(slug);
        return t == null ? null : t.lastPracticedAt();
    }

    private static void addIfPresent(List<Planned> list, Planned p) {
        if (p != null) list.add(p);
    }

    /** Stable 64-bit mix (SplitMix64 finaliser) for deterministic tie-breaking. */
    static long mix(long seed, UUID id) {
        long z = seed ^ id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
