package com.smarthire.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication + role-based access control, exercised against the real
 * PostgreSQL test database (profile {@code test} -> {@code smarthire_test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthAndRbacIntegrationTest {

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
        String payload = mockMvc.perform(request)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload.isEmpty() ? "{}" : payload);
    }

    private String login(String email, String password) throws Exception {
        JsonNode node = postJson("/api/auth/login", null,
                Map.of("email", email, "password", password));
        return node.path("accessToken").asText(null);
    }

    private String registerFreshCandidate() throws Exception {
        String email = "it.candidate." + UUID.randomUUID() + "@example.com";
        JsonNode node = postJson("/api/auth/register", null, Map.of(
                "fullName", "Integration Tester",
                "email", email,
                "password", "Password123!"));
        String token = node.path("accessToken").asText(null);
        assertThat(token).as("registration must return an access token").isNotBlank();
        return token;
    }

    // --------------------------------------------------------------- auth

    @Test
    @DisplayName("self-registration creates a CANDIDATE and issues a token pair")
    void registerCreatesCandidate() throws Exception {
        JsonNode node = postJson("/api/auth/register", null, Map.of(
                "fullName", "New Candidate",
                "email", "new.candidate." + UUID.randomUUID() + "@example.com",
                "password", "Password123!"));

        assertThat(node.path("accessToken").asText()).isNotBlank();
        assertThat(node.path("refreshToken").asText()).isNotBlank();
        assertThat(node.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(node.path("user").path("role").asText()).isEqualTo("CANDIDATE");
    }

    @Test
    @DisplayName("registering an existing email is a 409 conflict")
    void duplicateRegistrationConflicts() throws Exception {
        String email = "dupe." + UUID.randomUUID() + "@example.com";
        postJson("/api/auth/register", null,
                Map.of("fullName", "A", "email", email, "password", "Password123!"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("fullName", "B", "email", email, "password", "Password123!"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("weak passwords and malformed emails are rejected by bean validation")
    void validationRejectsBadInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("wrong password returns 401 with a stable message")
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "recruiter@smarthire.io", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("/api/auth/me without a token is 401, not 500")
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is 401")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token resolves the caller's identity and role")
    void meReturnsProfile() throws Exception {
        String token = login("recruiter@smarthire.io", "Password123!");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("recruiter@smarthire.io"))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    // --------------------------------------------------------------- RBAC

    @Test
    @DisplayName("anonymous callers can reach the public board but nothing else")
    void anonymousBoardIsPublic() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/jobs/1")).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/skills")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("candidates are barred from recruiter and admin surfaces")
    void candidateIsLeastPrivileged() throws Exception {
        String token = registerFreshCandidate();

        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":\"y\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"x\",\"email\":\"x@e.com\","
                                + "\"password\":\"Password123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("recruiters can run the pipeline but cannot administer users")
    void recruiterCannotAdminister() throws Exception {
        String token = login("recruiter@smarthire.io", "Password123!");

        mockMvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/jobs/5").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only an admin may delete jobs and list accounts")
    void adminHasFullPrivileges() throws Exception {
        String token = login("admin@smarthire.io", "Password123!");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/users/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItems("ADMIN", "RECRUITER", "CANDIDATE")));
    }

    @Test
    @DisplayName("an admin can suspend an account and that account can no longer log in")
    void suspendedAccountCannotLogin() throws Exception {
        String admin = login("admin@smarthire.io", "Password123!");
        String email = "to.suspend." + UUID.randomUUID() + "@example.com";

        JsonNode created = postJson("/api/admin/users", admin, Map.of(
                "fullName", "Temp Recruiter", "email", email,
                "password", "Password123!", "role", "RECRUITER"));
        long userId = created.path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password123!"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delete(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path);
    }
}
