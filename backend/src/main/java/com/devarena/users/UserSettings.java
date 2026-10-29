package com.devarena.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "JAVA";

    @Column(name = "target_role", nullable = false)
    private String targetRole = "BACKEND_ENGINEER";

    @Column(name = "prep_level", nullable = false)
    private String prepLevel = "INTERMEDIATE";

    @Column(name = "daily_study_minutes", nullable = false)
    private int dailyStudyMinutes = 60;

    /** Missions a user must finish on a local day for it to count toward the streak. */
    @Column(name = "daily_mission_minimum", nullable = false)
    private int dailyMissionMinimum = 2;

    @Column(name = "time_zone", nullable = false)
    private String timeZone = "UTC";

    @Column(name = "roadmap_start_date", nullable = false)
    private LocalDate roadmapStartDate;

    @Column(nullable = false)
    private boolean onboarded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
