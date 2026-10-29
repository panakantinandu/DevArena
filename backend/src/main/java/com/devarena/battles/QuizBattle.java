package com.devarena.battles;

import com.devarena.common.ApiException;
import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quiz_battles")
@Getter
@Setter
public class QuizBattle {
    @Id
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true)
    private String roomCode;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "seconds_per_question", nullable = false)
    private int secondsPerQuestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BattleStatus status = BattleStatus.WAITING;

    @Column(name = "current_index", nullable = false)
    private int currentIndex = -1;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** The only way to change status, so illegal transitions can't slip through. */
    public void transitionTo(BattleStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("INVALID_TRANSITION", "Battle can't go from " + status + " to " + next);
        }
        status = next;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
