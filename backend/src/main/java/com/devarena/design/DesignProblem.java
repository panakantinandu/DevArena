package com.devarena.design;

import com.devarena.common.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "design_problems")
@Getter
@Setter
public class DesignProblem {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "functional_reqs", nullable = false)
    private List<String> functionalReqs;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "non_functional_reqs", nullable = false)
    private List<String> nonFunctionalReqs;

    /** Interviewer-only. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "discussion_topics", nullable = false)
    private List<String> discussionTopics;

    /** Interviewer-only. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "follow_ups", nullable = false)
    private List<String> followUps;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
