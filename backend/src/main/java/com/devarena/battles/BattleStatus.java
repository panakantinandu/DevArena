package com.devarena.battles;

import java.util.Map;
import java.util.Set;

/** Battle lifecycle. Any transition not listed here is rejected. */
public enum BattleStatus {
    WAITING, READY, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<BattleStatus, Set<BattleStatus>> ALLOWED = Map.of(
            WAITING, Set.of(READY, CANCELLED),
            READY, Set.of(WAITING, IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    public boolean canMoveTo(BattleStatus next) {
        return this == next || ALLOWED.get(this).contains(next);
    }

    public boolean isOpenLobby() {
        return this == WAITING || this == READY;
    }
}
