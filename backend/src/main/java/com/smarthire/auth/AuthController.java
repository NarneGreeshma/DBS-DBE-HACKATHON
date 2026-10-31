package com.smarthire.auth;

import com.smarthire.auth.dto.AuthResponse;
import com.smarthire.auth.dto.LoginRequest;
import com.smarthire.auth.dto.RefreshRequest;
import com.smarthire.auth.dto.RegisterRequest;
import com.smarthire.security.CurrentUser;
import com.smarthire.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login and token refresh")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Self-registration (always creates a CANDIDATE account)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access + refresh token pair")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a fresh token pair")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user")
    public UserDto me() {
        return authService.me(CurrentUser.requireId());
    }
}
