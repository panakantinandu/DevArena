package com.devarena.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.devarena.support.ApiClient;
import com.devarena.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class AuthFlowTest extends IntegrationTest {
    @LocalServerPort int port;

    @Test
    void registerLoginMeLogout() {
        ApiClient alice = ApiClient.registered(port, "alice");
        ApiClient.Response me = alice.get("/api/users/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.text("username")).isEqualTo(alice.username);
        assertThat(me.raw()).doesNotContain("password").doesNotContain("$2a$");
        assertThat(me.body().path("settings").path("timeZone").asString()).isEqualTo("Europe/Berlin");

        assertThat(alice.post("/api/auth/logout", null).status()).isEqualTo(204);
        assertThat(alice.get("/api/users/me").status()).isEqualTo(401);

        ApiClient again = new ApiClient(port);
        ApiClient.Response login = again.post("/api/auth/login", Map.of("login", alice.username, "password", "correct-horse-battery"));
        assertThat(login.status()).isEqualTo(200);
        assertThat(again.get("/api/users/me").status()).isEqualTo(200);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/missions/today")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.body()).contains("UNAUTHENTICATED");
    }

    @Test
    void wrongPasswordIsRejectedWithGenericMessageAndThenThrottled() {
        ApiClient bob = ApiClient.registered(port, "bob");
        ApiClient attacker = new ApiClient(port);
        for (int i = 0; i < 5; i++) {
            ApiClient.Response r = attacker.post("/api/auth/login", Map.of("login", bob.username, "password", "nope-nope-nope"));
            assertThat(r.status()).isEqualTo(401);
            assertThat(r.text("message")).isEqualTo("Incorrect username or password");
        }
        // Even the right password is refused while the account is locked.
        ApiClient.Response locked = attacker.post("/api/auth/login", Map.of("login", bob.username, "password", "correct-horse-battery"));
        assertThat(locked.status()).isEqualTo(429);
    }

    @Test
    void unsafeRequestsWithoutCsrfTokenAreForbidden() {
        ApiClient carol = ApiClient.registered(port, "carol");
        carol.dropCsrfCookie();
        // Send a PUT with no token at all: build the request manually through a fresh client sharing nothing.
        ApiClient.Response ok = carol.get("/api/users/me");
        assertThat(ok.status()).isEqualTo(200);
        java.net.http.HttpResponse<String> res;
        try {
            res = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/auth/logout"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void duplicateRegistrationAndValidationErrors() {
        ApiClient dave = ApiClient.registered(port, "dave");
        ApiClient other = new ApiClient(port);
        ApiClient.Response dup = other.post("/api/auth/register", Map.of("email", dave.username + "@example.com",
                "username", dave.username + "x", "displayName", "Dave 2", "password", "correct-horse-battery"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.text("code")).isEqualTo("EMAIL_TAKEN");

        ApiClient.Response invalid = other.post("/api/auth/register", Map.of("email", "not-an-email",
                "username", "a b", "displayName", "D", "password", "short"));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("fieldErrors").has("password")).isTrue();
        assertThat(invalid.body().path("fieldErrors").has("email")).isTrue();
    }

    @Test
    void profileUpdatePersistsAndValidatesTimeZone() {
        ApiClient erin = ApiClient.registered(port, "erin");
        Map<String, Object> body = new java.util.HashMap<>(Map.of("displayName", "Erin K", "avatarUrl", "", "bio", "Backend dev",
                "preferredLanguage", "JAVA", "targetRole", "BACKEND_ENGINEER", "prepLevel", "ADVANCED",
                "dailyStudyMinutes", 90, "dailyMissionMinimum", 3, "timeZone", "Asia/Kolkata"));
        ApiClient.Response r = erin.put("/api/users/me", body);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("settings").path("onboarded").asBoolean()).isTrue();
        body.put("timeZone", "Mars/Olympus");
        assertThat(erin.put("/api/users/me", body).status()).isEqualTo(400);
    }
}
