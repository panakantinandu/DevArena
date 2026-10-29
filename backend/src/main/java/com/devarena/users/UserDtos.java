package com.devarena.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    /** The signed-in user's own view. Never includes the password hash. */
    public record MeResponse(
            UUID id, String email, String username, String displayName, String avatarUrl, String bio,
            String role, Instant createdAt, SettingsResponse settings) {}

    public record SettingsResponse(
            String preferredLanguage, String targetRole, String prepLevel, int dailyStudyMinutes,
            int dailyMissionMinimum, String timeZone, LocalDate roadmapStartDate, boolean onboarded) {}

    /** What other users may see. No email, no settings. */
    public record PublicUser(UUID id, String username, String displayName, String avatarUrl) {}

    public record PublicProfile(
            UUID id, String username, String displayName, String avatarUrl, String bio, String targetRole,
            long totalXp, int level, String levelTitle, int currentStreak, String friendship) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 60) String displayName,
            @Size(max = 500) @Pattern(regexp = "^$|^https://.+", message = "Avatar must be an https URL")
            String avatarUrl,
            @Size(max = 500) String bio,
            @NotBlank @Pattern(regexp = "JAVA|PYTHON|JAVASCRIPT|TYPESCRIPT|GO|CPP|KOTLIN") String preferredLanguage,
            @NotBlank @Pattern(regexp = "BACKEND_ENGINEER|FULL_STACK_ENGINEER|FRONTEND_ENGINEER|PLATFORM_ENGINEER|AI_ENGINEER")
            String targetRole,
            @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED") String prepLevel,
            @Min(15) @Max(480) int dailyStudyMinutes,
            @Min(1) @Max(7) int dailyMissionMinimum,
            @NotBlank @Size(max = 64) String timeZone) {}

    public static PublicUser toPublic(User u) {
        return new PublicUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl());
    }

    public static SettingsResponse toSettings(UserSettings s) {
        return new SettingsResponse(s.getPreferredLanguage(), s.getTargetRole(), s.getPrepLevel(),
                s.getDailyStudyMinutes(), s.getDailyMissionMinimum(), s.getTimeZone(), s.getRoadmapStartDate(),
                s.isOnboarded());
    }
}
