package com.devarena.coding;

import com.devarena.common.Difficulty;
import com.devarena.questions.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coding_problems")
@Getter
@Setter
public class CodingProblem {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String statement;

    @Column(nullable = false)
    private String examples;

    @Column(name = "constraints_text", nullable = false)
    private String constraintsText;

    @Column(name = "starter_code", nullable = false)
    private String starterCode;

    /** Interviewer-only. Never mapped into a candidate-facing DTO. */
    @Column(name = "solution_outline", nullable = false)
    private String solutionOutline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
