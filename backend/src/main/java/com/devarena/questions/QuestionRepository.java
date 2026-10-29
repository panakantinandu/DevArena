package com.devarena.questions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @EntityGraph(attributePaths = {"options", "topic"})
    @Query("select q from Question q where q.id in :ids")
    List<Question> findWithOptions(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = {"options", "topic"})
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findOneWithOptions(@Param("id") UUID id);

    Optional<Question> findBySlug(String slug);
}
