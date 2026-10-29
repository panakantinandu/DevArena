package com.devarena.users;

import com.devarena.common.ApiException;
import com.devarena.users.UserDtos.MeResponse;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserDtos.UpdateProfileRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final UserSettingsRepository settings;

    public UserService(UserRepository users, UserSettingsRepository settings) {
        this.users = users;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User u = require(userId);
        UserSettings s = settings(userId);
        return new MeResponse(u.getId(), u.getEmail(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                u.getBio(), u.getRole().name(), u.getCreatedAt(), UserDtos.toSettings(s));
    }

    @Transactional
    public MeResponse update(UUID userId, UpdateProfileRequest req) {
        try {
            ZoneId.of(req.timeZone());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIME_ZONE", "Unknown time zone");
        }
        User u = require(userId);
        u.setDisplayName(req.displayName().trim());
        u.setAvatarUrl(blankToNull(req.avatarUrl()));
        u.setBio(blankToNull(req.bio()));

        UserSettings s = settings(userId);
        s.setPreferredLanguage(req.preferredLanguage());
        s.setTargetRole(req.targetRole());
        s.setPrepLevel(req.prepLevel());
        s.setDailyStudyMinutes(req.dailyStudyMinutes());
        s.setDailyMissionMinimum(req.dailyMissionMinimum());
        s.setTimeZone(req.timeZone());
        s.setOnboarded(true);
        return me(userId);
    }

    @Transactional(readOnly = true)
    public List<PublicUser> search(String query, UUID me) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        return users.search(q, me, PageRequest.of(0, 20)).stream().map(UserDtos::toPublic).toList();
    }

    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public UserSettings settings(UUID userId) {
        return settings.findById(userId).orElseThrow(() -> ApiException.notFound("Settings"));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
