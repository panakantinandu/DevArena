package com.devarena.coding;

import com.devarena.common.ApiException;
import com.devarena.interviews.InterviewDtos.CodeSnapshot;
import com.devarena.interviews.InterviewSession;
import com.devarena.interviews.InterviewSessionRepository;
import com.devarena.interviews.InterviewStatus;
import com.devarena.realtime.ClientMessageHandler;
import com.devarena.realtime.RealtimeHub;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Live code sharing for coding interviews. The candidate is the single writer; the
 * interviewer gets a read-only mirror.
 *
 * <p>The candidate sends Monaco change events ({@code CODE_UPDATE}) tagged with the version
 * they were made against. The server applies them only if that version matches its own,
 * bumps the version and broadcasts the same deltas, so every keystroke doesn't ship the
 * whole document. On any mismatch (reconnect, restart, dropped message) the candidate
 * sends its full text once ({@code CODE_REPLACE}) and everyone resynchronises from that
 * snapshot. Watchers that see a version gap fetch a snapshot over REST.
 */
@Service
public class CodeSyncService {
    static final int MAX_CHARS = 100_000;

    private static final class Doc {
        final StringBuilder text;
        long version;
        String language;
        boolean dirty;

        Doc(String text, long version, String language) {
            this.text = new StringBuilder(text);
            this.version = version;
            this.language = language;
        }
    }

    private final Map<UUID, Doc> docs = new ConcurrentHashMap<>();
    private final InterviewSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final RealtimeHub hub;
    private final Clock clock;

    public CodeSyncService(InterviewSessionRepository sessions, JdbcTemplate jdbc, RealtimeHub hub, Clock clock) {
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.hub = hub;
        this.clock = clock;
    }

    public CodeSnapshot snapshot(UUID sessionId, String starterIfNew) {
        Doc d = doc(sessionId, starterIfNew);
        synchronized (d) {
            return new CodeSnapshot(sessionId, d.text.toString(), d.language, d.version);
        }
    }

    /** Applies an incremental update. Returns false when the base version is stale. */
    public boolean applyChanges(UUID userId, UUID sessionId, long baseVersion, List<Change> changes) {
        requireCandidateInActiveSession(userId, sessionId);
        Doc d = doc(sessionId, "");
        long version;
        synchronized (d) {
            if (baseVersion != d.version) return false;
            for (Change c : changes) {
                if (c.offset() < 0 || c.length() < 0 || c.offset() + c.length() > d.text.length()) return false;
            }
            int growth = changes.stream().mapToInt(c -> c.text().length() - c.length()).sum();
            if (d.text.length() + growth > MAX_CHARS) throw ApiException.badRequest("CODE_TOO_LARGE", "Code is too large");
            // Client sends changes sorted by descending offset, so applying in order is safe.
            for (Change c : changes) d.text.replace(c.offset(), c.offset() + c.length(), c.text());
            d.version++;
            d.dirty = true;
            version = d.version;
        }
        hub.publish("interview:" + sessionId, "CODE_UPDATED",
                Map.of("sessionId", sessionId, "version", version, "baseVersion", baseVersion, "changes", changes));
        return true;
    }

    public CodeSnapshot replace(UUID userId, UUID sessionId, String code, String language) {
        requireCandidateInActiveSession(userId, sessionId);
        if (code.length() > MAX_CHARS) throw ApiException.badRequest("CODE_TOO_LARGE", "Code is too large");
        Doc d = doc(sessionId, "");
        CodeSnapshot snap;
        synchronized (d) {
            d.text.setLength(0);
            d.text.append(code);
            if (language != null && !language.isBlank()) d.language = language;
            d.version++;
            d.dirty = true;
            snap = new CodeSnapshot(sessionId, code, d.language, d.version);
        }
        hub.publish("interview:" + sessionId, "CODE_SNAPSHOT", snap);
        return snap;
    }

    public record Change(int offset, int length, String text) {}

    private void requireCandidateInActiveSession(UUID userId, UUID sessionId) {
        InterviewSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Interview"));
        if (!s.isCandidate(userId)) throw ApiException.forbidden("Only the candidate can edit the shared code");
        if (s.getStatus() != InterviewStatus.ACTIVE) throw ApiException.conflict("NOT_ACTIVE", "The interview isn't active");
    }

    private Doc doc(UUID sessionId, String starter) {
        return docs.computeIfAbsent(sessionId, id -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select code, version, language from interview_code_drafts where session_id = ?", id);
            if (rows.isEmpty()) return new Doc(starter == null ? "" : starter, 0, "JAVA");
            Map<String, Object> r = rows.getFirst();
            return new Doc((String) r.get("code"), ((Number) r.get("version")).longValue(), (String) r.get("language"));
        });
    }

    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        for (Map.Entry<UUID, Doc> e : docs.entrySet()) {
            Doc d = e.getValue();
            String code;
            long version;
            String language;
            synchronized (d) {
                if (!d.dirty) continue;
                code = d.text.toString();
                version = d.version;
                language = d.language;
                d.dirty = false;
            }
            jdbc.update("""
                    insert into interview_code_drafts(session_id, language, code, version, updated_at) values (?, ?, ?, ?, ?)
                    on conflict (session_id) do update set language = excluded.language, code = excluded.code,
                        version = excluded.version, updated_at = excluded.updated_at
                    """, e.getKey(), language, code, version, Timestamp.from(clock.instant()));
        }
    }

    /** Persists and forgets a session's document (interview finished). */
    public void close(UUID sessionId) {
        flush();
        docs.remove(sessionId);
    }

    @PreDestroy
    void persistOnShutdown() {
        flush();
    }

    // ---------------------------------------------------------------- WebSocket handlers

    @org.springframework.stereotype.Component
    static class UpdateHandler implements ClientMessageHandler {
        private final CodeSyncService sync;

        UpdateHandler(CodeSyncService sync) { this.sync = sync; }

        @Override
        public String type() { return "CODE_UPDATE"; }

        @Override
        public void handle(UUID userId, JsonNode data, Reply reply) {
            UUID sessionId = UUID.fromString(data.path("sessionId").asString());
            long base = data.path("baseVersion").asLong(-1);
            List<Change> changes = new ArrayList<>();
            for (JsonNode c : data.path("changes")) {
                changes.add(new Change(c.path("offset").asInt(-1), c.path("length").asInt(-1), c.path("text").asString("")));
            }
            if (changes.isEmpty() || changes.size() > 200) {
                reply.send("CODE_REJECTED", Map.of("sessionId", sessionId, "reason", "BAD_CHANGES"));
                return;
            }
            if (!sync.applyChanges(userId, sessionId, base, changes)) {
                reply.send("CODE_REJECTED", Map.of("sessionId", sessionId, "reason", "STALE_VERSION"));
            }
        }
    }

    @org.springframework.stereotype.Component
    static class ReplaceHandler implements ClientMessageHandler {
        private final CodeSyncService sync;

        ReplaceHandler(CodeSyncService sync) { this.sync = sync; }

        @Override
        public String type() { return "CODE_REPLACE"; }

        @Override
        public void handle(UUID userId, JsonNode data, Reply reply) {
            UUID sessionId = UUID.fromString(data.path("sessionId").asString());
            sync.replace(userId, sessionId, data.path("code").asString(""), data.path("language").asString("JAVA"));
        }
    }
}
