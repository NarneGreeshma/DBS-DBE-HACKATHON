package com.smarthire.auth.dto;

import com.smarthire.user.dto.UserDto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String tokenType,
        UserDto user) {

    public static AuthResponse bearer(String access, String refresh, long ttlSeconds, UserDto user) {
        return new AuthResponse(access, refresh, ttlSeconds, "Bearer", user);
    }
}
