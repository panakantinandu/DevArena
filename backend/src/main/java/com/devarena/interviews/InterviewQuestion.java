package com.devarena.interviews;

import com.devarena.coding.CodingProblem;
import com.devarena.common.Difficulty;
import com.devarena.design.DesignProblem;
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
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "interview_questions")
@Getter
@Setter
public class InterviewQuestion {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false)
    private InterviewType interviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String prompt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coding_problem_id")
    private CodingProblem codingProblem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_problem_id")
    private DesignProblem designProblem;

    // ---- Interviewer-only guide. Never mapped into a candidate-facing DTO. ----

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "expected_concepts", nullable = false)
    private List<String> expectedConcepts;

    @Column(name = "answer_outline", nullable = false)
    private String answerOutline;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "common_mistakes", nullable = false)
    private List<String> commonMistakes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "follow_ups", nullable = false)
    private List<String> followUps;

    @Column(name = "rubric_notes", nullable = false)
    private String rubricNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
