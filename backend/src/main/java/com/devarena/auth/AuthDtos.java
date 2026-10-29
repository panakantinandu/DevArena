package com.devarena.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 3, max = 32)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Use letters, numbers and underscores only")
            String username,
            @NotBlank @Size(min = 2, max = 60) String displayName,
            @NotBlank @Size(min = 10, max = 128, message = "Use at least 10 characters") String password,
            @Size(max = 64) String timeZone) {}

    public record LoginRequest(@NotBlank @Size(max = 254) String login, @NotBlank @Size(max = 128) String password) {}

    public record WsTicketResponse(String ticket, int expiresInSeconds) {}
}
