package com.mett.hdr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.controller.AuthController;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.permission.repository.PermissionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserIdentityLinkRepository userIdentityLinkRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @BeforeEach
    void resetStores() {
        auditLogRepository.clear();
        refreshTokenRepository.clear();
        userIdentityLinkRepository.clear();
        userProfileRepository.clear();
        permissionRepository.clearAssignments();
        userRepository.clear();
    }

    @Test
    void registerLoginRefreshCurrentUserAndLogoutWorkTogether() throws Exception {
        String email = "identity-test@example.com";
        mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "phone": "",
                                  "password": "StrongPassword123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.globalUserId").exists())
                .andExpect(jsonPath("$.data.message").value("REGISTER_SUCCESS"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "%s",
                                  "password": "StrongPassword123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AuthController.REFRESH_TOKEN_COOKIE, true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn();

        String accessToken = read(loginResult, "/data/accessToken").asText();
        Cookie refreshCookie = loginResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshTokenRepository.findAll()).hasSize(1);

        mockMvc.perform(get("/api/v1/app/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.globalUserId").exists())
                .andExpect(jsonPath("$.data.identitySource").value("HDR"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/app/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AuthController.REFRESH_TOKEN_COOKIE, true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE);
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll().stream().filter(token -> token.getRevokedAt() != null)).hasSize(1);

        mockMvc.perform(post("/api/v1/app/auth/logout").cookie(rotatedRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("LOGOUT_SUCCESS"));

        mockMvc.perform(post("/api/v1/app/auth/refresh").cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.countByAction("REGISTER")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("LOGIN_SUCCESS")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("TOKEN_REFRESH")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("LOGOUT")).isEqualTo(1);
    }

    @Test
    void failedLoginWritesAuditLog() throws Exception {
        mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "missing@example.com",
                                  "password": "WrongPassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(auditLogRepository.countByAction("LOGIN_FAILED")).isEqualTo(1);
    }

    private JsonNode read(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer);
    }
}
