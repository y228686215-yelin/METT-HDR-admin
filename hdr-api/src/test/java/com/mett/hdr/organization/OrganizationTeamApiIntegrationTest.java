package com.mett.hdr.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationTeamApiIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
        auditLogRepository.clear();
        refreshTokenRepository.clear();
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
    void organizationLifecycleEnforcesContextualRolesAndHistory() throws Exception {
        TestUser owner = registerAndLogin("org-owner@example.com");
        TestUser administrator = registerAndLogin("org-admin@example.com");
        TestUser member = registerAndLogin("org-member@example.com");
        TestUser outsider = registerAndLogin("org-outsider@example.com");

        MvcResult create = mockMvc.perform(post("/api/v1/app/organizations")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example Design Organization",
                                 "organizationType":"DESIGN_ORGANIZATION",
                                 "description":"Integration test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("OWNER"))
                .andExpect(jsonPath("$.data.ownerGlobalUserId").value(owner.globalUserId()))
                .andReturn();
        String organizationId = read(create, "/data/globalOrganizationId");

        mockMvc.perform(get("/api/v1/app/organizations").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].globalOrganizationId").value(organizationId));
        mockMvc.perform(get("/api/v1/app/organizations/" + organizationId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());

        addOrganizationMember(owner, organizationId, administrator, "ADMIN", 200);
        addOrganizationMember(owner, organizationId, member, "MEMBER", 200);
        addOrganizationMember(member, organizationId, outsider, "MEMBER", 403);

        mockMvc.perform(patch("/api/v1/app/organizations/{id}/members/{user}",
                        organizationId, owner.globalUserId())
                        .header("Authorization", bearer(administrator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/app/organizations/{id}/members/{user}",
                        organizationId, member.globalUserId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberRole\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberRole").value("ADMIN"));

        mockMvc.perform(delete("/api/v1/app/organizations/{id}/members/{user}",
                        organizationId, owner.globalUserId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/app/organizations/{id}/ownership-transfer", organizationId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOwnerGlobalUserId":"%s"}
                                """.formatted(administrator.globalUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerGlobalUserId").value(administrator.globalUserId()));

        mockMvc.perform(delete("/api/v1/app/organizations/{id}/members/{user}",
                        organizationId, member.globalUserId())
                        .header("Authorization", bearer(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        Long organizationInternalId = organizationRepository.findByGlobalId(organizationId).orElseThrow().id();
        assertThat(organizationMemberRepository.find(
                organizationInternalId, userRepository.findByGlobalUserId(member.globalUserId()).orElseThrow().getId()))
                .get().extracting(value -> value.status()).isEqualTo("LEFT");
        assertThat(organizationMemberRepository.countActiveOwners(organizationInternalId)).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("ORGANIZATION_CREATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("ORGANIZATION_MEMBER_ADD")).isEqualTo(2);
        assertThat(auditLogRepository.countByAction("ORGANIZATION_OWNER_TRANSFER")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("ORGANIZATION_MEMBER_LEAVE")).isEqualTo(1);
    }

    @Test
    void teamLifecycleEnforcesOrganizationBoundaryAndManagerTransfer() throws Exception {
        TestUser owner = registerAndLogin("team-owner@example.com");
        TestUser member = registerAndLogin("team-member@example.com");
        TestUser outsider = registerAndLogin("team-outsider@example.com");

        String organizationId = createOrganization(owner);
        addOrganizationMember(owner, organizationId, member, "MEMBER", 200);

        mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Unauthorized Org Team","globalOrganizationId":"%s"}
                                """.formatted(organizationId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standalone Team\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.globalOrganizationId").isEmpty());

        MvcResult createTeam = mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Organization Team",
                                 "description":"Persistent ownership",
                                 "globalOrganizationId":"%s"}
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("LEAD"))
                .andExpect(jsonPath("$.data.managerGlobalUserId").value(owner.globalUserId()))
                .andReturn();
        String teamId = read(createTeam, "/data/globalTeamId");

        mockMvc.perform(get("/api/v1/app/teams").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/app/teams/" + teamId).header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());

        addTeamMember(owner, teamId, member, "MEMBER", 200);
        addTeamMember(owner, teamId, outsider, "MEMBER", 403);
        addTeamMember(member, teamId, outsider, "MEMBER", 403);

        mockMvc.perform(delete("/api/v1/app/teams/{id}/members/{user}", teamId, owner.globalUserId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/app/teams/{id}/members/{user}", teamId, member.globalUserId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberRole\":\"LEAD\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/app/teams/{id}/manager-transfer", teamId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newManagerGlobalUserId":"%s"}
                                """.formatted(member.globalUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managerGlobalUserId").value(member.globalUserId()));

        mockMvc.perform(delete("/api/v1/app/teams/{id}/members/{user}", teamId, owner.globalUserId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        Long teamInternalId = teamRepository.findByGlobalId(teamId).orElseThrow().id();
        assertThat(teamMemberRepository.find(
                teamInternalId, userRepository.findByGlobalUserId(owner.globalUserId()).orElseThrow().getId()))
                .get().extracting(value -> value.status()).isEqualTo("LEFT");
        assertThat(auditLogRepository.countByAction("TEAM_CREATE")).isEqualTo(2);
        assertThat(auditLogRepository.countByAction("TEAM_MEMBER_ADD")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("TEAM_MANAGER_TRANSFER")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("TEAM_MEMBER_LEAVE")).isEqualTo(1);
    }

    private TestUser registerAndLogin(String email) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","phone":"","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult login = mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(
                read(registration, "/data/globalUserId"),
                read(login, "/data/accessToken")
        );
    }

    private String createOrganization(TestUser owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/organizations")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team Parent","organizationType":"OTHER"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/data/globalOrganizationId");
    }

    private void addOrganizationMember(
            TestUser actor, String organizationId, TestUser target, String role, int expectedStatus
    ) throws Exception {
        mockMvc.perform(post("/api/v1/app/organizations/{id}/members", organizationId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"%s"}
                                """.formatted(target.globalUserId(), role)))
                .andExpect(status().is(expectedStatus));
    }

    private void addTeamMember(
            TestUser actor, String teamId, TestUser target, String role, int expectedStatus
    ) throws Exception {
        mockMvc.perform(post("/api/v1/app/teams/{id}/members", teamId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"%s"}
                                """.formatted(target.globalUserId(), role)))
                .andExpect(status().is(expectedStatus));
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.accessToken();
    }

    private String read(MvcResult result, String pointer) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer);
        return node.asText();
    }

    private record TestUser(String globalUserId, String accessToken) {
    }
}
