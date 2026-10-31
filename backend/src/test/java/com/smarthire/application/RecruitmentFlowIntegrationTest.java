package com.smarthire.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end recruitment flow: public board -> apply -> AI screening -> pipeline ->
 * interview -> analytics. Runs on real PostgreSQL; every test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecruitmentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --------------------------------------------------------------- helpers

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        String payload = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload.isEmpty() ? "{}" : payload);
    }

    private String login(String email) throws Exception {
        JsonNode node = postJson("/api/auth/login", null,
                Map.of("email", email, "password", "Password123!"));
        return node.path("accessToken").asText(null);
    }

    private String registerCandidate() throws Exception {
        JsonNode node = postJson("/api/auth/register", null, Map.of(
                "fullName", "Flow Candidate",
                "email", "flow." + UUID.randomUUID() + "@example.com",
                "password", "Password123!"));
        return node.path("accessToken").asText(null);
    }

    private long applyToJob(String token, long jobId) throws Exception {
        MockMultipartFile resume = new MockMultipartFile(
                "resume", "resume.txt", "text/plain",
                "Skills: Java, Spring Boot, PostgreSQL, Docker, 6 years experience"
                        .getBytes(StandardCharsets.UTF_8));

        String payload = mockMvc.perform(multipart("/api/applications")
                        .file(resume)
                        .param("jobId", String.valueOf(jobId))
                        .param("coverLetter", "I would love to work here")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload).path("id").asLong();
    }

    // --------------------------------------------------------------- job board

    @Test
    @DisplayName("the anonymous board only exposes OPEN postings; staff see everything")
    void publicBoardHidesNonOpenJobs() throws Exception {
        JsonNode anon = objectMapper.readTree(
                mockMvc.perform(get("/api/jobs")).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertThat(anon.path("content").size()).isGreaterThan(0);
        anon.path("content").forEach(job -> assertThat(job.path("status").asText()).isEqualTo("OPEN"));

        String recruiter = login("recruiter@smarthire.io");
        JsonNode staff = objectMapper.readTree(
                mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + recruiter))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertThat(staff.path("content").size()).isGreaterThan(anon.path("content").size());
    }

    @Test
    @DisplayName("a recruiter creates a draft, publishes it, and it becomes public")
    void recruiterPublishesJob() throws Exception {
        String recruiter = login("recruiter@smarthire.io");

        JsonNode created = postJson("/api/jobs", recruiter, Map.of(
                "title", "Platform Reliability Engineer",
                "description", "Keep the hiring platform fast and boring.",
                "location", "Remote",
                "workplaceType", "REMOTE",
                "experienceLevel", "SENIOR",
                "skills", java.util.List.of(
                        Map.of("name", "docker", "weight", 3, "required", true),
                        Map.of("name", "kubernetes", "weight", 2, "required", false))));
        assertThat(created.path("id").asLong()).isPositive();
        assertThat(created.path("slug").asText()).isEqualTo("platform-reliability-engineer");
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");

        long jobId = created.path("id").asLong();

        mockMvc.perform(patch("/api/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // now visible without authentication
        mockMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("a job cannot be published before it has skills attached")
    void publishRequiresSkills() throws Exception {
        String recruiter = login("recruiter@smarthire.io");
        JsonNode created = postJson("/api/jobs", recruiter, Map.of(
                "title", "Unskilled Role", "description", "No skills yet"));
        long jobId = created.path("id").asLong();

        mockMvc.perform(patch("/api/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("illegal job status transitions are rejected")
    void illegalJobTransitionRejected() throws Exception {
        String recruiter = login("recruiter@smarthire.io");
        // job 6 is seeded CLOSED and a closed job can never reopen
        mockMvc.perform(patch("/api/jobs/6/status")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- applications

    @Test
    @DisplayName("a candidate applies once, and a second attempt is a conflict")
    void applyOnceOnly() throws Exception {
        String candidate = registerCandidate();
        long applicationId = applyToJob(candidate, 1);
        assertThat(applicationId).isPositive();

        mockMvc.perform(multipart("/api/applications")
                        .param("jobId", "1")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("closed and draft postings refuse new applications")
    void closedJobsRefuseApplications() throws Exception {
        String candidate = registerCandidate();

        mockMvc.perform(multipart("/api/applications")
                        .param("jobId", "6")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isConflict());

        mockMvc.perform(multipart("/api/applications")
                        .param("jobId", "5")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("applying records an analysis even though the AI service is disabled")
    void analysisIsRecordedWhenAiDisabled() throws Exception {
        String candidate = registerCandidate();
        long applicationId = applyToJob(candidate, 1);

        String payload = mockMvc.perform(get("/api/applications/" + applicationId + "/analysis")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode history = objectMapper.readTree(payload);
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).path("status").asText()).isEqualTo("FAILED");
        assertThat(history.get(0).path("error").asText()).contains("disabled");
        // graceful degradation: the application itself still exists
        mockMvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("APPLIED"));
    }

    @Test
    @DisplayName("moving a stage writes the audit trail and notifies the candidate")
    void stageChangeIsAuditedAndNotifies() throws Exception {
        String candidate = registerCandidate();
        String recruiter = login("recruiter@smarthire.io");
        long applicationId = applyToJob(candidate, 1);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/stage")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"SCREENING\",\"note\":\"Strong resume\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SCREENING"));

        mockMvc.perform(get("/api/applications/" + applicationId + "/timeline")
                        .header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].toStage").value("SCREENING"))
                .andExpect(jsonPath("$[1].note").value("Strong resume"));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STAGE"));
    }

    @Test
    @DisplayName("the pipeline rejects moves that cannot happen in real life")
    void illegalStageTransitionRejected() throws Exception {
        String candidate = registerCandidate();
        String recruiter = login("recruiter@smarthire.io");
        long applicationId = applyToJob(candidate, 1);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/stage")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"HIRED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a candidate can read and withdraw only their own application")
    void candidateOwnsTheirApplication() throws Exception {
        String candidate = registerCandidate();
        String other = registerCandidate();
        String recruiter = login("recruiter@smarthire.io");
        long applicationId = applyToJob(candidate, 1);

        mockMvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/applications/" + applicationId + "/stage")
                        .header("Authorization", "Bearer " + candidate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"HIRED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/applications/" + applicationId + "/withdraw")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("WITHDRAWN"));

        // once withdrawn, the pipeline is closed for that application
        mockMvc.perform(patch("/api/applications/" + applicationId + "/stage")
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"OFFER\"}"))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------------- interviews

    @Test
    @DisplayName("scheduling an interview advances the pipeline and notifies the candidate")
    void interviewSchedulingAdvancesPipeline() throws Exception {
        String candidate = registerCandidate();
        String recruiter = login("recruiter@smarthire.io");
        long applicationId = applyToJob(candidate, 1);

        JsonNode interview = postJson("/api/interviews", recruiter, Map.of(
                "applicationId", applicationId,
                "round", 1,
                "mode", "ONLINE",
                "scheduledAt", "2030-03-01T10:00:00Z",
                "durationMinutes", 60));
        assertThat(interview.path("id").asLong()).isPositive();
        assertThat(interview.path("status").asText()).isEqualTo("SCHEDULED");

        mockMvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("INTERVIEW"));

        mockMvc.perform(get("/api/interviews").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobTitle").exists());

        // completing it twice is a conflict
        mockMvc.perform(patch("/api/interviews/" + interview.path("id").asLong())
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"feedback\":\"Strong signal\",\"rating\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4));

        mockMvc.perform(patch("/api/interviews/" + interview.path("id").asLong())
                        .header("Authorization", "Bearer " + recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------------- analytics

    @Test
    @DisplayName("dashboard aggregates reflect the seeded dataset")
    void analyticsAggregatesSeedData() throws Exception {
        String recruiter = login("recruiter@smarthire.io");

        String payload = mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode summary = objectMapper.readTree(payload);

        assertThat(summary.path("totalJobs").asLong()).isEqualTo(6);
        assertThat(summary.path("openJobs").asLong()).isEqualTo(4);
        assertThat(summary.path("totalApplications").asLong()).isEqualTo(5);
        assertThat(summary.path("hired").asLong()).isZero();
        assertThat(summary.path("averageScore").isNumber()).isTrue();

        mockMvc.perform(get("/api/analytics/funnel").header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        mockMvc.perform(get("/api/analytics/skills").header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobCount").isNumber());

        mockMvc.perform(get("/api/applications/stage-counts")
                        .header("Authorization", "Bearer " + recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.APPLIED").value(2));
    }

    @Test
    @DisplayName("candidates get their own application list and notifications")
    void candidateSeesOwnData() throws Exception {
        String candidate = registerCandidate();
        applyToJob(candidate, 1);

        mockMvc.perform(get("/api/applications/mine").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].job.title").value("Senior Backend Engineer"));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPLICATION"));

        mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResume").value(true));
    }
}
