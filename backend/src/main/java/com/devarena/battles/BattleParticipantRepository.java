package com.devarena.battles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleParticipantRepository extends JpaRepository<BattleParticipant, UUID> {
    List<BattleParticipant> findByBattleIdOrderByJoinedAt(UUID battleId);

    Optional<BattleParticipant> findByBattleIdAndUserId(UUID battleId, UUID userId);

    boolean existsByBattleIdAndUserId(UUID battleId, UUID userId);
}
