package com.devarena.questions;

import com.devarena.common.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Optional<Topic> findBySlug(String slug);

    List<Topic> findAllByOrderBySortOrderAsc();

    List<Topic> findByCategoryOrderBySortOrderAsc(Category category);
}
