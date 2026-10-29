package com.devarena.gamification;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Game levels are a function of total XP only. They are deliberately separate from
 * skill mastery, which is computed from answer history (see MasteryService).
 */
@Service
public class LevelService {

    public record Level(int level, String title, long minXp) {}

    public record LevelInfo(int level, String title, long xp, long currentLevelXp, Long nextLevelXp) {}

    @ConfigurationProperties(prefix = "devarena.gamification")
    public record LevelProperties(List<Level> levels) {
        public LevelProperties {
            if (levels == null || levels.isEmpty()) {
                levels = List.of(
                        new Level(1, "Java Apprentice", 0),
                        new Level(2, "Code Explorer", 300),
                        new Level(3, "Algorithm Warrior", 900),
                        new Level(4, "Backend Builder", 2000),
                        new Level(5, "System Architect", 4000),
                        new Level(6, "Interview Challenger", 7000));
            }
        }
    }

    private final List<Level> levels;

    public LevelService(LevelProperties props) {
        this.levels = props.levels().stream().sorted((a, b) -> Long.compare(a.minXp(), b.minXp())).toList();
    }

    public LevelInfo forXp(long xp) {
        Level current = levels.getFirst();
        Level next = null;
        for (int i = 0; i < levels.size(); i++) {
            if (xp >= levels.get(i).minXp()) {
                current = levels.get(i);
                next = i + 1 < levels.size() ? levels.get(i + 1) : null;
            }
        }
        return new LevelInfo(current.level(), current.title(), xp, current.minXp(), next == null ? null : next.minXp());
    }

    public List<Level> all() {
        return levels;
    }
}
