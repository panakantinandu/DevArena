package com.devarena.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.devarena.support.ApiClient;
import com.devarena.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

class InterviewFlowTest extends IntegrationTest {
    @LocalServerPort int port;

    @Test
    void fullInterviewWithRoleSeparationAndSavedFeedback() {
        ApiClient interviewer = ApiClient.registered(port, "ivr");
        ApiClient candidate = ApiClient.registered(port, "cand");
        ApiClient stranger = ApiClient.registered(port, "strg");

        ApiClient.Response created = interviewer.post("/api/interviews", Map.of("type", "JAVA_BACKEND", "difficulty", "MEDIUM",
                "durationMinutes", 45, "questionCount", 2));
        assertThat(created.status()).isEqualTo(201);
        String id = created.text("id");
        String code = created.text("roomCode");

        assertThat(candidate.post("/api/interviews/join", Map.of("code", code)).status()).isEqualTo(200);
        // A third person can't take the candidate seat or read the room.
        assertThat(stranger.post("/api/interviews/join", Map.of("code", code)).status()).isEqualTo(409);
        assertThat(stranger.get("/api/interviews/" + id).status()).isEqualTo(404);

        // Only the interviewer can start.
        assertThat(candidate.post("/api/interviews/" + id + "/start", null).status()).isEqualTo(403);
        assertThat(interviewer.post("/api/interviews/" + id + "/start", null).status()).isEqualTo(200);

        // The candidate's view has the question but none of the guide.
        ApiClient.Response cView = candidate.get("/api/interviews/" + id);
        assertThat(cView.body().path("current").path("prompt").asString()).isNotBlank();
        assertThat(cView.raw()).doesNotContain("answerOutline").doesNotContain("expectedConcepts")
                .doesNotContain("commonMistakes").doesNotContain("privateNote").doesNotContain("rubricNotes");
        // The interviewer-only endpoint is refused for the candidate at the API layer.
        assertThat(candidate.get("/api/interviews/" + id + "/interviewer").status()).isEqualTo(403);

        JsonNode guide = interviewer.get("/api/interviews/" + id + "/interviewer").body();
        assertThat(guide.path("questions").get(0).path("answerOutline").asString()).isNotBlank();
        String sq = guide.path("questions").get(0).path("sessionQuestionId").asString();
        assertThat(interviewer.put("/api/interviews/" + id + "/notes", Map.of("sessionQuestionId", sq,
                "body", "Missed treeification; strong on resizing.")).status()).isEqualTo(204);
        assertThat(candidate.put("/api/interviews/" + id + "/notes", Map.of("body", "hack")).status()).isEqualTo(403);

        // Candidate only sees asked questions: one so far.
        assertThat(cView.body().path("asked").size()).isEqualTo(1);
        interviewer.post("/api/interviews/" + id + "/next", null);
        assertThat(candidate.get("/api/interviews/" + id).body().path("asked").size()).isEqualTo(2);

        // Evaluation is only possible after finishing, and only by the interviewer.
        Map<String, Object> eval = Map.of("technical", 4, "depth", 3, "problemSolving", 4, "communication", 5,
                "followUpHandling", 3, "strengths", "Clear structure", "improvements", "Go deeper on JVM internals");
        assertThat(interviewer.post("/api/interviews/" + id + "/evaluation", eval).status()).isEqualTo(409);
        interviewer.post("/api/interviews/" + id + "/finish", null);
        assertThat(candidate.post("/api/interviews/" + id + "/evaluation", eval).status()).isEqualTo(403);
        ApiClient.Response report = interviewer.post("/api/interviews/" + id + "/evaluation", eval);
        assertThat(report.status()).isEqualTo(200);
        // 4*.30 + 3*.20 + 4*.20 + 5*.15 + 3*.15 = 3.80
        assertThat(report.body().path("evaluation").path("overall").asDouble()).isEqualTo(3.80);

        ApiClient.Response candidateReport = candidate.get("/api/interviews/" + id + "/report");
        assertThat(candidateReport.status()).isEqualTo(200);
        assertThat(candidateReport.body().path("evaluation").path("strengths").asString()).isEqualTo("Clear structure");
        // Private notes never reach the candidate, even after the interview.
        assertThat(candidateReport.raw()).doesNotContain("Missed treeification");

        // Roles swap on rematch.
        ApiClient.Response rematch = candidate.post("/api/interviews/" + id + "/rematch", null);
        assertThat(rematch.status()).isEqualTo(201);
        assertThat(rematch.body().path("interviewer").path("id").asString()).isEqualTo(candidate.userId.toString());
        assertThat(rematch.body().path("candidate").path("id").asString()).isEqualTo(interviewer.userId.toString());
    }
}
