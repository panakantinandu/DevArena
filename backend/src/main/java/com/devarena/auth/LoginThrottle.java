package com.devarena.auth;

import com.devarena.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for login and registration. Failures are counted per
 * account and per client IP; too many failures lock that key for a while.
 * In-memory, which is correct for the single-instance deployment this app targets.
 */
@Component
public class LoginThrottle {
    private record Window(int failures, Instant lockedUntil, Instant lastSeen) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockFor;

    public LoginThrottle(AppProperties props, Clock clock) {
        this.clock = clock;
        this.maxAttempts = props.loginMaxAttempts();
        this.lockFor = Duration.ofMinutes(props.loginLockMinutes());
    }

    /** Seconds until the key may try again, or 0 if it is not locked. */
    public long lockedForSeconds(String login, String ip) {
        Instant now = clock.instant();
        long a = remaining(key("acct", login), now);
        long b = remaining(key("ip", ip), now);
        return Math.max(a, b);
    }

    public void recordFailure(String login, String ip) {
        Instant now = clock.instant();
        // IPs get a looser budget so two friends behind one NAT don't lock each other out.
        bump(key("acct", login), maxAttempts, now);
        bump(key("ip", ip), maxAttempts * 4, now);
    }

    public void recordSuccess(String login) {
        windows.remove(key("acct", login));
    }

    private long remaining(String key, Instant now) {
        Window w = windows.get(key);
        if (w == null || w.lockedUntil() == null || !w.lockedUntil().isAfter(now)) return 0;
        return Duration.between(now, w.lockedUntil()).toSeconds() + 1;
    }

    private void bump(String key, int limit, Instant now) {
        windows.compute(key, (k, w) -> {
            int failures = (w == null || (w.lockedUntil() != null && !w.lockedUntil().isAfter(now))) ? 1 : w.failures() + 1;
            Instant lock = failures >= limit ? now.plus(lockFor) : null;
            return new Window(failures, lock, now);
        });
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    @Scheduled(fixedDelay = 600_000)
    void evictStale() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(2));
        windows.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
    }
}
