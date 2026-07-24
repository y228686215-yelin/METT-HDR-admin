package com.mett.hdr.project.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.organization.entity.Organization;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.ownership.model.OwnershipAssignmentRequest;
import com.mett.hdr.ownership.model.OwnershipContext;
import com.mett.hdr.ownership.model.OwnershipScope;
import com.mett.hdr.ownership.model.ResourceOwnership;
import com.mett.hdr.ownership.service.ResourceOwnershipPolicyService;
import com.mett.hdr.project.dto.ProjectCreateRequest;
import com.mett.hdr.project.dto.ProjectManagerTransferRequest;
import com.mett.hdr.project.dto.ProjectResponse;
import com.mett.hdr.project.dto.ProjectUpdateRequest;
import com.mett.hdr.project.entity.Project;
import com.mett.hdr.project.entity.ProjectMember;
import com.mett.hdr.project.repository.ProjectMemberRepository;
import com.mett.hdr.project.repository.ProjectRepository;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final Set<String> PROJECT_TYPES =
            Set.of("RESIDENTIAL", "SMALL_COMMERCIAL", "OTHER");
    private static final Set<String> PROJECT_STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

    private final CurrentActorService currentActorService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final GlobalIdService globalIdService;
    private final ResourceOwnershipPolicyService ownershipPolicyService;
    private final ProjectAccessPolicyService accessPolicyService;
    private final ProjectStateMachine stateMachine;
    private final AuditService auditService;

    public ProjectService(
            CurrentActorService currentActorService,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            TeamRepository teamRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            GlobalIdService globalIdService,
            ResourceOwnershipPolicyService ownershipPolicyService,
            ProjectAccessPolicyService accessPolicyService,
            ProjectStateMachine stateMachine,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.teamRepository = teamRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.globalIdService = globalIdService;
        this.ownershipPolicyService = ownershipPolicyService;
        this.accessPolicyService = accessPolicyService;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectResponse create(
            String authorization,
            ProjectCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        String type = allowed(request.projectType(), PROJECT_TYPES, "Invalid project type.");
        OwnershipScope scope = ownershipScope(request.ownershipScope());
        Long teamId = null;
        Long organizationId = null;
        if (scope == OwnershipScope.PERSONAL) {
            if (hasText(request.globalTeamId()) || hasText(request.globalOrganizationId())) {
                throw new BadRequestException("Personal ownership cannot include team or organization.");
            }
        } else if (scope == OwnershipScope.TEAM) {
            if (!hasText(request.globalTeamId()) || hasText(request.globalOrganizationId())) {
                throw new BadRequestException("Team ownership requires only a team.");
            }
            Team team = teamRepository.findByGlobalId(request.globalTeamId())
                    .orElseThrow(() -> new NotFoundException("Team not found."));
            if (!accessPolicyService.isActiveTeamMember(team.id(), actor.userId())) {
                throw new NotFoundException("Team not found.");
            }
            accessPolicyService.requireTeamCreationAccess(team.id(), actor.userId());
            teamId = team.id();
            organizationId = team.organizationId();
        } else {
            if (!hasText(request.globalOrganizationId()) || hasText(request.globalTeamId())) {
                throw new BadRequestException("Organization ownership requires only an organization.");
            }
            Organization organization = organizationRepository.findByGlobalId(request.globalOrganizationId())
                    .orElseThrow(() -> new NotFoundException("Organization not found."));
            if (!accessPolicyService.isActiveOrganizationMember(organization.id(), actor.userId())) {
                throw new NotFoundException("Organization not found.");
            }
            organizationId = organization.id();
        }
        ResourceOwnership ownership = ownershipPolicyService.validateAssignment(
                new OwnershipContext(actor.userId()),
                new OwnershipAssignmentRequest(scope, teamId, organizationId, actor.userId())
        );
        Project project = projectRepository.create(
                globalIdService.projectId(),
                globalIdService.projectNumber(),
                request.name().trim(),
                type,
                trimToNull(request.description()),
                trimToNull(request.city()),
                trimToNull(request.timezone()),
                ownership.ownerUserId(),
                ownership.teamId(),
                ownership.organizationId(),
                actor.userId(),
                ownership.managedByUserId()
        );
        memberRepository.create(project.id(), actor.userId(), "MANAGER", actor.userId());
        auditService.record(actor.userId(), "PROJECT_CREATE", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(project, actor.userId());
    }

    public List<ProjectResponse> list(
            String authorization,
            String status,
            String projectType,
            String ownershipScope
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        String statusFilter = optionalAllowed(status, PROJECT_STATUSES, "Invalid project status.");
        String typeFilter = optionalAllowed(projectType, PROJECT_TYPES, "Invalid project type.");
        OwnershipScope scopeFilter = hasText(ownershipScope) ? ownershipScope(ownershipScope) : null;
        return projectRepository.findAll().stream()
                .filter(project -> accessPolicyService.canRead(project, actor.userId()))
                .filter(project -> statusFilter == null || statusFilter.equals(project.status()))
                .filter(project -> typeFilter == null || typeFilter.equals(project.projectType()))
                .filter(project -> scopeFilter == null || scopeFilter.name().equals(scope(project)))
                .map(project -> toResponse(project, actor.userId()))
                .toList();
    }

    public ProjectResponse get(String authorization, String globalProjectId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = requireVisible(globalProjectId, actor.userId());
        return toResponse(project, actor.userId());
    }

    @Transactional
    public ProjectResponse update(
            String authorization,
            String globalProjectId,
            ProjectUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = requireVisible(globalProjectId, actor.userId());
        requireMutable(project);
        accessPolicyService.requireEdit(project, actor.userId());
        String name = request.name() == null ? project.name() : requireNonBlank(request.name());
        String type = request.projectType() == null
                ? project.projectType()
                : allowed(request.projectType(), PROJECT_TYPES, "Invalid project type.");
        Project updated = projectRepository.updateDetails(
                project.id(),
                name,
                type,
                request.description() == null ? project.description() : trimToNull(request.description()),
                request.city() == null ? project.city() : trimToNull(request.city()),
                request.timezone() == null ? project.timezone() : trimToNull(request.timezone())
        );
        auditService.record(actor.userId(), "PROJECT_UPDATE", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(updated, actor.userId());
    }

    @Transactional
    public ProjectResponse activate(
            String authorization,
            String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return transition(authorization, globalProjectId, "ACTIVE", "PROJECT_ACTIVATE", servletRequest);
    }

    @Transactional
    public ProjectResponse archive(
            String authorization,
            String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return transition(authorization, globalProjectId, "ARCHIVED", "PROJECT_ARCHIVE", servletRequest);
    }

    @Transactional
    public ProjectResponse restore(
            String authorization,
            String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return transition(authorization, globalProjectId, "ACTIVE", "PROJECT_RESTORE", servletRequest);
    }

    @Transactional
    public ProjectResponse transferManager(
            String authorization,
            String globalProjectId,
            ProjectManagerTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectRepository.lockByGlobalId(globalProjectId)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        if (!accessPolicyService.canRead(project, actor.userId())) {
            throw new NotFoundException("Project not found.");
        }
        accessPolicyService.requireTransfer(project, actor.userId());
        Long newManagerId = userRepository.findByGlobalUserId(request.newManagerGlobalUserId())
                .orElseThrow(() -> new NotFoundException("User not found."))
                .getId();
        if (project.managedByUserId().equals(newManagerId)) {
            throw new ConflictException("The selected user already manages the project.");
        }
        ProjectMember target = memberRepository.lock(project.id(), newManagerId)
                .filter(ProjectMember::isActive)
                .orElseThrow(() -> new ConflictException("New manager must be an active project member."));
        memberRepository.lock(project.id(), project.managedByUserId())
                .filter(ProjectMember::isActive)
                .orElseThrow(() -> new ConflictException("Current project manager membership is invalid."));
        memberRepository.transferManagerRoles(project.id(), project.managedByUserId(), target.userId());
        Project updated = projectRepository.transferManager(project.id(), newManagerId);
        if (memberRepository.countActiveManagers(project.id()) != 1) {
            throw new IllegalStateException("Project must have exactly one active manager.");
        }
        auditService.record(actor.userId(), "PROJECT_MANAGER_TRANSFER", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(updated, actor.userId());
    }

    public Project requireVisible(String globalProjectId, Long actorUserId) {
        Project project = projectRepository.findByGlobalId(globalProjectId)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        if (!accessPolicyService.canRead(project, actorUserId)) {
            throw new NotFoundException("Project not found.");
        }
        return project;
    }

    private ProjectResponse transition(
            String authorization,
            String globalProjectId,
            String target,
            String auditAction,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectRepository.lockByGlobalId(globalProjectId)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        if (!accessPolicyService.canRead(project, actor.userId())) {
            throw new NotFoundException("Project not found.");
        }
        accessPolicyService.requireManage(project, actor.userId());
        stateMachine.requireTransition(project.status(), target);
        LocalDateTime activatedAt = "ACTIVE".equals(target)
                ? LocalDateTime.now() : project.activatedAt();
        LocalDateTime archivedAt = "ARCHIVED".equals(target) ? LocalDateTime.now() : null;
        Project updated = projectRepository.updateStatus(
                project.id(), target, activatedAt, archivedAt);
        auditService.record(actor.userId(), auditAction, "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(updated, actor.userId());
    }

    private ProjectResponse toResponse(Project project, Long actorUserId) {
        String currentRole = memberRepository.find(project.id(), actorUserId)
                .filter(ProjectMember::isActive)
                .map(ProjectMember::role)
                .orElse(null);
        return new ProjectResponse(
                project.globalProjectId(),
                project.projectNumber(),
                project.name(),
                project.projectType(),
                project.description(),
                project.status(),
                project.originSystem(),
                scope(project),
                project.teamId() == null ? null : teamRepository.findById(project.teamId())
                        .orElseThrow().globalTeamId(),
                project.organizationId() == null ? null
                        : organizationRepository.findById(project.organizationId())
                                .orElseThrow().globalOrganizationId(),
                globalUserId(project.ownerUserId()),
                globalUserId(project.managedByUserId()),
                currentRole,
                project.city(),
                project.timezone(),
                project.activatedAt(),
                project.archivedAt(),
                project.createdAt(),
                project.updatedAt()
        );
    }

    private String scope(Project project) {
        if (project.teamId() != null) {
            return OwnershipScope.TEAM.name();
        }
        return project.organizationId() == null
                ? OwnershipScope.PERSONAL.name() : OwnershipScope.ORGANIZATION.name();
    }

    private String globalUserId(Long userId) {
        return userRepository.findById(userId).orElseThrow().getGlobalUserId();
    }

    private OwnershipScope ownershipScope(String value) {
        try {
            return OwnershipScope.valueOf(upper(value));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid ownership scope.");
        }
    }

    private String optionalAllowed(String value, Set<String> values, String message) {
        return hasText(value) ? allowed(value, values, message) : null;
    }

    private String allowed(String value, Set<String> values, String message) {
        String normalized = upper(value);
        if (!values.contains(normalized)) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String requireNonBlank(String value) {
        if (!hasText(value)) {
            throw new BadRequestException("Project name is required.");
        }
        return value.trim();
    }

    private void requireMutable(Project project) {
        if ("ARCHIVED".equals(project.status())) {
            throw new ConflictException("Archived projects cannot be modified.");
        }
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
