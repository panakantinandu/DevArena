package com.devarena.interviews;

import com.devarena.common.Difficulty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {
    List<InterviewQuestion> findByInterviewTypeOrderBySlug(InterviewType type);

    List<InterviewQuestion> findByInterviewTypeAndDifficultyOrderBySlug(InterviewType type, Difficulty difficulty);
}
