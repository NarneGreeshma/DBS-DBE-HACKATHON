package com.smarthire.user;

import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.security.CurrentUser;
import com.smarthire.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints available to every authenticated role.
 * Candidate-specific career data lives in {@code CandidateController}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Account self-service")
public class UserController {

    private final UserRepository userRepository;

    public record UpdateAccountRequest(
            @Size(min = 2, max = 120, message = "Name must be 2-120 characters") String fullName,
            @Size(max = 32, message = "Phone must be at most 32 characters") String phone) {
    }

    @GetMapping("/me")
    @Operation(summary = "Current account")
    public UserDto me() {
        return UserDto.from(require(CurrentUser.requireId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update own display name and phone")
    @Transactional
    public UserDto updateMe(@Valid @RequestBody UpdateAccountRequest request) {
        User user = require(CurrentUser.requireId());
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        return UserDto.from(userRepository.save(user));
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
