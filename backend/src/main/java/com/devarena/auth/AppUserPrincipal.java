package com.devarena.auth;

import com.devarena.users.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated identity stored in the (JDBC-backed) HTTP session. Holds only what
 * authorization needs; profile data is always re-read from the database.
 */
public record AppUserPrincipal(UUID id, String username, String passwordHash, String role) implements UserDetails {

    public static AppUserPrincipal from(User u) {
        return new AppUserPrincipal(u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole().name());
    }

    /** Copy without the hash, which is what we keep in the session. */
    public AppUserPrincipal withoutPassword() {
        return new AppUserPrincipal(id, username, null, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    public boolean isAdmin() { return "ADMIN".equals(role); }
}
