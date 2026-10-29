package com.devarena.realtime;

import java.util.UUID;

/**
 * Authorizes subscriptions to one family of topics, e.g. {@code battle:<id>}.
 * Every module that publishes real-time events implements one of these.
 */
public interface RoomAccess {
    /** Topic prefix this access policy owns, without the colon (e.g. "battle"). */
    String prefix();

    /** Whether the user may receive events for the room with this id. */
    boolean canSubscribe(UUID userId, UUID roomId);
}
