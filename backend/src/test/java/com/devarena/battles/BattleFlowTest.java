package com.devarena.battles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devarena.support.ApiClient;
import com.devarena.support.IntegrationTest;
import java.time.Duration;
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

class BattleFlowTest extends IntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoPlayersCompeteAndServerScores() {
        ApiClient host = ApiClient.registered(port, "host");
        ApiClient guest = ApiClient.registered(port, "guest");

        ApiClient.Response created = host.post("/api/battles", Map.of("category", "JAVA", "difficulty", "EASY",
                "questionCount", 3, "secondsPerQuestion", 20));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("state").path("id").asString();
        String code = created.body().path("state").path("roomCode").asString();
        assertThat(created.body().path("state").path("status").asString()).isEqualTo("WAITING");

        // Guest can't act before joining.
        assertThat(guest.get("/api/battles/" + id).status()).isEqualTo(404);
        assertThat(guest.post("/api/battles/join", Map.of("code", code)).status()).isEqualTo(200);

        // Host can't start until everyone is ready; guest can't start at all.
        assertThat(host.post("/api/battles/" + id + "/start", null).status()).isEqualTo(409);
        host.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        ApiClient.Response ready = guest.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        assertThat(ready.body().path("state").path("status").asString()).isEqualTo("READY");
        assertThat(guest.post("/api/battles/" + id + "/start", null).status()).isEqualTo(403);
        assertThat(host.post("/api/battles/" + id + "/start", null).status()).isEqualTo(200);

        for (int round = 0; round < 3; round++) {
            int r = round;
            JsonNode live = await().atMost(Duration.ofSeconds(15)).until(
                    () -> host.get("/api/battles/" + id).body().path("state").path("live"),
                    n -> !n.isNull() && !n.isMissingNode() && n.path("index").asInt() == r);
            // The live question never includes the answer.
            assertThat(live.toString()).doesNotContain("correct");
            String hostOption = live.path("question").path("options").get(0).path("id").asString();
            String guestOption = live.path("question").path("options").get(1).path("id").asString();
            assertThat(host.post("/api/battles/" + id + "/answer", Map.of("questionIndex", r, "optionId", hostOption)).status())
                    .isEqualTo(200);
            // A second answer to the same question is rejected.
            assertThat(host.post("/api/battles/" + id + "/answer", Map.of("questionIndex", r, "optionId", guestOption)).status())
                    .isEqualTo(409);
            assertThat(guest.post("/api/battles/" + id + "/answer", Map.of("questionIndex", r, "optionId", guestOption)).status())
                    .isEqualTo(200);
        }

        JsonNode done = await().atMost(Duration.ofSeconds(20)).until(
                () -> host.get("/api/battles/" + id).body().path("state"),
                s -> s.path("status").asString().equals("COMPLETED"));

        // Scores equal the sum of server-computed points for each player's answers.
        for (JsonNode p : done.path("players")) {
            Integer sum = jdbc.queryForObject("""
                    select coalesce(sum(a.points), 0) from quiz_answers a join quiz_battle_questions q on q.id = a.battle_question_id
                    where q.battle_id = ?::uuid and a.user_id = ?::uuid
                    """, Integer.class, id, p.path("user").path("id").asString());
            assertThat(p.path("score").asInt()).isEqualTo(sum);
        }
        ApiClient.Response results = guest.get("/api/battles/" + id + "/results");
        assertThat(results.status()).isEqualTo(200);
        assertThat(results.body().path("questions").size()).isEqualTo(3);
        assertThat(guest.get("/api/battles").body().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void concurrentDuplicateAnswersAreStoredOnce() throws Exception {
        ApiClient host = ApiClient.registered(port, "h2");
        ApiClient guest = ApiClient.registered(port, "g2");
        JsonNode state = host.post("/api/battles", Map.of("category", "DSA", "difficulty", "MEDIUM",
                "questionCount", 3, "secondsPerQuestion", 30)).body().path("state");
        String id = state.path("id").asString();
        guest.post("/api/battles/join", Map.of("code", state.path("roomCode").asString()));
        host.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        guest.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        host.post("/api/battles/" + id + "/start", null);
        JsonNode live = await().atMost(Duration.ofSeconds(10)).until(
                () -> host.get("/api/battles/" + id).body().path("state").path("live"), n -> !n.isNull() && !n.isMissingNode());
        String option = live.path("question").path("options").get(0).path("id").asString();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> host.post("/api/battles/" + id + "/answer", Map.of("questionIndex", 0, "optionId", option)).status());
        }
        int ok = 0;
        for (Future<Integer> f : pool.invokeAll(calls)) if (f.get() == 200) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from quiz_answers a join quiz_battle_questions q on q.id = a.battle_question_id
                where q.battle_id = ?::uuid and a.user_id = ?
                """, Integer.class, id, host.userId)).isEqualTo(1);
    }

    @Test
    void outsidersCannotAnswerAndOptionsMustBelongToQuestion() {
        ApiClient host = ApiClient.registered(port, "h3");
        ApiClient guest = ApiClient.registered(port, "g3");
        ApiClient outsider = ApiClient.registered(port, "o3");
        JsonNode state = host.post("/api/battles", Map.of("category", "DATABASES", "difficulty", "EASY",
                "questionCount", 3, "secondsPerQuestion", 30)).body().path("state");
        String id = state.path("id").asString();
        guest.post("/api/battles/join", Map.of("code", state.path("roomCode").asString()));
        host.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        guest.post("/api/battles/" + id + "/ready", Map.of("ready", true));
        host.post("/api/battles/" + id + "/start", null);
        JsonNode live = await().atMost(Duration.ofSeconds(10)).until(
                () -> host.get("/api/battles/" + id).body().path("state").path("live"), n -> !n.isNull() && !n.isMissingNode());
        String option = live.path("question").path("options").get(0).path("id").asString();

        assertThat(outsider.post("/api/battles/" + id + "/answer", Map.of("questionIndex", 0, "optionId", option)).status())
                .isEqualTo(404);
        assertThat(guest.post("/api/battles/" + id + "/answer",
                Map.of("questionIndex", 0, "optionId", java.util.UUID.randomUUID().toString())).status()).isEqualTo(400);
        assertThat(guest.post("/api/battles/" + id + "/answer", Map.of("questionIndex", 2, "optionId", option)).status())
                .isEqualTo(409);
    }
}
