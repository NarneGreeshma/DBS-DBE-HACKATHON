package com.smarthire.job;

import com.smarthire.common.PageResponse;
import com.smarthire.enums.Enums.JobStatus;
import com.smarthire.job.dto.JobDtos.JobRequest;
import com.smarthire.job.dto.JobDtos.JobResponse;
import com.smarthire.security.AuthenticatedUser;
import com.smarthire.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job postings: public board + recruiter management")
public class JobController {

    private final JobService jobService;

    public record StatusRequest(@NotBlank String status) {
    }

    @GetMapping
    @Operation(summary = "Browse jobs. Anonymous callers only ever see OPEN, unexpired postings.")
    public PageResponse<JobResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) com.smarthire.enums.Enums.ExperienceLevel level,
            @RequestParam(required = false) com.smarthire.enums.Enums.WorkplaceType workplace,
            @RequestParam(required = false) List<Long> skillIds,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "12") int size) {

        AuthenticatedUser user = CurrentUser.optional();
        boolean staff = user != null && user.isStaff();

        JobService.JobSearch search = new JobService.JobSearch(
                q,
                location,
                staff ? status : null,
                level,
                workplace,
                skillIds == null || skillIds.isEmpty() ? null : skillIds,
                staff && mine && !user.isAdmin() ? user.id() : null,
                !staff);

        return jobService.search(search, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Job detail by id")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Job detail by slug")
    public JobResponse getBySlug(@PathVariable String slug) {
        return jobService.getBySlug(slug);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Create a job draft (recruiters/admins)")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.create(request, CurrentUser.require()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Update a job the caller owns (admins may update any)")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return jobService.update(id, request, CurrentUser.require());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Transition a job between DRAFT / OPEN / ON_HOLD / CLOSED")
    public JobResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        JobStatus target = JobStatus.valueOf(request.status().trim().toUpperCase(java.util.Locale.ROOT));
        return jobService.changeStatus(id, target, CurrentUser.require());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a job (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobService.delete(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }
}
