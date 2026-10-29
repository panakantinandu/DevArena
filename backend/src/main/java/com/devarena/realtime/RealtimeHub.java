package com.devarena.realtime;

import com.devarena.auth.AppUserPrincipal;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The single WebSocket endpoint. See docs/realtime-protocol.md for the message contract.
 *
 * <p>Clients send {@code {"type": "...", "topic"?: "...", "data"?: {...}}}. The server sends
 * {@code {"type": "...", "topic"?: "...", "data": {...}, "serverTime": epochMillis}}.
 * Subscriptions are authorized per topic by the module that owns it. The sender's identity
 * always comes from the handshake ticket, never from message content.
 */
@Component
public class RealtimeHub extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
    private static final int MAX_MESSAGE_CHARS = 64 * 1024;

    private record Conn(WebSocketSession session, UUID userId, Set<String> topics) {}

    private final Map<String, Conn> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> topicMembers = new ConcurrentHashMap<>();
    // Resolved lazily: room modules depend on the hub to publish, and the hub needs them to authorize.
    private final ObjectProvider<RoomAccess> accessProvider;
    private final ObjectProvider<ClientMessageHandler> handlerProvider;
    private volatile Map<String, RoomAccess> access;
    private volatile Map<String, ClientMessageHandler> handlers;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RealtimeHub(ObjectProvider<RoomAccess> access, ObjectProvider<ClientMessageHandler> handlers,
                       ObjectMapper mapper, Clock clock) {
        this.accessProvider = access;
        this.handlerProvider = handlers;
        this.mapper = mapper;
        this.clock = clock;
    }

    private Map<String, RoomAccess> access() {
        if (access == null) {
            access = accessProvider.orderedStream().collect(Collectors.toMap(RoomAccess::prefix, Function.identity()));
        }
        return access;
    }

    private Map<String, ClientMessageHandler> handlers() {
        if (handlers == null) {
            handlers = handlerProvider.orderedStream().collect(Collectors.toMap(ClientMessageHandler::type, Function.identity()));
        }
        return handlers;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AppUserPrincipal p = (AppUserPrincipal) session.getAttributes().get(WsAuthInterceptor.PRINCIPAL_ATTR);
        // Decorator serialises concurrent sends and bounds slow-consumer buffering.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024);
        connections.put(session.getId(), new Conn(safe, p.id(), ConcurrentHashMap.newKeySet()));
        send(safe, "WELCOME", null, Map.of("userId", p.id()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Conn conn = connections.get(session.getId());
        if (conn == null) return;
        if (message.getPayloadLength() > MAX_MESSAGE_CHARS) {
            error(conn, "MESSAGE_TOO_LARGE", "Message too large");
            return;
        }
        JsonNode msg;
        try {
            msg = mapper.readTree(message.getPayload());
        } catch (RuntimeException e) {
            error(conn, "BAD_MESSAGE", "Malformed JSON");
            return;
        }
        String type = msg.path("type").asString("");
        String topic = msg.path("topic").asString("");
        switch (type) {
            case "PING" -> send(conn.session(), "PONG", null, Map.of());
            case "SUBSCRIBE" -> subscribe(conn, topic);
            case "UNSUBSCRIBE" -> unsubscribe(conn, topic);
            default -> {
                ClientMessageHandler h = handlers().get(type);
                if (h == null) {
                    error(conn, "UNKNOWN_TYPE", "Unknown message type");
                    return;
                }
                try {
                    h.handle(conn.userId(), msg.path("data"), (t, d) -> send(conn.session(), t, null, d));
                } catch (RuntimeException e) {
                    log.debug("Handler {} rejected message", type, e);
                    error(conn, "REJECTED", e instanceof com.devarena.common.ApiException ? e.getMessage() : "Request rejected");
                }
            }
        }
    }

    private void subscribe(Conn conn, String topic) {
        RoomAccess policy = policyFor(topic);
        UUID roomId = roomId(topic);
        if (policy == null || roomId == null || !policy.canSubscribe(conn.userId(), roomId)) {
            error(conn, "SUBSCRIBE_DENIED", "You can't join this room");
            return;
        }
        conn.topics().add(topic);
        topicMembers.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(conn.session().getId());
        send(conn.session(), "SUBSCRIBED", topic, Map.of());
    }

    private void unsubscribe(Conn conn, String topic) {
        conn.topics().remove(topic);
        Set<String> members = topicMembers.get(topic);
        if (members != null) members.remove(conn.session().getId());
    }

    /** Sends an event to every connection subscribed to the topic. */
    public void publish(String topic, String type, Object data) {
        Set<String> members = topicMembers.get(topic);
        if (members == null) return;
        String json = envelope(type, topic, data);
        for (String id : members) {
            Conn c = connections.get(id);
            if (c != null) sendRaw(c.session(), json);
        }
    }

    /** Sends an event to all of one user's connections (e.g. a friend invitation). */
    public void sendToUser(UUID userId, String type, Object data) {
        String json = envelope(type, null, data);
        connections.values().stream().filter(c -> c.userId().equals(userId)).forEach(c -> sendRaw(c.session(), json));
    }

    /** Users currently subscribed to a topic; used for presence in rooms. */
    public Set<UUID> subscribers(String topic) {
        Set<String> members = topicMembers.getOrDefault(topic, Set.of());
        return members.stream().map(connections::get).filter(java.util.Objects::nonNull)
                .map(Conn::userId).collect(Collectors.toSet());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Conn conn = connections.remove(session.getId());
        if (conn == null) return;
        for (String topic : conn.topics()) {
            Set<String> members = topicMembers.get(topic);
            if (members != null) {
                members.remove(session.getId());
                if (members.isEmpty()) topicMembers.remove(topic, members);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket transport error", exception);
    }

    private RoomAccess policyFor(String topic) {
        int i = topic.indexOf(':');
        return i <= 0 ? null : access().get(topic.substring(0, i));
    }

    private static UUID roomId(String topic) {
        int i = topic.indexOf(':');
        if (i <= 0) return null;
        try {
            return UUID.fromString(topic.substring(i + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void error(Conn conn, String code, String message) {
        send(conn.session(), "ERROR", null, Map.of("code", code, "message", message));
    }

    private void send(WebSocketSession s, String type, String topic, Object data) {
        sendRaw(s, envelope(type, topic, data));
    }

    private String envelope(String type, String topic, Object data) {
        var node = mapper.createObjectNode();
        node.put("type", type);
        if (topic != null) node.put("topic", topic);
        node.set("data", mapper.valueToTree(data == null ? Map.of() : data));
        node.put("serverTime", clock.millis());
        return mapper.writeValueAsString(node);
    }

    private void sendRaw(WebSocketSession s, String json) {
        if (!s.isOpen()) return;
        try {
            s.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping message to closed or slow connection {}", s.getId());
        }
    }
}
