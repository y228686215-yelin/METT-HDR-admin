package com.mett.hdr.team.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.organization.entity.Organization;
import com.mett.hdr.organization.entity.OrganizationMember;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.team.dto.TeamCreateRequest;
import com.mett.hdr.team.dto.TeamManagerTransferRequest;
import com.mett.hdr.team.dto.TeamMemberRequest;
import com.mett.hdr.team.dto.TeamMemberResponse;
import com.mett.hdr.team.dto.TeamMemberUpdateRequest;
import com.mett.hdr.team.dto.TeamResponse;
import com.mett.hdr.team.dto.TeamUpdateRequest;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.entity.TeamMember;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private static final Set<String> TEAM_STATUSES = Set.of("ACTIVE", "SUSPENDED", "ARCHIVED");
    private static final Set<String> MEMBER_ROLES = Set.of("LEAD", "MEMBER");
    private static final Set<String> MEMBER_STATUSES = Set.of("ACTIVE", "SUSPENDED", "LEFT");

    private final CurrentActorService currentActorService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final GlobalIdService globalIdService;
    private final AuditService auditService;

    public TeamService(
            CurrentActorService currentActorService,
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            UserRepository userRepository,
            GlobalIdService globalIdService,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
        this.globalIdService = globalIdService;
        this.auditService = auditService;
    }

    @Transactional
    public TeamResponse create(
            String authorization,
            TeamCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Long organizationId = null;
        if (request.globalOrganizationId() != null && !request.globalOrganizationId().isBlank()) {
            Organization organization = organizationRepository.findByGlobalId(request.globalOrganizationId())
                    .orElseThrow(() -> new NotFoundException("Organization not found."));
            OrganizationMember membership = requireActiveOrganizationMember(
                    organization.id(), actor.userId());
            if (!Set.of("OWNER", "ADMIN").contains(membership.memberRole())) {
                throw new ForbiddenException("Organization OWNER or ADMIN is required to create a team.");
            }
            organizationId = organization.id();
        }
        Team team = teamRepository.create(
                globalIdService.teamId(),
                organizationId,
                request.name().trim(),
                trimToNull(request.description()),
                actor.userId());
        memberRepository.create(team.id(), actor.userId(), "LEAD", actor.userId());
        auditService.record(actor.userId(), "TEAM_CREATE", "TEAM", team.globalTeamId(), servletRequest);
        return toResponse(team, "LEAD");
    }

    public List<TeamResponse> list(String authorization) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        return teamRepository.findActiveForUser(actor.userId()).stream()
                .map(team -> toResponse(team, requireActiveMember(team.id(), actor.userId()).memberRole()))
                .toList();
    }

    public TeamResponse get(String authorization, String globalTeamId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        return toResponse(team, requireActiveMember(team.id(), actor.userId()).memberRole());
    }

    @Transactional
    public TeamResponse update(
            String authorization,
            String globalTeamId,
            TeamUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        TeamMember actorMember = requireActiveMember(team.id(), actor.userId());
        requireLead(actorMember);
        String name = request.name() == null ? team.name() : requireNonBlank(request.name(), "Name is required.");
        String description = request.description() == null ? team.description() : trimToNull(request.description());
        String status = request.status() == null ? team.status() : upper(request.status());
        requireAllowed(status, TEAM_STATUSES, "Invalid team status.");
        Team updated = teamRepository.updateDetails(team.id(), name, description, status);
        auditService.record(actor.userId(), "TEAM_UPDATE", "TEAM", team.globalTeamId(), servletRequest);
        return toResponse(updated, actorMember.memberRole());
    }

    public List<TeamMemberResponse> members(String authorization, String globalTeamId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        return memberRepository.findAll(team.id()).stream().map(this::toMemberResponse).toList();
    }

    @Transactional
    public TeamMemberResponse addMember(
            String authorization,
            String globalTeamId,
            TeamMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        requireLead(requireActiveMember(team.id(), actor.userId()));
        String role = upper(request.memberRole());
        requireAllowed(role, MEMBER_ROLES, "Invalid team member role.");
        User user = requireUser(request.globalUserId());
        if (team.organizationId() != null) {
            requireActiveOrganizationMember(team.organizationId(), user.getId());
        }
        TeamMember saved = memberRepository.find(team.id(), user.getId())
                .map(existing -> memberRepository.update(existing.id(), role, "ACTIVE"))
                .orElseGet(() -> memberRepository.create(team.id(), user.getId(), role, actor.userId()));
        auditService.record(actor.userId(), "TEAM_MEMBER_ADD", "TEAM", team.globalTeamId(), servletRequest);
        return toMemberResponse(saved);
    }

    @Transactional
    public TeamMemberResponse updateMember(
            String authorization,
            String globalTeamId,
            String globalUserId,
            TeamMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        requireLead(requireActiveMember(team.id(), actor.userId()));
        User targetUser = requireUser(globalUserId);
        TeamMember target = requireMember(team.id(), targetUser.getId());
        String role = request.memberRole() == null ? target.memberRole() : upper(request.memberRole());
        String status = request.status() == null ? target.status() : upper(request.status());
        requireAllowed(role, MEMBER_ROLES, "Invalid team member role.");
        requireAllowed(status, MEMBER_STATUSES, "Invalid team member status.");
        requireManagerMayTransition(team, target, role, status);
        if (team.organizationId() != null && "ACTIVE".equals(status)) {
            requireActiveOrganizationMember(team.organizationId(), target.userId());
        }
        TeamMember updated = memberRepository.update(target.id(), role, status);
        auditService.record(actor.userId(), "TEAM_MEMBER_UPDATE", "TEAM", team.globalTeamId(), servletRequest);
        return toMemberResponse(updated);
    }

    @Transactional
    public TeamMemberResponse removeMember(
            String authorization,
            String globalTeamId,
            String globalUserId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = requireVisible(globalTeamId, actor.userId());
        requireLead(requireActiveMember(team.id(), actor.userId()));
        User targetUser = requireUser(globalUserId);
        TeamMember target = requireMember(team.id(), targetUser.getId());
        requireManagerMayTransition(team, target, target.memberRole(), "LEFT");
        TeamMember updated = memberRepository.update(target.id(), target.memberRole(), "LEFT");
        auditService.record(actor.userId(), "TEAM_MEMBER_LEAVE", "TEAM", team.globalTeamId(), servletRequest);
        return toMemberResponse(updated);
    }

    @Transactional
    public TeamResponse transferManager(
            String authorization,
            String globalTeamId,
            TeamManagerTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = teamRepository.findByGlobalId(globalTeamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        boolean currentManager = team.managedByUserId().equals(actor.userId());
        boolean organizationOwner = team.organizationId() != null
                && organizationMemberRepository.find(team.organizationId(), actor.userId())
                .filter(OrganizationMember::isActive)
                .map(member -> "OWNER".equals(member.memberRole()))
                .orElse(false);
        if (!currentManager && !organizationOwner) {
            throw new ForbiddenException("Team manager or owning organization owner access is required.");
        }
        User newManager = requireUser(request.newManagerGlobalUserId());
        TeamMember managerMember = requireActiveMember(team.id(), newManager.getId());
        if (!"LEAD".equals(managerMember.memberRole())) {
            throw new ConflictException("The new team manager must be an active LEAD.");
        }
        if (team.managedByUserId().equals(newManager.getId())) {
            throw new ConflictException("The selected user already manages the team.");
        }
        Team updated = teamRepository.transferManager(team.id(), newManager.getId());
        auditService.record(actor.userId(), "TEAM_MANAGER_TRANSFER", "TEAM",
                team.globalTeamId(), servletRequest);
        String currentRole = memberRepository.find(team.id(), actor.userId())
                .filter(TeamMember::isActive).map(TeamMember::memberRole).orElse(null);
        return toResponse(updated, currentRole);
    }

    public Team requireVisible(String globalId, Long userId) {
        Team team = teamRepository.findByGlobalId(globalId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        memberRepository.find(team.id(), userId)
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        return team;
    }

    public TeamMember requireActiveMember(Long teamId, Long userId) {
        return memberRepository.find(teamId, userId)
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new ForbiddenException("Active team membership is required."));
    }

    private TeamMember requireMember(Long teamId, Long userId) {
        return memberRepository.find(teamId, userId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));
    }

    private OrganizationMember requireActiveOrganizationMember(Long organizationId, Long userId) {
        return organizationMemberRepository.find(organizationId, userId)
                .filter(OrganizationMember::isActive)
                .orElseThrow(() -> new ForbiddenException("Active organization membership is required."));
    }

    private User requireUser(String globalUserId) {
        return userRepository.findByGlobalUserId(globalUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private void requireLead(TeamMember member) {
        if (!"LEAD".equals(member.memberRole())) {
            throw new ForbiddenException("Team LEAD access is required.");
        }
    }

    private void requireManagerMayTransition(
            Team team,
            TeamMember target,
            String newRole,
            String newStatus
    ) {
        if (team.managedByUserId().equals(target.userId())
                && (!"ACTIVE".equals(newStatus) || !"LEAD".equals(newRole))) {
            throw new ConflictException("Team management must be transferred first.");
        }
    }

    private TeamResponse toResponse(Team team, String currentRole) {
        return new TeamResponse(
                team.globalTeamId(),
                team.name(),
                team.description(),
                team.status(),
                team.organizationId() == null ? null : organizationRepository.findById(team.organizationId())
                        .orElseThrow().globalOrganizationId(),
                currentRole,
                globalUserId(team.ownerUserId()),
                globalUserId(team.managedByUserId())
        );
    }

    private TeamMemberResponse toMemberResponse(TeamMember member) {
        return new TeamMemberResponse(
                globalUserId(member.userId()),
                member.memberRole(),
                member.status(),
                member.joinedAt(),
                member.leftAt()
        );
    }

    private String globalUserId(Long id) {
        return userRepository.findById(id).orElseThrow().getGlobalUserId();
    }

    private void requireAllowed(String value, Set<String> allowed, String message) {
        if (!allowed.contains(value)) {
            throw new BadRequestException(message);
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
