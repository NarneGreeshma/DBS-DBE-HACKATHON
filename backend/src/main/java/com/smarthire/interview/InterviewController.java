package com.smarthire.interview;

import com.smarthire.enums.Enums.InterviewMode;
import com.smarthire.enums.Enums.InterviewStatus;
import com.smarthire.interview.InterviewService.InterviewRequest;
import com.smarthire.interview.InterviewService.InterviewUpdate;
import com.smarthire.interview.dto.InterviewResponse;
import com.smarthire.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews", description = "Interview scheduling and feedback")
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping
    @Operation(summary = "List interviews. Staff see all; candidates only their own.")
    public List<InterviewResponse> list(@RequestParam(required = false) InterviewStatus status,
                                        @RequestParam(defaultValue = "false") boolean upcoming) {
        return interviewService.list(status, upcoming, CurrentUser.require());
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Interviews attached to one application")
    public List<InterviewResponse> byApplication(@PathVariable Long applicationId) {
        return interviewService.byApplication(applicationId, CurrentUser.require());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Schedule an interview (recruiters/admins)")
    public ResponseEntity<InterviewResponse> schedule(@Valid @RequestBody InterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.schedule(request, CurrentUser.require()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Update status, feedback and rating")
    public InterviewResponse update(@PathVariable Long id, @RequestBody InterviewUpdate update) {
        return interviewService.update(id, update, CurrentUser.require());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Cancel/delete a not-yet-completed interview")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewService.delete(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }
}
