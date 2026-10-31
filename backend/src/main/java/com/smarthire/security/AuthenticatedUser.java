package com.smarthire.security;

import com.smarthire.user.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * Principal placed into the {@code SecurityContext} by {@link JwtAuthenticationFilter}.
 * It always reflects a freshly loaded {@link User}, so suspending an account takes
 * effect on the very next request.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String fullName,
        String role,
        String passwordHash,
        boolean enabled) implements UserDetails {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getCode(),
                user.getPasswordHash(),
                user.getStatus() == com.smarthire.enums.Enums.UserStatus.ACTIVE);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isStaff() {
        return "ADMIN".equals(role) || "RECRUITER".equals(role);
    }

    @Override
    public List<SimpleGrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
