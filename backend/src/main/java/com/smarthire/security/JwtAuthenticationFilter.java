package com.smarthire.security;

import com.smarthire.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Extracts {@code Authorization: Bearer <jwt>}, validates it and populates the
 * {@code SecurityContext}. Invalid or missing tokens simply leave the context empty,
 * so the authorization rules downstream decide the outcome (401 vs 403).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        try {
            if (jwtService.isValid(token, JwtService.TYPE_ACCESS)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtService.userIdOf(token);
                if (userId != null) {
                    Optional<com.smarthire.user.User> user = userRepository.findById(userId);
                    if (user.isPresent()) {
                        AuthenticatedUser principal = AuthenticatedUser.from(user.get());
                        if (principal.isEnabled()) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            principal, null, principal.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Rejected bearer token on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
