package com.devarena.missions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionAssignmentRepository extends JpaRepository<MissionAssignment, UUID> {
    List<MissionAssignment> findByUserIdAndAssignedDateOrderBySlot(UUID userId, LocalDate date);
}
