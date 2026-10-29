package com.devarena.realtime;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Handles one client-to-server message type other than the built-in SUBSCRIBE/UNSUBSCRIBE/PING. */
public interface ClientMessageHandler {
    String type();

    /**
     * @param userId the authenticated sender (from the handshake ticket, never from the message)
     * @param payload the message's {@code data} field
     * @param reply sends a message back to this connection only
     */
    void handle(UUID userId, JsonNode payload, Reply reply);

    interface Reply {
        void send(String type, Object data);
    }
}
