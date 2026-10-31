package com.smarthire.candidate;

import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.RoleCode;
import com.smarthire.security.CurrentUser;
import com.smarthire.storage.FileStorageService;
import com.smarthire.storage.ResumeTextExtractor;
import com.smarthire.user.User;
import com.smarthire.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidates", description = "Candidate profiles and resume upload")
public class CandidateController {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;

    public record CandidateResponse(
            Long userId,
            String fullName,
            String email,
            String phone,
            String headline,
            String summary,
            Integer experienceYears,
            String location,
            String linkedinUrl,
            String portfolioUrl,
            String resumeFileName,
            boolean hasResume) {

        public static CandidateResponse of(User user, CandidateProfile profile) {
            return new CandidateResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPhone(),
                    profile == null ? null : profile.getHeadline(),
                    profile == null ? null : profile.getSummary(),
                    profile == null ? 0 : profile.getExperienceYears(),
                    profile == null ? null : profile.getLocation(),
                    profile == null ? null : profile.getLinkedinUrl(),
                    profile == null ? null : profile.getPortfolioUrl(),
                    profile == null ? null : profile.getResumeOriginalName(),
                    profile != null && profile.hasResume());
        }
    }

    public record UpdateProfileRequest(
            @Size(max = 120) String fullName,
            @Size(max = 32) String phone,
            @Size(max = 160) String headline,
            @Size(max = 4000) String summary,
            @Min(0) @Max(50) Integer experienceYears,
            @Size(max = 120) String location,
            @Size(max = 255) String linkedinUrl,
            @Size(max = 255) String portfolioUrl) {
    }

    @GetMapping("/me")
    @Operation(summary = "Own candidate profile")
    public CandidateResponse me() {
        User user = requireUser(CurrentUser.requireId());
        return CandidateResponse.of(user, candidateProfileRepository.findByUserId(user.getId()).orElse(null));
    }

    @PutMapping("/me")
    @Operation(summary = "Update own candidate profile")
    @Transactional
    public CandidateResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        User user = requireUser(CurrentUser.requireId());
        CandidateProfile profile = candidateProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> newProfile(user.getId()));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
            userRepository.save(user);
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
            userRepository.save(user);
        }
        if (request.headline() != null) profile.setHeadline(blankToNull(request.headline()));
        if (request.summary() != null) profile.setSummary(blankToNull(request.summary()));
        if (request.experienceYears() != null) profile.setExperienceYears(request.experienceYears());
        if (request.location() != null) profile.setLocation(blankToNull(request.location()));
        if (request.linkedinUrl() != null) profile.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        if (request.portfolioUrl() != null) profile.setPortfolioUrl(blankToNull(request.portfolioUrl()));

        CandidateProfile saved = candidateProfileRepository.save(profile);
        return CandidateResponse.of(user, saved);
    }

    @PostMapping(value = "/me/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload resume (PDF/DOCX/TXT); text is extracted for AI screening")
    @Transactional
    public CandidateResponse uploadResume(@RequestPart("file") MultipartFile file) throws Exception {
        User user = requireUser(CurrentUser.requireId());
        CandidateProfile profile = candidateProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> newProfile(user.getId()));

        String text = resumeTextExtractor.extract(file.getBytes(), file.getOriginalFilename());
        String storedPath = fileStorageService.store(file, "resume-" + user.getId());

        if (profile.hasResume()) {
            fileStorageService.deleteIfExists(profile.getResumeFilePath());
        }
        profile.setResumeFilePath(storedPath);
        profile.setResumeOriginalName(file.getOriginalFilename());
        profile.setResumeContentType(file.getContentType());
        profile.setResumeText(text);

        CandidateProfile saved = candidateProfileRepository.save(profile);
        log.info("Stored resume for candidate {}: {} chars extracted", user.getId(), text.length());
        return CandidateResponse.of(user, saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Browse candidates (recruiters/admins)")
    public List<CandidateResponse> list(@RequestParam(required = false) String q) {
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return userRepository.findAllCandidates().stream()
                .filter(u -> needle == null
                        || u.getFullName().toLowerCase(Locale.ROOT).contains(needle)
                        || u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                .map(u -> CandidateResponse.of(u,
                        candidateProfileRepository.findByUserId(u.getId()).orElse(null)))
                .toList();
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Candidate detail (recruiters/admins)")
    public CandidateResponse get(@PathVariable Long userId) {
        User user = requireUser(userId);
        if (user.getRole().getCode().equals(RoleCode.CANDIDATE.name())) {
            return CandidateResponse.of(user,
                    candidateProfileRepository.findByUserId(userId).orElse(null));
        }
        return CandidateResponse.of(user, null);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private CandidateProfile newProfile(Long userId) {
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(userId);
        profile.setExperienceYears(0);
        return profile;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
