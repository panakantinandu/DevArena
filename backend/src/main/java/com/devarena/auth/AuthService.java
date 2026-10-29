package com.devarena.auth;

import com.devarena.auth.AuthDtos.RegisterRequest;
import com.devarena.common.ApiException;
import com.devarena.common.Ids;
import com.devarena.users.User;
import com.devarena.users.UserRepository;
import com.devarena.users.UserSettings;
import com.devarena.users.UserSettingsRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(UserRepository users, UserSettingsRepository settings, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.settings = settings;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public User register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String username = req.username().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }
        ZoneId zone = parseZone(req.timeZone());

        User user = new User();
        user.setId(Ids.newId());
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(req.displayName().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        users.saveAndFlush(user);

        UserSettings s = new UserSettings();
        s.setUserId(user.getId());
        s.setTimeZone(zone.getId());
        s.setRoadmapStartDate(LocalDate.now(clock.withZone(zone)));
        settings.save(s);
        return user;
    }

    static ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) return ZoneId.of("UTC");
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIME_ZONE", "Unknown time zone");
        }
    }
}
