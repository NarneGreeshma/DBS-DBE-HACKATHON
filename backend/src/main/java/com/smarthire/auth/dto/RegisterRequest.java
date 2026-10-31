package com.smarthire.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration. The role is deliberately absent: unauthenticated visitors
 * can only become CANDIDATEs. Recruiters/admins are provisioned through
 * {@code POST /api/admin/users}.
 */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain a letter")
        String password,

        @Size(max = 32, message = "Phone must be at most 32 characters")
        String phone,

        @Size(max = 160)
        String headline,

        @Size(max = 120)
        String location) {
}
