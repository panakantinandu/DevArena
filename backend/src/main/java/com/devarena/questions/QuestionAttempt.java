package com.devarena.questions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "question_attempts")
@Getter
@Setter
public class QuestionAttempt {
    public enum Context { PRACTICE, MISSION, BATTLE }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "selected_option_id")
    private UUID selectedOptionId;

    @Column(nullable = false)
    private boolean correct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Context context;

    @Column(name = "context_id")
    private UUID contextId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
