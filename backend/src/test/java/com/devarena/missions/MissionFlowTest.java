package com.devarena.missions;

import static org.assertj.core.api.Assertions.assertThat;

import com.devarena.support.ApiClient;
import com.devarena.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
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

class MissionFlowTest extends IntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test
    void completesQuestionMissionOnceAndAwardsXpOnce() {
        ApiClient user = ApiClient.registered(port, "mia");
        ApiClient.Response today = user.get("/api/missions/today");
        assertThat(today.status()).isEqualTo(200);
        JsonNode missions = today.body().path("missions");
        assertThat(missions.size()).isGreaterThanOrEqualTo(today.body().path("dailyMinimum").asInt());
        // Asking again returns the same stable plan.
        assertThat(user.get("/api/missions/today").body().path("missions").get(0).path("id").asString())
                .isEqualTo(missions.get(0).path("id").asString());

        String missionId = firstOfKind(missions, "QUESTION_SET");
        ApiClient.Response detail = user.get("/api/missions/" + missionId);
        assertThat(detail.body().path("review").isNull()).isTrue();
        assertThat(detail.raw()).doesNotContain("\"correct\":true").doesNotContain("correctOptionId");

        assertThat(user.post("/api/missions/" + missionId + "/submit", Map.of("answers", Map.of())).status()).isEqualTo(400);

        Map<String, Object> answers = firstOptionAnswers(detail.body());
        ApiClient.Response first = user.post("/api/missions/" + missionId + "/submit", Map.of("answers", answers));
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("firstCompletion").asBoolean()).isTrue();
        assertThat(first.body().path("xpAwarded").asInt()).isPositive();
        assertThat(first.body().path("detail").path("review").size()).isEqualTo(answers.size());

        ApiClient.Response again = user.post("/api/missions/" + missionId + "/submit", Map.of("answers", answers));
        assertThat(again.body().path("firstCompletion").asBoolean()).isFalse();
        assertThat(again.body().path("xpAwarded").asInt()).isZero();
        assertThat(xpRows(user, missionId)).isEqualTo(1);
    }

    @Test
    void concurrentSubmissionsAwardXpExactlyOnce() throws Exception {
        ApiClient user = ApiClient.registered(port, "conc");
        JsonNode missions = user.get("/api/missions/today").body().path("missions");
        String missionId = firstOfKind(missions, "QUESTION_SET");
        Map<String, Object> answers = firstOptionAnswers(user.get("/api/missions/" + missionId).body());

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<ApiClient.Response>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) calls.add(() -> user.post("/api/missions/" + missionId + "/submit", Map.of("answers", answers)));
        int firsts = 0;
        for (Future<ApiClient.Response> f : pool.invokeAll(calls)) {
            ApiClient.Response r = f.get();
            assertThat(r.status()).isIn(200, 409);
            if (r.status() == 200 && r.body().path("firstCompletion").asBoolean()) firsts++;
        }
        pool.shutdown();
        assertThat(firsts).isEqualTo(1);
        assertThat(xpRows(user, missionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from mission_completions where assignment_id = ?::uuid",
                Integer.class, missionId)).isEqualTo(1);
    }

    @Test
    void cannotSeeOrSubmitSomeoneElsesMission() {
        ApiClient owner = ApiClient.registered(port, "owner");
        ApiClient intruder = ApiClient.registered(port, "intruder");
        String missionId = owner.get("/api/missions/today").body().path("missions").get(0).path("id").asString();
        assertThat(intruder.get("/api/missions/" + missionId).status()).isEqualTo(404);
        assertThat(intruder.post("/api/missions/" + missionId + "/submit", Map.of("answers", Map.of())).status()).isEqualTo(404);
    }

    @Test
    void studyMissionRevealsGuideOnlyAfterSubmission() {
        ApiClient user = ApiClient.registered(port, "study");
        JsonNode missions = user.get("/api/missions/today").body().path("missions");
        String id = firstOfKindOrNull(missions, "INTERVIEW_STUDY");
        if (id == null) return; // not assigned for this budget today
        JsonNode before = user.get("/api/missions/" + id).body().path("interviewItems").get(0);
        assertThat(before.path("answerOutline").isNull()).isTrue();
        assertThat(user.post("/api/missions/" + id + "/submit", Map.of("notes", "too short", "selfRating", 3)).status())
                .isEqualTo(400);
        ApiClient.Response done = user.post("/api/missions/" + id + "/submit",
                Map.of("notes", "My answer covers the main idea, the trade-offs, an example and the edge cases I know.", "selfRating", 3));
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.body().path("detail").path("interviewItems").get(0).path("answerOutline").asString()).isNotBlank();
    }

    private long xpRows(ApiClient user, String missionId) {
        Long n = jdbc.queryForObject("select count(*) from xp_transactions where user_id = ? and source_type = 'MISSION' and source_id = ?",
                Long.class, user.userId, missionId);
        return n == null ? 0 : n;
    }

    static String firstOfKind(JsonNode missions, String kind) {
        String id = firstOfKindOrNull(missions, kind);
        if (id == null) throw new AssertionError("No " + kind + " mission assigned");
        return id;
    }

    static String firstOfKindOrNull(JsonNode missions, String kind) {
        for (JsonNode m : missions) if (m.path("kind").asString().equals(kind)) return m.path("id").asString();
        return null;
    }

    static Map<String, Object> firstOptionAnswers(JsonNode detail) {
        Map<String, Object> answers = new HashMap<>();
        for (JsonNode q : detail.path("questions")) {
            answers.put(q.path("id").asString(), q.path("options").get(0).path("id").asString());
        }
        return answers;
    }
}
