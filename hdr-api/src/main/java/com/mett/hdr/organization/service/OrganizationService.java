package com.mett.hdr.organization.service;

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
import com.mett.hdr.organization.dto.OrganizationCreateRequest;
import com.mett.hdr.organization.dto.OrganizationMemberRequest;
import com.mett.hdr.organization.dto.OrganizationMemberResponse;
import com.mett.hdr.organization.dto.OrganizationMemberUpdateRequest;
import com.mett.hdr.organization.dto.OrganizationOwnershipTransferRequest;
import com.mett.hdr.organization.dto.OrganizationResponse;
import com.mett.hdr.organization.dto.OrganizationUpdateRequest;
import com.mett.hdr.organization.entity.Organization;
import com.mett.hdr.organization.entity.OrganizationMember;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final Set<String> TYPES = Set.of(
            "METT_INTERNAL", "DESIGN_ORGANIZATION", "PRODUCT_PROVIDER",
            "SERVICE_PROVIDER", "EXPERT_INSTITUTION", "OTHER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "ARCHIVED");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
    private static final Set<String> MEMBER_STATUSES = Set.of("ACTIVE", "SUSPENDED", "LEFT");

    private final CurrentActorService currentActorService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final GlobalIdService globalIdService;
    private final AuditService auditService;

    public OrganizationService(
            CurrentActorService currentActorService,
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            GlobalIdService globalIdService,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.globalIdService = globalIdService;
        this.auditService = auditService;
    }

    @Transactional
    public OrganizationResponse create(
            String authorization,
            OrganizationCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        String type = upper(request.organizationType());
        requireAllowed(type, TYPES, "Invalid organization type.");
        Organization organization = organizationRepository.create(
                globalIdService.organizationId(),
                request.name().trim(),
                type,
                trimToNull(request.description()),
                actor.userId());
        memberRepository.create(organization.id(), actor.userId(), "OWNER", actor.userId());
        auditService.record(actor.userId(), "ORGANIZATION_CREATE", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toResponse(organization, "OWNER");
    }

    public List<OrganizationResponse> list(String authorization) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        return organizationRepository.findActiveForUser(actor.userId()).stream()
                .map(organization -> toResponse(
                        organization,
                        requireActiveMember(organization.id(), actor.userId()).memberRole()))
                .toList();
    }

    public OrganizationResponse get(String authorization, String globalOrganizationId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        return toResponse(organization, requireActiveMember(organization.id(), actor.userId()).memberRole());
    }

    @Transactional
    public OrganizationResponse update(
            String authorization,
            String globalOrganizationId,
            OrganizationUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        OrganizationMember actorMember = requireActiveMember(organization.id(), actor.userId());
        requireOrgAdmin(actorMember);

        String name = request.name() == null ? organization.name() : requireNonBlank(request.name(), "Name is required.");
        String description = request.description() == null ? organization.description() : trimToNull(request.description());
        String status = request.status() == null ? organization.status() : upper(request.status());
        requireAllowed(status, STATUSES, "Invalid organization status.");
        Long managerId = organization.managedByUserId();
        if (request.managerGlobalUserId() != null) {
            if (!"OWNER".equals(actorMember.memberRole())) {
                throw new ForbiddenException("Only the organization owner may transfer management.");
            }
            User manager = requireUser(request.managerGlobalUserId());
            requireActiveMember(organization.id(), manager.getId());
            managerId = manager.getId();
        }
        Organization updated = organizationRepository.updateDetails(
                organization.id(), name, description, status, managerId);
        auditService.record(actor.userId(), "ORGANIZATION_UPDATE", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toResponse(updated, actorMember.memberRole());
    }

    public List<OrganizationMemberResponse> members(String authorization, String globalOrganizationId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        return memberRepository.findAll(organization.id()).stream().map(this::toMemberResponse).toList();
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            String authorization,
            String globalOrganizationId,
            OrganizationMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        OrganizationMember actorMember = requireActiveMember(organization.id(), actor.userId());
        requireOrgAdmin(actorMember);
        String role = upper(request.memberRole());
        if (!Set.of("ADMIN", "MEMBER").contains(role)) {
            throw new BadRequestException("New organization members must be ADMIN or MEMBER.");
        }
        if ("ADMIN".equals(role) && !"OWNER".equals(actorMember.memberRole())) {
            throw new ForbiddenException("Only the organization owner may grant ADMIN.");
        }
        User user = requireUser(request.globalUserId());
        OrganizationMember saved = memberRepository.find(organization.id(), user.getId())
                .map(existing -> {
                    if (roleChangeRequiresOwner(existing.memberRole(), role)
                            && !"OWNER".equals(actorMember.memberRole())) {
                        throw new ForbiddenException("Only the organization owner may change ADMIN membership.");
                    }
                    return memberRepository.update(existing.id(), role, "ACTIVE");
                })
                .orElseGet(() -> memberRepository.create(
                        organization.id(), user.getId(), role, actor.userId()));
        auditService.record(actor.userId(), "ORGANIZATION_MEMBER_ADD", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toMemberResponse(saved);
    }

    @Transactional
    public OrganizationMemberResponse updateMember(
            String authorization,
            String globalOrganizationId,
            String globalUserId,
            OrganizationMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        OrganizationMember actorMember = requireActiveMember(organization.id(), actor.userId());
        requireOrgAdmin(actorMember);
        User targetUser = requireUser(globalUserId);
        OrganizationMember target = requireMember(organization.id(), targetUser.getId());
        if ("OWNER".equals(target.memberRole())) {
            throw new ConflictException("Organization ownership must be transferred first.");
        }
        String role = request.memberRole() == null ? target.memberRole() : upper(request.memberRole());
        String status = request.status() == null ? target.status() : upper(request.status());
        requireAllowed(role, MEMBER_ROLES, "Invalid organization member role.");
        requireAllowed(status, MEMBER_STATUSES, "Invalid organization member status.");
        if ("OWNER".equals(role)) {
            throw new ConflictException("Use ownership transfer to assign OWNER.");
        }
        if (roleChangeRequiresOwner(target.memberRole(), role)
                && !"OWNER".equals(actorMember.memberRole())) {
            throw new ForbiddenException("Only the organization owner may change ADMIN membership.");
        }
        requireManagerMayTransition(organization, target, status);
        OrganizationMember updated = memberRepository.update(target.id(), role, status);
        transitionOrganizationTeams(organization, target, status);
        auditService.record(actor.userId(), "ORGANIZATION_MEMBER_UPDATE", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toMemberResponse(updated);
    }

    @Transactional
    public OrganizationMemberResponse removeMember(
            String authorization,
            String globalOrganizationId,
            String globalUserId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        OrganizationMember actorMember = requireActiveMember(organization.id(), actor.userId());
        requireOrgAdmin(actorMember);
        User targetUser = requireUser(globalUserId);
        OrganizationMember target = requireMember(organization.id(), targetUser.getId());
        if ("OWNER".equals(target.memberRole())) {
            throw new ConflictException("Organization ownership must be transferred first.");
        }
        if ("ADMIN".equals(target.memberRole()) && !"OWNER".equals(actorMember.memberRole())) {
            throw new ForbiddenException("Only the organization owner may remove an administrator.");
        }
        requireManagerMayTransition(organization, target, "LEFT");
        OrganizationMember updated = memberRepository.update(target.id(), target.memberRole(), "LEFT");
        transitionOrganizationTeams(organization, target, "LEFT");
        auditService.record(actor.userId(), "ORGANIZATION_MEMBER_LEAVE", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toMemberResponse(updated);
    }

    @Transactional
    public OrganizationResponse transferOwnership(
            String authorization,
            String globalOrganizationId,
            OrganizationOwnershipTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Organization organization = requireVisible(globalOrganizationId, actor.userId());
        if (!organization.ownerUserId().equals(actor.userId())) {
            throw new ForbiddenException("Only the current owner may transfer ownership.");
        }
        User newOwner = requireUser(request.newOwnerGlobalUserId());
        requireActiveMember(organization.id(), newOwner.getId());
        if (newOwner.getId().equals(actor.userId())) {
            throw new ConflictException("The selected user already owns the organization.");
        }
        memberRepository.transferOwnerRoles(organization.id(), actor.userId(), newOwner.getId());
        Organization updated = organizationRepository.transferOwnership(
                organization.id(), actor.userId(), newOwner.getId());
        if (memberRepository.countActiveOwners(organization.id()) != 1) {
            throw new ConflictException("Organization must have exactly one active owner.");
        }
        auditService.record(actor.userId(), "ORGANIZATION_OWNER_TRANSFER", "ORGANIZATION",
                organization.globalOrganizationId(), servletRequest);
        return toResponse(updated, "ADMIN");
    }

    public Organization requireVisible(String globalId, Long userId) {
        Organization organization = organizationRepository.findByGlobalId(globalId)
                .orElseThrow(() -> new NotFoundException("Organization not found."));
        OrganizationMember membership = memberRepository.find(organization.id(), userId)
                .filter(OrganizationMember::isActive)
                .orElseThrow(() -> new NotFoundException("Organization not found."));
        return organization;
    }

    public OrganizationMember requireActiveMember(Long organizationId, Long userId) {
        return memberRepository.find(organizationId, userId)
                .filter(OrganizationMember::isActive)
                .orElseThrow(() -> new ForbiddenException("Active organization membership is required."));
    }

    private OrganizationMember requireMember(Long organizationId, Long userId) {
        return memberRepository.find(organizationId, userId)
                .orElseThrow(() -> new NotFoundException("Organization member not found."));
    }

    private User requireUser(String globalUserId) {
        return userRepository.findByGlobalUserId(globalUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private void requireOrgAdmin(OrganizationMember membership) {
        if (!Set.of("OWNER", "ADMIN").contains(membership.memberRole())) {
            throw new ForbiddenException("Organization administrator access is required.");
        }
    }

    private void requireManagerMayTransition(
            Organization organization,
            OrganizationMember target,
            String newStatus
    ) {
        if (!"ACTIVE".equals(newStatus) && organization.managedByUserId().equals(target.userId())) {
            throw new ConflictException("Organization management must be transferred first.");
        }
        if (!"ACTIVE".equals(newStatus)
                && teamRepository.existsManagedOrganizationTeam(organization.id(), target.userId())) {
            throw new ConflictException("Team management must be transferred first.");
        }
    }

    private void transitionOrganizationTeams(
            Organization organization,
            OrganizationMember target,
            String newStatus
    ) {
        if (!"ACTIVE".equals(newStatus)) {
            teamMemberRepository.transitionForOrganizationMembership(
                    organization.id(), target.userId(), newStatus);
        }
    }

    private boolean roleChangeRequiresOwner(String oldRole, String newRole) {
        return !oldRole.equals(newRole) && ("ADMIN".equals(oldRole) || "ADMIN".equals(newRole));
    }

    private OrganizationResponse toResponse(Organization organization, String currentRole) {
        return new OrganizationResponse(
                organization.globalOrganizationId(),
                organization.globalCompanyId(),
                organization.name(),
                organization.organizationType(),
                organization.description(),
                organization.status(),
                currentRole,
                globalUserId(organization.ownerUserId()),
                globalUserId(organization.managedByUserId())
        );
    }

    private OrganizationMemberResponse toMemberResponse(OrganizationMember member) {
        return new OrganizationMemberResponse(
                globalUserId(member.userId()),
                member.memberRole(),
                member.status(),
                member.joinedAt(),
                member.leftAt()
        );
    }

    private String globalUserId(Long userId) {
        return userRepository.findById(userId).orElseThrow().getGlobalUserId();
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
