package com.devarena.missions;

import com.devarena.common.Category;
import java.util.List;
import java.util.Map;

/**
 * The 90-day preparation roadmap. Each phase lists the topics (by slug) in focus for each
 * category. Versioned with the code so changes are reviewed like any other change.
 */
public final class RoadmapPlan {
    public static final int TOTAL_DAYS = 90;

    public enum Phase {
        FUNDAMENTALS(1, 30, "Fundamentals"),
        ADVANCED(31, 60, "Advanced engineering"),
        INTERVIEW_READY(61, 90, "Interview readiness");

        public final int firstDay;
        public final int lastDay;
        public final String label;

        Phase(int firstDay, int lastDay, String label) {
            this.firstDay = firstDay;
            this.lastDay = lastDay;
            this.label = label;
        }
    }

    private static final Map<Phase, Map<Category, List<String>>> FOCUS = Map.of(
            Phase.FUNDAMENTALS, Map.of(
                    Category.DSA, List.of("arrays", "hashing", "two-pointers", "sliding-window", "strings", "binary-search",
                            "linked-lists", "stacks-queues", "trees", "graphs"),
                    Category.JAVA, List.of("java-oop", "java-collections", "java-exceptions", "java-generics", "java-streams"),
                    Category.SPRING, List.of("spring-di", "spring-rest", "spring-data-jpa", "spring-testing"),
                    Category.DATABASES, List.of("sql-joins", "normalization", "sql-indexes"),
                    Category.SYSTEM_DESIGN, List.of("system-design-fundamentals"),
                    Category.DISTRIBUTED, List.of()),
            Phase.ADVANCED, Map.of(
                    Category.DSA, List.of("dynamic-programming", "graphs", "heaps", "backtracking", "greedy", "trees"),
                    Category.JAVA, List.of("java-concurrency", "jvm-memory", "completable-future", "java-streams"),
                    Category.SPRING, List.of("spring-transactions", "spring-security", "spring-caching", "microservices"),
                    Category.DATABASES, List.of("sql-transactions", "sql-indexes", "postgresql"),
                    Category.SYSTEM_DESIGN, List.of("system-design-fundamentals"),
                    Category.DISTRIBUTED, List.of("distributed-caching", "messaging", "resilience", "consistency",
                            "rate-limiting", "load-balancing", "distributed-locks")),
            Phase.INTERVIEW_READY, Map.of(
                    Category.DSA, List.of("dynamic-programming", "graphs", "sliding-window", "binary-search", "trees",
                            "heaps", "backtracking", "hashing", "two-pointers", "linked-lists", "greedy", "arrays"),
                    Category.JAVA, List.of("java-concurrency", "java-collections", "jvm-memory", "java-oop", "completable-future"),
                    Category.SPRING, List.of("spring-transactions", "spring-data-jpa", "spring-security", "spring-rest"),
                    Category.DATABASES, List.of("sql-transactions", "sql-indexes", "sql-joins", "postgresql"),
                    Category.SYSTEM_DESIGN, List.of("system-design-fundamentals"),
                    Category.DISTRIBUTED, List.of("resilience", "messaging", "consistency", "distributed-caching",
                            "distributed-locks", "rate-limiting")));

    private RoadmapPlan() {}

    public static Phase phaseFor(int day) {
        if (day <= Phase.FUNDAMENTALS.lastDay) return Phase.FUNDAMENTALS;
        if (day <= Phase.ADVANCED.lastDay) return Phase.ADVANCED;
        return Phase.INTERVIEW_READY;
    }

    public static List<String> focus(Phase phase, Category category) {
        return FOCUS.get(phase).getOrDefault(category, List.of());
    }
}
