package com.smarthire.user.dto;

import com.smarthire.enums.Enums.UserStatus;
import com.smarthire.user.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getCode(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
