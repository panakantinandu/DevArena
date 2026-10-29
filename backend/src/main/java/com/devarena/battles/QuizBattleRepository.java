package com.devarena.battles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizBattleRepository extends JpaRepository<QuizBattle, UUID> {
    Optional<QuizBattle> findByRoomCode(String roomCode);

    List<QuizBattle> findByStatus(BattleStatus status);
}
