package com.smarthire.admin;

import com.smarthire.common.ConflictException;
import com.smarthire.common.PageResponse;
import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.RoleCode;
import com.smarthire.enums.Enums.UserStatus;
import com.smarthire.role.Role;
import com.smarthire.role.RoleRepository;
import com.smarthire.security.CurrentUser;
import com.smarthire.user.User;
import com.smarthire.user.UserRepository;
import com.smarthire.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ADMIN-only user administration. Public self-registration always creates a CANDIDATE,
 * so this is the single place where privileged accounts (recruiters, other admins)
 * are provisioned - that is what keeps role-based access meaningful.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "User administration (ADMIN only)")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public record CreateUserRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72) String password,
            @NotNull(message = "Role is required") RoleCode role,
            @Size(max = 32) String phone) {
    }

    public record StatusRequest(@NotNull UserStatus status) {
    }

    @GetMapping
    @Operation(summary = "Search accounts")
    public PageResponse<UserDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        RoleCode roleFilter = role;
        UserStatus statusFilter = status;

        Specification<User> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (needle != null) {
                String like = "%" + needle + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("fullName")), like),
                        cb.like(cb.lower(root.<String>get("email")), like)));
            }
            if (roleFilter != null) {
                predicates.add(cb.equal(root.get("role").get("code"), roleFilter.name()));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return PageResponse.from(
                userRepository.findAll(specification,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdAt"))),
                UserDto::from);
    }

    @PostMapping
    @Operation(summary = "Provision a recruiter or administrator account")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role role = roleRepository.findByCode(request.role().name())
                .orElseThrow(() -> new IllegalStateException("Role missing: " + request.role()));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        log.info("ADMIN {} provisioned {} account {}", CurrentUser.require().email(), role.getCode(), email);
        return UserDto.from(saved);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or suspend an account")
    @Transactional
    public UserDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        if (user.getId().equals(CurrentUser.requireId()) && request.status() != UserStatus.ACTIVE) {
            throw new ConflictException("You cannot suspend your own account");
        }
        user.setStatus(request.status());
        User saved = userRepository.save(user);
        log.info("ADMIN {} set {} status to {}", CurrentUser.require().email(), saved.getEmail(), saved.getStatus());
        return UserDto.from(saved);
    }

    @GetMapping("/roles")
    @Operation(summary = "Assignable roles")
    public List<String> roles() {
        return roleRepository.findAll().stream().map(Role::getCode).sorted().toList();
    }
}
