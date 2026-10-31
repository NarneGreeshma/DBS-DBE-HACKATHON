package com.smarthire.skill;

import com.smarthire.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Tag(name = "Skills", description = "Curated skill taxonomy used for matching")
public class SkillController {

    private final SkillService skillService;

    public record SkillResponse(Long id, String name, String category) {
        public static SkillResponse from(Skill skill) {
            return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory());
        }
    }

    public record CreateSkillRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 60) String category) {
    }

    @GetMapping
    @Operation(summary = "List the skill taxonomy (searchable)")
    public List<SkillResponse> list(@RequestParam(required = false) String q) {
        return skillService.list(q).stream().map(SkillResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a skill to the taxonomy (ADMIN)")
    public ResponseEntity<SkillResponse> create(@Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SkillResponse.from(skillService.create(request.name(), request.category())));
    }
}
