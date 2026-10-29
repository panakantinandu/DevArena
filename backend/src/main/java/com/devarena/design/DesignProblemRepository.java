package com.devarena.design;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignProblemRepository extends JpaRepository<DesignProblem, UUID> {
    List<DesignProblem> findAllByOrderByDifficultyAscTitleAsc();

    Optional<DesignProblem> findBySlug(String slug);
}
