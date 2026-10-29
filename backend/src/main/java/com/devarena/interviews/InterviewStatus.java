package com.devarena.interviews;

import java.util.Map;
import java.util.Set;

/** Interview lifecycle. Transitions not listed here are rejected. */
public enum InterviewStatus {
    CREATED, LOBBY, ACTIVE, FEEDBACK, COMPLETED, CANCELLED;

    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED = Map.of(
            CREATED, Set.of(LOBBY, CANCELLED),
            LOBBY, Set.of(ACTIVE, CANCELLED),
            ACTIVE, Set.of(FEEDBACK, CANCELLED),
            FEEDBACK, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    public boolean canMoveTo(InterviewStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
