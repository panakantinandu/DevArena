package com.devarena.interviews;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    Optional<InterviewSession> findByRoomCode(String roomCode);

    @Query("""
            select s from InterviewSession s where s.interviewerId = :u or s.candidateId = :u
            order by s.createdAt desc
            """)
    List<InterviewSession> findMine(@Param("u") UUID userId, Pageable pageable);
}
