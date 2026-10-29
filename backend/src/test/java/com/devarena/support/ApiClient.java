package com.devarena.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A browser-like HTTP client for tests: keeps cookies (SESSION, XSRF-TOKEN) and echoes the
 * CSRF token header on unsafe methods, exactly like the SPA does.
 */
public class ApiClient {
    public record Response(int status, JsonNode body, String raw) {
        public String text(String field) { return body.path(field).asString(); }
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public ApiClient(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public static ApiClient registered(int port, String name) {
        ApiClient c = new ApiClient(port);
        String u = name + "_" + UUID.randomUUID().toString().substring(0, 8);
        Response r = c.post("/api/auth/register", Map.of("email", u + "@example.com", "username", u,
                "displayName", name, "password", "correct-horse-battery", "timeZone", "Europe/Berlin"));
        if (r.status() != 201) throw new IllegalStateException("Registration failed: " + r.raw());
        c.userId = UUID.fromString(r.text("id"));
        c.username = u;
        return c;
    }

    public UUID userId;
    public String username;

    public Response get(String path) { return send("GET", path, null); }

    public Response post(String path, Object body) { return send("POST", path, body); }

    public Response put(String path, Object body) { return send("PUT", path, body); }

    public Response delete(String path) { return send("DELETE", path, null); }

    private Response send(String method, String path, Object body) {
        try {
            if (!method.equals("GET") && csrf() == null) {
                http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "application/json");
            String token = csrf();
            if (token != null && !method.equals("GET")) b.header("X-XSRF-TOKEN", token);
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String raw = res.body();
            JsonNode node = raw == null || raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
            return new Response(res.statusCode(), node, raw);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String csrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse(null);
    }

    public void dropCsrfCookie() {
        cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .toList().forEach(c -> cookies.getCookieStore().remove(null, c));
    }
}
