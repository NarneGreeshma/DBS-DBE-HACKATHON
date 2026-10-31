package com.smarthire.application;

import com.smarthire.application.dto.ApplicationDtos.AnalysisDetail;
import com.smarthire.application.dto.ApplicationDtos.ApplicationResponse;
import com.smarthire.application.dto.ApplicationDtos.EventResponse;
import com.smarthire.application.dto.ApplicationDtos.StageRequest;
import com.smarthire.common.PageResponse;
import com.smarthire.enums.Enums.ApplicationSource;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Applications", description = "The hiring pipeline")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationRepository applicationRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Apply to a job, optionally attaching a fresh resume")
    public ResponseEntity<ApplicationResponse> apply(
            @RequestParam Long jobId,
            @RequestParam(required = false) String coverLetter,
            @RequestParam(required = false) ApplicationSource source,
            @RequestPart(value = "resume", required = false) MultipartFile resume) throws Exception {
        ApplicationResponse response = applicationService.apply(
                jobId, coverLetter, source, resume, CurrentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Own applications")
    public List<ApplicationResponse> mine() {
        return applicationService.mine(CurrentUser.requireId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Search the pipeline (recruiters/admins)")
    public PageResponse<ApplicationResponse> search(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) ApplicationStage stage,
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean sortByScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationService.search(
                new ApplicationService.ApplicationSearch(jobId, stage, candidateId, q, sortByScore),
                page, size, CurrentUser.require());
    }

    @GetMapping("/stage-counts")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Application counts per pipeline stage")
    public Map<String, Long> stageCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ApplicationStage stage : ApplicationStage.values()) {
            counts.put(stage.name(), applicationRepository.countByStage(stage));
        }
        return counts;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail (owner candidate or staff)")
    public ApplicationResponse get(@PathVariable Long id) {
        return applicationService.get(id, CurrentUser.require());
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Stage-change audit trail")
    public List<EventResponse> timeline(@PathVariable Long id) {
        return applicationService.timeline(id, CurrentUser.require());
    }

    @GetMapping("/{id}/analysis")
    @Operation(summary = "AI resume-screening history for an application")
    public List<AnalysisDetail> analysis(@PathVariable Long id) {
        return applicationService.analysisHistory(id, CurrentUser.require());
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Move an application through the pipeline")
    public ApplicationResponse changeStage(@PathVariable Long id, @Valid @RequestBody StageRequest request) {
        return applicationService.changeStage(id, request, CurrentUser.require());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Withdraw own application")
    public ApplicationResponse withdraw(@PathVariable Long id) {
        return applicationService.withdraw(id, CurrentUser.require());
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Operation(summary = "Force a fresh AI resume screening")
    public AnalysisDetail reanalyze(@PathVariable Long id) {
        return AnalysisDetail.from(applicationService.reanalyze(id, CurrentUser.require()));
    }
}
