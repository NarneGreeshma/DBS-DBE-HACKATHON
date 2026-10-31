package com.smarthire.auth;

import com.smarthire.auth.dto.AuthResponse;
import com.smarthire.auth.dto.LoginRequest;
import com.smarthire.auth.dto.RegisterRequest;
import com.smarthire.candidate.CandidateProfile;
import com.smarthire.candidate.CandidateProfileRepository;
import com.smarthire.common.ConflictException;
import com.smarthire.enums.Enums.RoleCode;
import com.smarthire.enums.Enums.UserStatus;
import com.smarthire.role.Role;
import com.smarthire.role.RoleRepository;
import com.smarthire.security.AuthenticatedUser;
import com.smarthire.security.JwtService;
import com.smarthire.user.User;
import com.smarthire.user.UserRepository;
import com.smarthire.user.dto.UserDto;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Public self-registration always yields a CANDIDATE account. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        Role candidateRole = roleRepository.findByCode(RoleCode.CANDIDATE.name())
                .orElseThrow(() -> new IllegalStateException("Role CANDIDATE is missing - run Flyway migrations"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setRole(candidateRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(user.getId());
        profile.setExperienceYears(0);
        profile.setHeadline(blankToNull(request.headline()));
        profile.setLocation(blankToNull(request.location()));
        candidateProfileRepository.save(profile);

        log.info("Registered new candidate account {}", email);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new com.smarthire.common.BusinessException("This account is " + user.getStatus());
        }

        user.setLastLoginAt(Instant.now());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.parse(refreshToken);
        if (claims == null || !JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }
        User user = userRepository.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new com.smarthire.common.BusinessException("This account is " + user.getStatus());
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> com.smarthire.common.ResourceNotFoundException.of("User", userId));
        return UserDto.from(user);
    }

    private AuthResponse issueTokens(User user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        String access = jwtService.createAccessToken(principal);
        String refresh = jwtService.createRefreshToken(principal);
        return AuthResponse.bearer(access, refresh, jwtService.accessTokenTtlSeconds(), UserDto.from(user));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
