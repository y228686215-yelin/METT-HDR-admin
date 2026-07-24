package com.mett.hdr.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.service.MembershipProvisioningService;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.permission.repository.PermissionRepository;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipApiIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private MembershipPlanRepository planRepository;
    @Autowired
    private MembershipProvisioningService provisioningService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserIdentityLinkRepository userIdentityLinkRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("""
                DELETE FROM membership_plan_entitlements
                WHERE entitlement_definition_id IN (
                    SELECT id FROM entitlement_definitions
                    WHERE entitlement_type = 'QUOTA'
                )
                """);
        jdbcTemplate.update("""
                UPDATE membership_plans SET status = 'ACTIVE'
                WHERE plan_code IN (
                    'PERSONAL_FREE', 'PERSONAL_PLUS', 'PERSONAL_PRO',
                    'TEAM_PLUS', 'TEAM_PRO'
                )
                """);
        auditLogRepository.clear();
        refreshTokenRepository.clear();
        membershipRepository.clearAllMembershipData();
        teamMemberRepository.clear();
        teamRepository.clear();
        organizationMemberRepository.clear();
        organizationRepository.clear();
        userIdentityLinkRepository.clear();
        userProfileRepository.clear();
        permissionRepository.clearAssignments();
        userRepository.clear();
    }

    @Test
    void registrationIsTransactionalAndPersonalMembershipApisAreReadOnly() throws Exception {
        TestUser user = registerAndLogin("membership-user@example.com", false);
        Long userId = userRepository.findByGlobalUserId(user.globalUserId()).orElseThrow().getId();

        assertThat(membershipRepository.countCurrent(MembershipSubject.user(userId))).isEqualTo(1);
        assertThat(planRepository.findById(
                membershipRepository.findCurrentForUser(userId).orElseThrow().membershipPlanId())
                .orElseThrow().planCode()).isEqualTo("PERSONAL_FREE");
        assertThat(auditLogRepository.countByAction("MEMBERSHIP_DEFAULT_PROVISION")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("membership-user@example.com")))
                .andExpect(status().isConflict());
        assertThat(membershipRepository.countCurrent(MembershipSubject.user(userId))).isEqualTo(1);

        MvcResult membershipResult = mockMvc.perform(get("/api/v1/app/memberships/me")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectType").value("USER"))
                .andExpect(jsonPath("$.data.planCode").value("PERSONAL_FREE"))
                .andExpect(jsonPath("$.data.planVersion").value(1))
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        assertThat(membershipResult.getResponse().getContentAsString())
                .doesNotContain("\"id\"");

        mockMvc.perform(get("/api/v1/app/memberships/me/entitlements")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("HDR_ACCESS"))
                .andExpect(jsonPath("$.data[0].type").value("BOOLEAN"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));

        mockMvc.perform(get("/api/v1/app/memberships/me/usage")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        jdbcTemplate.update("""
                UPDATE membership_plans SET status = 'DRAFT'
                WHERE plan_code = 'PERSONAL_FREE'
                """);
        try {
            mockMvc.perform(post("/api/v1/app/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registrationBody("rollback-user@example.com")))
                    .andExpect(status().isInternalServerError());
            assertThat(userRepository.findByAccount("rollback-user@example.com")).isEmpty();
        } finally {
            jdbcTemplate.update("""
                    UPDATE membership_plans SET status = 'ACTIVE'
                    WHERE plan_code = 'PERSONAL_FREE'
                    """);
        }
    }

    @Test
    void teamQueriesRequireContextualMembershipAndNoPlanIsAutoCreated() throws Exception {
        TestUser owner = registerAndLogin("membership-team-owner@example.com", false);
        TestUser member = registerAndLogin("membership-team-member@example.com", false);
        TestUser outsider = registerAndLogin("membership-team-outsider@example.com", false);
        TestUser platformAdmin = registerAndLogin("membership-platform-admin@example.com", true);

        MvcResult created = mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Membership Query Team\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String globalTeamId = read(created, "/data/globalTeamId");
        Long teamId = teamRepository.findByGlobalId(globalTeamId).orElseThrow().id();

        assertThat(membershipRepository.findCurrentForTeam(teamId)).isEmpty();
        mockMvc.perform(get("/api/v1/app/teams/{id}/membership", globalTeamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());

        provisioningService.activateTeamPlan(
                teamId, "TEAM_PLUS", 1, "MANUAL",
                userId(owner), null);
        assertThat(membershipRepository.countCurrent(MembershipSubject.team(teamId))).isEqualTo(1);

        mockMvc.perform(post("/api/v1/app/teams/{id}/members", globalTeamId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"MEMBER"}
                                """.formatted(member.globalUserId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/app/teams/{id}/membership", globalTeamId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectType").value("TEAM"))
                .andExpect(jsonPath("$.data.planCode").value("TEAM_PLUS"));
        mockMvc.perform(get("/api/v1/app/teams/{id}/membership/entitlements", globalTeamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("TEAM_WORKSPACE_ACCESS"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
        mockMvc.perform(get("/api/v1/app/teams/{id}/membership/usage", globalTeamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/app/teams/{id}/membership", globalTeamId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/app/teams/{id}/membership", globalTeamId)
                        .header("Authorization", bearer(platformAdmin)))
                .andExpect(status().isNotFound());
    }

    private TestUser registerAndLogin(String email, boolean admin) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(email)))
                .andExpect(status().isOk())
                .andReturn();
        String globalUserId = read(registration, "/data/globalUserId");
        if (admin) {
            permissionRepository.assignRole(
                    userRepository.findByGlobalUserId(globalUserId).orElseThrow().getId(),
                    "ADMIN");
        }
        MvcResult login = mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(globalUserId, read(login, "/data/accessToken"));
    }

    private String registrationBody(String email) {
        return """
                {"email":"%s","phone":"","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private Long userId(TestUser user) {
        return userRepository.findByGlobalUserId(user.globalUserId()).orElseThrow().getId();
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.accessToken();
    }

    private String read(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).at(pointer).asText();
    }

    private record TestUser(String globalUserId, String accessToken) {
    }
}
