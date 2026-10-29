package com.devarena.gym;

import static org.assertj.core.api.Assertions.assertThat;

import com.devarena.support.ApiClient;
import com.devarena.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class GymFlowTest extends IntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test
    void dailyRunIsSharedHidesSecretsAndAwardsXpOnce() {
        ApiClient a = ApiClient.registered(port, "gyma");
        ApiClient b = ApiClient.registered(port, "gymb");
        JsonNode runA = a.post("/api/gym/daily", null).body();
        JsonNode runB = b.post("/api/gym/daily", null).body();
        assertThat(runA.path("puzzles").size()).isEqualTo(5);
        // Everyone gets the same puzzles on the same date.
        assertThat(ids(runA)).isEqualTo(ids(runB));
        // Starting again resumes the same run.
        assertThat(a.post("/api/gym/daily", null).body().path("id").asString()).isEqualTo(runA.path("id").asString());
        // Nothing secret before answering: no answers, clue bodies or option scores.
        String raw = a.get("/api/gym/runs/" + runA.path("id").asString()).raw();
        assertThat(raw).doesNotContain("\"answer\":2").doesNotContain("rootCause\":\"").doesNotContain("bestRisk")
                .doesNotContain("\"bugs\"").doesNotContain("rationale");

        String runId = runA.path("id").asString();
        int xpBefore = xp(a);
        for (JsonNode p : runA.path("puzzles")) {
            ApiClient.Response r = a.post(path(runId, p) + "/answer", Map.of("response", answer(a, runId, p), "confidence", 70));
            assertThat(r.status()).as(p.path("mode").asString()).isEqualTo(200);
            assertThat(r.body().path("puzzle").path("answer").path("solution").isObject()).isTrue();
        }
        JsonNode done = a.get("/api/gym/runs/" + runId).body();
        assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("totalScore").asInt()).isBetween(0, 100);
        int gained = xp(a) - xpBefore;
        assertThat(gained).isBetween(0, GymService.DAILY_XP_MAX);
        // A second answer to the same puzzle is rejected; XP doesn't change.
        JsonNode first = runA.path("puzzles").get(0);
        assertThat(a.post(path(runId, first) + "/answer", Map.of("response", answer(a, runId, first), "confidence", 60)).status())
                .isEqualTo(409);
        assertThat(xp(a) - xpBefore).isEqualTo(gained);

        // The overview includes skills, calibration and the friends board (just you, until you add friends).
        JsonNode overview = a.get("/api/gym").body();
        assertThat(overview.path("daily").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(overview.path("skills").size()).isEqualTo(5);
        assertThat(overview.path("calibration").size()).isEqualTo(5);
    }

    @Test
    void cluesCostPointsAndAreRecordedServerSide() {
        ApiClient u = ApiClient.registered(port, "gymc");
        JsonNode run = u.post("/api/gym/practice", Map.of("mode", "INCIDENT")).body();
        String runId = run.path("id").asString();
        JsonNode p = run.path("puzzles").get(0);
        JsonNode clue = p.path("payload").path("clues").get(0);
        ApiClient.Response revealed = u.post(path(runId, p) + "/clues/" + clue.path("id").asString(), null);
        assertThat(revealed.status()).isEqualTo(200);
        assertThat(revealed.text("body")).isNotBlank();
        assertThat(u.get("/api/gym/runs/" + runId).body().path("puzzles").get(0).path("revealedClues").size()).isEqualTo(1);

        // Answer with the true root cause and action: score = 100 - clue cost.
        String puzzleId = p.path("id").asString();
        Map<String, Object> secret = jdbc.queryForMap("select secret->>'rootCause' as rc, secret->>'action' as ac from gym_puzzles where id = ?::uuid", puzzleId);
        ApiClient.Response r = u.post(path(runId, p) + "/answer", Map.of(
                "response", Map.of("rootCause", secret.get("rc"), "action", secret.get("ac")), "confidence", 90));
        assertThat(r.body().path("puzzle").path("answer").path("score").asInt()).isEqualTo(100 - clue.path("cost").asInt());
        // No more clues once answered.
        assertThat(u.post(path(runId, p) + "/clues/" + clue.path("id").asString(), null).status()).isEqualTo(409);
    }

    @Test
    void runsArePrivateAndInputIsValidated() throws Exception {
        ApiClient owner = ApiClient.registered(port, "gymo");
        ApiClient other = ApiClient.registered(port, "gymx");
        JsonNode run = owner.post("/api/gym/practice", Map.of("mode", "ESTIMATION")).body();
        String runId = run.path("id").asString();
        JsonNode p = run.path("puzzles").get(0);
        assertThat(other.get("/api/gym/runs/" + runId).status()).isEqualTo(404);
        assertThat(other.post(path(runId, p) + "/answer", Map.of("response", Map.of("value", 1, "low", 1, "high", 2), "confidence", 60)).status())
                .isEqualTo(404);
        // Guess outside its own range, and confidence out of bounds.
        assertThat(owner.post(path(runId, p) + "/answer", Map.of("response", Map.of("value", 50, "low", 1, "high", 10), "confidence", 60)).status())
                .isEqualTo(400);
        assertThat(owner.post(path(runId, p) + "/answer", Map.of("response", Map.of("value", 5, "low", 1, "high", 10), "confidence", 100)).status())
                .isEqualTo(400);

        // Concurrent duplicate answers store exactly one.
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> owner.post(path(runId, p) + "/answer", Map.of("response", Map.of("value", 5, "low", 1, "high", 10), "confidence", 60)).status());
        }
        int ok = 0;
        for (Future<Integer> f : pool.invokeAll(calls)) if (f.get() == 200) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    private static String path(String runId, JsonNode p) {
        return "/api/gym/runs/" + runId + "/puzzles/" + p.path("id").asString();
    }

    private static List<String> ids(JsonNode run) {
        List<String> out = new ArrayList<>();
        run.path("puzzles").forEach(p -> out.add(p.path("id").asString()));
        return out;
    }

    /** A valid (not necessarily right) answer for each mode. */
    private static Object answer(ApiClient c, String runId, JsonNode p) {
        JsonNode pl = p.path("payload");
        return switch (p.path("mode").asString()) {
            case "ESTIMATION" -> Map.of("value", 1000, "low", 100, "high", 10000);
            case "INCIDENT" -> Map.of("rootCause", pl.path("rootCauses").get(1).path("id").asString(),
                    "action", pl.path("actions").get(0).path("id").asString());
            case "REVIEW" -> Map.of("lines", List.of(3, 7));
            default -> Map.of("option", pl.path("options").get(1).path("id").asString(),
                    "risk", pl.path("risks").get(0).path("id").asString());
        };
    }

    private int xp(ApiClient c) {
        Integer v = jdbc.queryForObject("select coalesce(sum(amount),0) from xp_transactions where user_id = ?", Integer.class, c.userId);
        return v == null ? 0 : v;
    }
}
