package com.mett.hdr.project.service;

import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.service.EntitlementResolutionService;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.ownership.model.ResourceOwnership;
import com.mett.hdr.ownership.service.ResourceOwnershipPolicyService;
import com.mett.hdr.project.entity.Project;
import com.mett.hdr.project.entity.ProjectMember;
import com.mett.hdr.project.repository.ProjectMemberRepository;
import com.mett.hdr.team.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessPolicyService {

    private final MembershipRepository membershipRepository;
    private final EntitlementResolutionService entitlementService;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ResourceOwnershipPolicyService ownershipPolicyService;

    public ProjectAccessPolicyService(
            MembershipRepository membershipRepository,
            EntitlementResolutionService entitlementService,
            ProjectMemberRepository projectMemberRepository,
            TeamMemberRepository teamMemberRepository,
            OrganizationMemberRepository organizationMemberRepository,
            ResourceOwnershipPolicyService ownershipPolicyService
    ) {
        this.membershipRepository = membershipRepository;
        this.entitlementService = entitlementService;
        this.projectMemberRepository = projectMemberRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.ownershipPolicyService = ownershipPolicyService;
    }

    public void requirePersonalAccess(Long userId) {
        Membership membership = membershipRepository.findCurrentForUser(userId)
                .orElseThrow(() -> new ForbiddenException("An active personal membership is required."));
        entitlementService.requireEntitlement(membership, "HDR_ACCESS");
    }

    public void requireTeamCreationAccess(Long teamId, Long userId) {
        requireActiveTeamMember(teamId, userId);
        Membership membership = membershipRepository.findCurrentForTeam(teamId)
                .orElseThrow(() -> new ForbiddenException("An active team membership plan is required."));
        entitlementService.requireEntitlement(membership, "TEAM_WORKSPACE_ACCESS");
    }

    public void requireContextMutationAccess(Project project, Long userId) {
        if (project.teamId() != null) {
            requireTeamCreationAccess(project.teamId(), userId);
        } else if (project.organizationId() != null) {
            requireActiveOrganizationMember(project.organizationId(), userId);
        }
    }

    public boolean canRead(Project project, Long userId) {
        return activeProjectMember(project.id(), userId) != null
                || ownershipPolicyService.canManage(userId, ownership(project));
    }

    public boolean canEdit(Project project, Long userId) {
        ProjectMember member = activeProjectMember(project.id(), userId);
        return member != null && ("MANAGER".equals(member.role()) || "EDITOR".equals(member.role()))
                || ownershipPolicyService.canManage(userId, ownership(project));
    }

    public boolean canManage(Project project, Long userId) {
        ProjectMember member = activeProjectMember(project.id(), userId);
        return member != null && "MANAGER".equals(member.role())
                || ownershipPolicyService.canManage(userId, ownership(project));
    }

    public void requireRead(Project project, Long userId) {
        if (!canRead(project, userId)) {
            throw new ForbiddenException("Project access is required.");
        }
    }

    public void requireEdit(Project project, Long userId) {
        requireContextMutationAccess(project, userId);
        if (!canEdit(project, userId)) {
            throw new ForbiddenException("Project edit access is required.");
        }
    }

    public void requireManage(Project project, Long userId) {
        requireContextMutationAccess(project, userId);
        if (!canManage(project, userId)) {
            throw new ForbiddenException("Project management access is required.");
        }
    }

    public void requireTransfer(Project project, Long userId) {
        requireContextMutationAccess(project, userId);
        if (!ownershipPolicyService.canTransferManagement(userId, ownership(project))
                && !project.managedByUserId().equals(userId)) {
            throw new ForbiddenException("Project manager transfer access is required.");
        }
    }

    public boolean isActiveTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.find(teamId, userId)
                .map(member -> "ACTIVE".equals(member.status()))
                .orElse(false);
    }

    public boolean isActiveOrganizationMember(Long organizationId, Long userId) {
        return organizationMemberRepository.find(organizationId, userId)
                .map(member -> "ACTIVE".equals(member.status()))
                .orElse(false);
    }

    private void requireActiveTeamMember(Long teamId, Long userId) {
        if (!isActiveTeamMember(teamId, userId)) {
            throw new ForbiddenException("Active team membership is required.");
        }
    }

    private void requireActiveOrganizationMember(Long organizationId, Long userId) {
        if (!isActiveOrganizationMember(organizationId, userId)) {
            throw new ForbiddenException("Active organization membership is required.");
        }
    }

    private ProjectMember activeProjectMember(Long projectId, Long userId) {
        return projectMemberRepository.find(projectId, userId)
                .filter(ProjectMember::isActive)
                .orElse(null);
    }

    private ResourceOwnership ownership(Project project) {
        return new ResourceOwnership(
                project.ownerUserId(),
                project.teamId(),
                project.organizationId(),
                project.createdByUserId(),
                project.managedByUserId()
        );
    }
}
