package com.devarena.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from {@code devarena.*}. Every value can be overridden
 * through environment variables (e.g. DEVARENA_CORS_ALLOWED_ORIGINS).
 */
@ConfigurationProperties(prefix = "devarena")
public record AppProperties(
        List<String> corsAllowedOrigins,
        boolean secureCookies,
        int loginMaxAttempts,
        int loginLockMinutes,
        int wsTicketSeconds,
        boolean seedDemoUsers) {

    public AppProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        if (loginMaxAttempts <= 0) loginMaxAttempts = 5;
        if (loginLockMinutes <= 0) loginLockMinutes = 15;
        if (wsTicketSeconds <= 0) wsTicketSeconds = 30;
    }
}
