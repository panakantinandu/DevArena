package com.devarena.interviews;

import com.devarena.common.ApiException;
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
@Table(name = "interview_sessions")
@Getter
@Setter
public class InterviewSession {
    @Id
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false)
    private InterviewType interviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.CREATED;

    @Column(name = "interviewer_id", nullable = false)
    private UUID interviewerId;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "current_index", nullable = false)
    private int currentIndex;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public void transitionTo(InterviewStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("INVALID_TRANSITION", "Interview can't go from " + status + " to " + next);
        }
        status = next;
    }

    public boolean isInterviewer(UUID userId) { return interviewerId.equals(userId); }

    public boolean isCandidate(UUID userId) { return userId.equals(candidateId); }

    public boolean isParticipant(UUID userId) { return isInterviewer(userId) || isCandidate(userId); }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
