package com.mett.hdr.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.integration.entity.ExternalObjectLink;
import com.mett.hdr.integration.repository.ExternalObjectLinkRepository;
import com.mett.hdr.integration.service.ExternalObjectLinkService;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.service.MembershipProvisioningService;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.permission.repository.PermissionRepository;
import com.mett.hdr.project.repository.ProjectMemberRepository;
import com.mett.hdr.project.repository.ProjectRepository;
import com.mett.hdr.project.repository.ProjectSpaceRepository;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
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
class ProjectSpaceApiIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExternalObjectLinkService externalObjectLinkService;
    @Autowired
    private MembershipProvisioningService membershipProvisioningService;
    @Autowired
    private ExternalObjectLinkRepository externalObjectLinkRepository;
    @Autowired
    private ProjectSpaceRepository projectSpaceRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private MembershipRepository membershipRepository;
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
        clearAll();
    }

    @AfterEach
    void cleanup() {
        clearAll();
    }

    @Test
    void personalProjectLifecycleEnforcesVisibilityMembershipAndManagerTransfer()
            throws Exception {
        TestUser owner = registerAndLogin("project-owner@example.com");
        TestUser editor = registerAndLogin("project-editor@example.com");
        TestUser viewer = registerAndLogin("project-viewer@example.com");
        TestUser outsider = registerAndLogin("project-outsider@example.com");

        MvcResult created = createProject(owner, "Personal Project", "PERSONAL", null, null);
        String projectId = read(created, "/data/globalProjectId");
        assertThat(read(created, "/data/projectNumber")).startsWith("HDR-P-");
        assertThat(read(created, "/data/status")).isEqualTo("DRAFT");
        assertThat(read(created, "/data/currentUserRole")).isEqualTo("MANAGER");

        mockMvc.perform(get("/api/v1/app/projects/{id}", projectId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/app/projects")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        addProjectMember(owner, projectId, editor, "EDITOR", 200);
        addProjectMember(owner, projectId, viewer, "VIEWER", 200);
        mockMvc.perform(post("/api/v1/app/projects/{id}/members", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"EDITOR"}
                                """.formatted(editor.globalUserId())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/app/projects/{id}/activate", projectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(patch("/api/v1/app/projects/{id}", projectId)
                        .header("Authorization", bearer(editor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Shanghai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Shanghai"));
        mockMvc.perform(patch("/api/v1/app/projects/{id}", projectId)
                        .header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Beijing\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/app/projects/{id}/members/{user}",
                        projectId, owner.globalUserId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberRole\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/app/projects/{id}/manager-transfer", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newManagerGlobalUserId":"%s"}
                                """.formatted(editor.globalUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managerGlobalUserId").value(editor.globalUserId()));
        Long internalProjectId = projectRepository.findByGlobalId(projectId).orElseThrow().id();
        assertThat(projectMemberRepository.countActiveManagers(internalProjectId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/app/projects/{id}/members/{user}",
                        projectId, editor.globalUserId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/app/projects/{id}/members/{user}",
                        projectId, viewer.globalUserId())
                        .header("Authorization", bearer(editor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberRole\":\"EDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberRole").value("EDITOR"));
        mockMvc.perform(delete("/api/v1/app/projects/{id}/members/{user}",
                        projectId, viewer.globalUserId())
                        .header("Authorization", bearer(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        mockMvc.perform(post("/api/v1/app/projects/{id}/archive", projectId)
                        .header("Authorization", bearer(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(patch("/api/v1/app/projects/{id}", projectId)
                        .header("Authorization", bearer(editor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/app/projects/{id}/restore", projectId)
                        .header("Authorization", bearer(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(auditLogRepository.countByAction("PROJECT_CREATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_UPDATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_ACTIVATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_MANAGER_TRANSFER")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_MEMBER_UPDATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_MEMBER_LEAVE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_ARCHIVE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("PROJECT_RESTORE")).isEqualTo(1);
    }

    @Test
    void spacesDeriveGeometryAndProtectHierarchyLifecycle() throws Exception {
        TestUser owner = registerAndLogin("space-owner@example.com");
        String projectId = read(
                createProject(owner, "Space Project", "PERSONAL", null, null),
                "/data/globalProjectId");
        activate(owner, projectId);

        MvcResult floorResult = mockMvc.perform(post("/api/v1/app/projects/{id}/spaces", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Ground Floor",
                                  "spaceLevelType":"FLOOR",
                                  "geometryType":"UNSPECIFIED",
                                  "sortOrder":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String floorId = read(floorResult, "/data/globalSpaceId");

        MvcResult roomResult = mockMvc.perform(post("/api/v1/app/projects/{id}/spaces", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Living Room",
                                  "parentGlobalSpaceId":"%s",
                                  "spaceLevelType":"ROOM",
                                  "usageCode":"LIVING_ROOM",
                                  "geometryType":"RECTANGLE",
                                  "lengthM":5.2,
                                  "widthM":4.1,
                                  "heightM":2.8,
                                  "orientationCode":"S",
                                  "sortOrder":10
                                }
                                """.formatted(floorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.floorAreaM2").value(21.320))
                .andExpect(jsonPath("$.data.volumeM3").value(59.696))
                .andReturn();
        String roomId = read(roomResult, "/data/globalSpaceId");

        mockMvc.perform(patch("/api/v1/app/projects/{project}/spaces/{space}",
                        projectId, roomId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentGlobalSpaceId":"%s"}
                                """.formatted(roomId)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/app/projects/{project}/spaces/{space}",
                        projectId, roomId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentGlobalSpaceId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentGlobalSpaceId").isEmpty());
        mockMvc.perform(patch("/api/v1/app/projects/{project}/spaces/{space}",
                        projectId, roomId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentGlobalSpaceId":"%s"}
                                """.formatted(floorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentGlobalSpaceId").value(floorId));

        mockMvc.perform(post("/api/v1/app/projects/{id}/spaces", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalid","spaceLevelType":"ROOM",
                                 "geometryType":"RECTANGLE","lengthM":-1,"widthM":2}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/app/projects/{project}/spaces/{space}",
                        projectId, floorId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentGlobalSpaceId":"%s"}
                                """.formatted(roomId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/app/projects/{project}/spaces/{space}/archive",
                        projectId, floorId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());

        archiveSpace(owner, projectId, roomId, 200);
        archiveSpace(owner, projectId, floorId, 200);
        mockMvc.perform(post("/api/v1/app/projects/{project}/spaces/{space}/restore",
                        projectId, roomId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        restoreSpace(owner, projectId, floorId);
        restoreSpace(owner, projectId, roomId);

        mockMvc.perform(get("/api/v1/app/projects/{project}/spaces/{space}",
                        projectId, roomId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentGlobalSpaceId").value(floorId));
        assertThat(auditLogRepository.countByAction("PROJECT_SPACE_CREATE")).isEqualTo(2);
        assertThat(auditLogRepository.countByAction("PROJECT_SPACE_ARCHIVE")).isEqualTo(2);
        assertThat(auditLogRepository.countByAction("PROJECT_SPACE_RESTORE")).isEqualTo(2);

        mockMvc.perform(post("/api/v1/app/projects/{id}/archive", projectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/app/projects/{id}/spaces", projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Blocked","spaceLevelType":"ZONE",
                                 "geometryType":"UNSPECIFIED"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void teamAndOrganizationOwnershipUseEntitlementsAndExternalLinksStayInternal()
            throws Exception {
        TestUser owner = registerAndLogin("context-owner@example.com");
        TestUser member = registerAndLogin("context-member@example.com");
        registerAndLogin("context-outsider@example.com");
        permissionRepository.assignRole(
                userRepository.findByAccount("context-outsider@example.com").orElseThrow().getId(),
                "ADMIN");
        TestUser outsider = login("context-outsider@example.com");

        String teamId = createTeam(owner);
        addTeamMember(owner, teamId, member, "MEMBER");
        mockMvc.perform(post("/api/v1/app/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("No Plan", "TEAM", teamId, null)))
                .andExpect(status().isForbidden());
        Long teamInternalId = teamRepository.findByGlobalId(teamId).orElseThrow().id();
        Long ownerInternalId = internalUserId(owner);
        membershipProvisioningService.activateTeamPlan(
                teamInternalId, "TEAM_PLUS", 1, "MANUAL", ownerInternalId, null);

        MvcResult teamProjectResult = createProject(
                owner, "Team Project", "TEAM", teamId, null);
        String teamProjectId = read(teamProjectResult, "/data/globalProjectId");
        assertThat(read(teamProjectResult, "/data/ownershipScope")).isEqualTo("TEAM");
        mockMvc.perform(get("/api/v1/app/projects/{id}", teamProjectId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
        addProjectMember(owner, teamProjectId, member, "VIEWER", 200);
        mockMvc.perform(get("/api/v1/app/projects/{id}", teamProjectId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentUserRole").value("VIEWER"));
        addProjectMember(owner, teamProjectId, outsider, "VIEWER", 409);

        String organizationId = createOrganization(owner);
        addOrganizationMember(owner, organizationId, member);
        mockMvc.perform(post("/api/v1/app/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("Mismatched", "TEAM", teamId, organizationId)))
                .andExpect(status().isBadRequest());
        MvcResult organizationProject = createProject(
                member, "Organization Project", "ORGANIZATION", null, organizationId);
        assertThat(read(organizationProject, "/data/ownershipScope")).isEqualTo("ORGANIZATION");
        String organizationProjectId = read(organizationProject, "/data/globalProjectId");
        mockMvc.perform(get("/api/v1/app/projects/{id}", organizationProjectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/app/projects/{id}", organizationProjectId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());

        ExternalObjectLink link = externalObjectLinkService.createLink(
                ownerInternalId,
                teamProjectId,
                "METT",
                "EVALUATION_PROJECT",
                "external-42",
                "mett-project-42",
                "REFERENCE",
                null
        );
        assertThat(externalObjectLinkService.findByLocalGlobalId(teamProjectId))
                .extracting(ExternalObjectLink::id).containsExactly(link.id());
        assertThat(externalObjectLinkService.findByExternalGlobalId("METT", "mett-project-42"))
                .hasSize(1);
        assertThatThrownBy(() -> externalObjectLinkService.createLink(
                ownerInternalId,
                teamProjectId,
                "METT",
                "EVALUATION_PROJECT",
                "external-42",
                "mett-project-42",
                "REFERENCE",
                null
        )).isInstanceOf(ConflictException.class);
        assertThat(externalObjectLinkService.deactivateLink(ownerInternalId, link.id(), null).status())
                .isEqualTo("INACTIVE");
        mockMvc.perform(get("/api/v1/app/external-object-links")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        assertThat(auditLogRepository.countByAction("EXTERNAL_OBJECT_LINK_CREATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("EXTERNAL_OBJECT_LINK_DEACTIVATE")).isEqualTo(1);
    }

    @Test
    void missingPersonalMembershipAccessRejectsProjectCreation() throws Exception {
        TestUser user = registerAndLogin("project-no-membership@example.com");
        membershipRepository.clearAllMembershipData();

        mockMvc.perform(post("/api/v1/app/projects")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("No Access", "PERSONAL", null, null)))
                .andExpect(status().isForbidden());
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

    private TestUser login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(
                userRepository.findByAccount(email).orElseThrow().getGlobalUserId(),
                read(login, "/data/accessToken")
        );
    }

    private MvcResult createProject(
            TestUser actor,
            String name,
            String scope,
            String teamId,
            String organizationId
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/app/projects")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody(name, scope, teamId, organizationId)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String projectBody(
            String name,
            String scope,
            String teamId,
            String organizationId
    ) {
        return """
                {
                  "name":"%s",
                  "projectType":"RESIDENTIAL",
                  "ownershipScope":"%s",
                  "globalTeamId":%s,
                  "globalOrganizationId":%s,
                  "city":"Shanghai",
                  "timezone":"Asia/Shanghai"
                }
                """.formatted(name, scope, json(teamId), json(organizationId));
    }

    private void activate(TestUser actor, String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/app/projects/{id}/activate", projectId)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk());
    }

    private void addProjectMember(
            TestUser actor,
            String projectId,
            TestUser target,
            String role,
            int expectedStatus
    ) throws Exception {
        mockMvc.perform(post("/api/v1/app/projects/{id}/members", projectId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"%s"}
                                """.formatted(target.globalUserId(), role)))
                .andExpect(status().is(expectedStatus));
    }

    private String createTeam(TestUser owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project Team\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/data/globalTeamId");
    }

    private void addTeamMember(TestUser owner, String teamId, TestUser member, String role)
            throws Exception {
        mockMvc.perform(post("/api/v1/app/teams/{id}/members", teamId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"%s"}
                                """.formatted(member.globalUserId(), role)))
                .andExpect(status().isOk());
    }

    private String createOrganization(TestUser owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/organizations")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Project Organization","organizationType":"OTHER"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/data/globalOrganizationId");
    }

    private void addOrganizationMember(TestUser owner, String organizationId, TestUser member)
            throws Exception {
        mockMvc.perform(post("/api/v1/app/organizations/{id}/members", organizationId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"MEMBER"}
                                """.formatted(member.globalUserId())))
                .andExpect(status().isOk());
    }

    private void archiveSpace(TestUser owner, String projectId, String spaceId, int expected)
            throws Exception {
        mockMvc.perform(post("/api/v1/app/projects/{project}/spaces/{space}/archive",
                        projectId, spaceId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().is(expected));
    }

    private void restoreSpace(TestUser owner, String projectId, String spaceId) throws Exception {
        mockMvc.perform(post("/api/v1/app/projects/{project}/spaces/{space}/restore",
                        projectId, spaceId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
    }

    private Long internalUserId(TestUser user) {
        return userRepository.findByGlobalUserId(user.globalUserId()).orElseThrow().getId();
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.accessToken();
    }

    private String read(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asText();
    }

    private String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private void clearAll() {
        externalObjectLinkRepository.clear();
        projectSpaceRepository.clear();
        projectMemberRepository.clear();
        projectRepository.clear();
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

    private record TestUser(String globalUserId, String accessToken) {
    }
}
