package com.devarena.questions;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {}
