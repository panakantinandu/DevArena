package com.devarena.battles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quiz_battle_participants")
@Getter
@Setter
public class BattleParticipant {
    @Id
    private UUID id;

    @Column(name = "battle_id", nullable = false)
    private UUID battleId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean ready;

    @Column(nullable = false)
    private int score;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
