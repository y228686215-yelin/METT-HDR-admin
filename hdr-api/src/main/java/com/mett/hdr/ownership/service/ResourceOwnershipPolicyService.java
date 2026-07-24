package com.mett.hdr.ownership.service;

import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.organization.entity.OrganizationMember;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.ownership.model.OwnershipAssignmentRequest;
import com.mett.hdr.ownership.model.OwnershipContext;
import com.mett.hdr.ownership.model.OwnershipScope;
import com.mett.hdr.ownership.model.ResourceOwnership;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.entity.TeamMember;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import org.springframework.stereotype.Service;

@Service
public class ResourceOwnershipPolicyService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public ResourceOwnershipPolicyService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    public ResourceOwnership validateAssignment(
            OwnershipContext context,
            OwnershipAssignmentRequest request
    ) {
        if (context == null || context.actorUserId() == null || request == null || request.scope() == null) {
            throw new BadRequestException("Ownership context and scope are required.");
        }
        return switch (request.scope()) {
            case PERSONAL -> personal(context, request);
            case ORGANIZATION -> organization(context, request);
            case TEAM -> team(context, request);
        };
    }

    public boolean canRead(Long actorUserId, ResourceOwnership ownership) {
        if (actorUserId == null || ownership == null) {
            return false;
        }
        if (actorUserId.equals(ownership.ownerUserId()) || actorUserId.equals(ownership.managedByUserId())) {
            return true;
        }
        if (ownership.teamId() != null) {
            return activeTeamMember(ownership.teamId(), actorUserId) != null;
        }
        return ownership.organizationId() != null
                && activeOrganizationMember(ownership.organizationId(), actorUserId) != null;
    }

    public boolean canManage(Long actorUserId, ResourceOwnership ownership) {
        if (actorUserId == null || ownership == null) {
            return false;
        }
        if (actorUserId.equals(ownership.ownerUserId()) || actorUserId.equals(ownership.managedByUserId())) {
            return true;
        }
        if (ownership.teamId() != null) {
            TeamMember teamMember = activeTeamMember(ownership.teamId(), actorUserId);
            if (teamMember != null && "LEAD".equals(teamMember.memberRole())) {
                return true;
            }
            Team team = teamRepository.findById(ownership.teamId()).orElse(null);
            return team != null && team.organizationId() != null
                    && isOrganizationOwner(team.organizationId(), actorUserId);
        }
        if (ownership.organizationId() != null) {
            OrganizationMember member = activeOrganizationMember(ownership.organizationId(), actorUserId);
            return member != null && ("OWNER".equals(member.memberRole()) || "ADMIN".equals(member.memberRole()));
        }
        return false;
    }

    public boolean canTransferManagement(Long actorUserId, ResourceOwnership ownership) {
        return canManage(actorUserId, ownership);
    }

    private ResourceOwnership personal(
            OwnershipContext context,
            OwnershipAssignmentRequest request
    ) {
        if (request.teamId() != null || request.organizationId() != null) {
            throw new BadRequestException("Personal ownership cannot include team or organization.");
        }
        if (request.managedByUserId() != null && !context.actorUserId().equals(request.managedByUserId())) {
            throw new ForbiddenException("Personal ownership manager must be the authenticated user.");
        }
        return new ResourceOwnership(
                context.actorUserId(), null, null, context.actorUserId(), context.actorUserId());
    }

    private ResourceOwnership organization(
            OwnershipContext context,
            OwnershipAssignmentRequest request
    ) {
        if (request.organizationId() == null || request.teamId() != null) {
            throw new BadRequestException("Organization ownership requires only an organization.");
        }
        organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new NotFoundException("Organization not found."));
        OrganizationMember actor = requireActiveOrganizationMember(
                request.organizationId(), context.actorUserId());
        Long managerId = request.managedByUserId() == null
                ? context.actorUserId() : request.managedByUserId();
        requireActiveOrganizationMember(request.organizationId(), managerId);
        if (!managerId.equals(context.actorUserId())
                && !("OWNER".equals(actor.memberRole()) || "ADMIN".equals(actor.memberRole()))) {
            throw new ForbiddenException("Only organization OWNER or ADMIN may assign another manager.");
        }
        return new ResourceOwnership(
                context.actorUserId(),
                null,
                request.organizationId(),
                context.actorUserId(),
                managerId
        );
    }

    private ResourceOwnership team(
            OwnershipContext context,
            OwnershipAssignmentRequest request
    ) {
        if (request.teamId() == null) {
            throw new BadRequestException("Team ownership requires a team.");
        }
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new NotFoundException("Team not found."));
        if (request.organizationId() != null && !request.organizationId().equals(team.organizationId())) {
            throw new BadRequestException("Team and organization ownership context do not match.");
        }
        TeamMember actor = requireActiveTeamMember(team.id(), context.actorUserId());
        Long managerId = request.managedByUserId() == null
                ? context.actorUserId() : request.managedByUserId();
        requireActiveTeamMember(team.id(), managerId);
        if (!managerId.equals(context.actorUserId()) && !"LEAD".equals(actor.memberRole())) {
            throw new ForbiddenException("Only a team LEAD may assign another team manager.");
        }
        return new ResourceOwnership(
                context.actorUserId(),
                team.id(),
                team.organizationId(),
                context.actorUserId(),
                managerId
        );
    }

    private OrganizationMember requireActiveOrganizationMember(Long organizationId, Long userId) {
        OrganizationMember member = activeOrganizationMember(organizationId, userId);
        if (member == null) {
            throw new ForbiddenException("Active organization membership is required.");
        }
        return member;
    }

    private TeamMember requireActiveTeamMember(Long teamId, Long userId) {
        TeamMember member = activeTeamMember(teamId, userId);
        if (member == null) {
            throw new ForbiddenException("Active team membership is required.");
        }
        return member;
    }

    private OrganizationMember activeOrganizationMember(Long organizationId, Long userId) {
        return organizationMemberRepository.find(organizationId, userId)
                .filter(OrganizationMember::isActive)
                .orElse(null);
    }

    private TeamMember activeTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.find(teamId, userId)
                .filter(TeamMember::isActive)
                .orElse(null);
    }

    private boolean isOrganizationOwner(Long organizationId, Long userId) {
        OrganizationMember member = activeOrganizationMember(organizationId, userId);
        return member != null && "OWNER".equals(member.memberRole());
    }
}
