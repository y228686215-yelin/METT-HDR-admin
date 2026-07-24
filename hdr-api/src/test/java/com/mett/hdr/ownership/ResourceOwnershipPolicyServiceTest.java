package com.mett.hdr.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.identity.entity.IdentitySource;
import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.entity.UserStatus;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.organization.entity.Organization;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.ownership.model.OwnershipAssignmentRequest;
import com.mett.hdr.ownership.model.OwnershipContext;
import com.mett.hdr.ownership.model.OwnershipScope;
import com.mett.hdr.ownership.model.ResourceOwnership;
import com.mett.hdr.ownership.service.ResourceOwnershipPolicyService;
import com.mett.hdr.permission.repository.PermissionRepository;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ResourceOwnershipPolicyServiceTest {

    @Autowired
    private ResourceOwnershipPolicyService policyService;
    @Autowired
    private GlobalIdService globalIdService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserIdentityLinkRepository userIdentityLinkRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;

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
    void resolvesPersonalOrganizationAndTeamOwnershipAndRejectsInvalidAssignments() {
        User owner = createUser("ownership-owner@example.com");
        User member = createUser("ownership-member@example.com");
        User outsider = createUser("ownership-outsider@example.com");
        Organization organization = organizationRepository.create(
                globalIdService.organizationId(), "Ownership Org", "OTHER", null, owner.getId());
        organizationMemberRepository.create(organization.id(), owner.getId(), "OWNER", owner.getId());
        organizationMemberRepository.create(organization.id(), member.getId(), "MEMBER", owner.getId());
        Team team = teamRepository.create(
                globalIdService.teamId(), organization.id(), "Ownership Team", null, owner.getId());
        teamMemberRepository.create(team.id(), owner.getId(), "LEAD", owner.getId());
        teamMemberRepository.create(team.id(), member.getId(), "MEMBER", owner.getId());

        ResourceOwnership personal = policyService.validateAssignment(
                new OwnershipContext(owner.getId()),
                new OwnershipAssignmentRequest(OwnershipScope.PERSONAL, null, null, null));
        assertThat(personal.ownerUserId()).isEqualTo(owner.getId());
        assertThat(personal.teamId()).isNull();
        assertThat(personal.organizationId()).isNull();
        assertThat(personal.createdByUserId()).isEqualTo(owner.getId());
        assertThat(personal.managedByUserId()).isEqualTo(owner.getId());

        ResourceOwnership organizationOwnership = policyService.validateAssignment(
                new OwnershipContext(owner.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.ORGANIZATION, null, organization.id(), member.getId()));
        assertThat(organizationOwnership.organizationId()).isEqualTo(organization.id());
        assertThat(organizationOwnership.managedByUserId()).isEqualTo(member.getId());

        ResourceOwnership teamOwnership = policyService.validateAssignment(
                new OwnershipContext(owner.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.TEAM, team.id(), organization.id(), member.getId()));
        assertThat(teamOwnership.teamId()).isEqualTo(team.id());
        assertThat(teamOwnership.organizationId()).isEqualTo(organization.id());
        assertThat(policyService.canManage(owner.getId(), teamOwnership)).isTrue();
        assertThat(policyService.canRead(member.getId(), teamOwnership)).isTrue();
        assertThat(policyService.canManage(outsider.getId(), teamOwnership)).isFalse();

        assertThatThrownBy(() -> policyService.validateAssignment(
                new OwnershipContext(owner.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.TEAM, team.id(), organization.id() + 100, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policyService.validateAssignment(
                new OwnershipContext(member.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.ORGANIZATION, null, organization.id(), owner.getId())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policyService.validateAssignment(
                new OwnershipContext(member.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.TEAM, team.id(), organization.id(), owner.getId())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policyService.validateAssignment(
                new OwnershipContext(outsider.getId()),
                new OwnershipAssignmentRequest(
                        OwnershipScope.TEAM, team.id(), organization.id(), null)))
                .isInstanceOf(ForbiddenException.class);
    }

    private User createUser(String email) {
        User user = new User();
        user.setGlobalUserId(globalIdService.userId());
        user.setIdentitySource(IdentitySource.HDR);
        user.setEmail(email);
        user.setUsername(email);
        user.setPasswordHash("test-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
