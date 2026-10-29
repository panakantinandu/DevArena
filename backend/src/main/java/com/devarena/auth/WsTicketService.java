package com.devarena.auth;

import com.devarena.common.Ids;
import com.devarena.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Short-lived, single-use tickets that authenticate a WebSocket handshake.
 *
 * <p>In production the SPA reaches the REST API through a same-origin proxy (cookies
 * work), but the WebSocket connects straight to the backend host where the session
 * cookie is not sent. The SPA therefore asks for a ticket over the authenticated REST
 * API and presents it once during the handshake.
 */
@Service
public class WsTicketService {
    private record Ticket(AppUserPrincipal principal, Instant expiresAt) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int ttlSeconds;

    public WsTicketService(AppProperties props, Clock clock) {
        this.clock = clock;
        this.ttlSeconds = props.wsTicketSeconds();
    }

    public String issue(AppUserPrincipal principal) {
        String value = Ids.token(32);
        tickets.put(value, new Ticket(principal.withoutPassword(), clock.instant().plusSeconds(ttlSeconds)));
        return value;
    }

    public int ttlSeconds() { return ttlSeconds; }

    /** Consumes the ticket: a ticket can open exactly one connection. */
    public Optional<AppUserPrincipal> redeem(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        Ticket t = tickets.remove(value);
        if (t == null || t.expiresAt().isBefore(clock.instant())) return Optional.empty();
        return Optional.of(t.principal());
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
    }
}
