package com.mett.hdr.project.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.entity.UserStatus;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.project.dto.ProjectMemberRequest;
import com.mett.hdr.project.dto.ProjectMemberResponse;
import com.mett.hdr.project.dto.ProjectMemberUpdateRequest;
import com.mett.hdr.project.entity.Project;
import com.mett.hdr.project.entity.ProjectMember;
import com.mett.hdr.project.repository.ProjectMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {

    private static final Set<String> MEMBER_ROLES = Set.of("EDITOR", "VIEWER");
    private static final Set<String> MEMBER_STATUSES = Set.of("ACTIVE", "SUSPENDED");

    private final CurrentActorService currentActorService;
    private final ProjectService projectService;
    private final ProjectAccessPolicyService accessPolicyService;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProjectMemberService(
            CurrentActorService currentActorService,
            ProjectService projectService,
            ProjectAccessPolicyService accessPolicyService,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.projectService = projectService;
        this.accessPolicyService = accessPolicyService;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<ProjectMemberResponse> list(String authorization, String globalProjectId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        return memberRepository.findAll(project.id()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProjectMemberResponse add(
            String authorization,
            String globalProjectId,
            ProjectMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireNormalMutation(project);
        accessPolicyService.requireManage(project, actor.userId());
        String role = allowed(request.memberRole(), MEMBER_ROLES, "Invalid project member role.");
        User target = requireActiveUser(request.globalUserId());
        requireTargetContext(project, target.getId());
        ProjectMember saved = memberRepository.find(project.id(), target.getId())
                .map(existing -> reactivate(existing, role))
                .orElseGet(() -> memberRepository.create(
                        project.id(), target.getId(), role, actor.userId()));
        auditService.record(actor.userId(), "PROJECT_MEMBER_ADD", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(saved);
    }

    @Transactional
    public ProjectMemberResponse update(
            String authorization,
            String globalProjectId,
            String globalUserId,
            ProjectMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireNormalMutation(project);
        accessPolicyService.requireManage(project, actor.userId());
        User targetUser = requireActiveUser(globalUserId);
        ProjectMember target = memberRepository.find(project.id(), targetUser.getId())
                .orElseThrow(() -> new NotFoundException("Project member not found."));
        requireNotManager(project, target);
        String role = request.memberRole() == null
                ? target.role()
                : allowed(request.memberRole(), MEMBER_ROLES, "Invalid project member role.");
        String status = request.status() == null
                ? target.status()
                : allowed(request.status(), MEMBER_STATUSES, "Invalid project member status.");
        if ("ACTIVE".equals(status)) {
            requireTargetContext(project, target.userId());
        }
        ProjectMember updated = memberRepository.update(target.id(), role, status);
        auditService.record(actor.userId(), "PROJECT_MEMBER_UPDATE", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(updated);
    }

    @Transactional
    public ProjectMemberResponse remove(
            String authorization,
            String globalProjectId,
            String globalUserId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireNormalMutation(project);
        accessPolicyService.requireManage(project, actor.userId());
        User targetUser = userRepository.findByGlobalUserId(globalUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        ProjectMember target = memberRepository.find(project.id(), targetUser.getId())
                .orElseThrow(() -> new NotFoundException("Project member not found."));
        requireNotManager(project, target);
        ProjectMember updated = memberRepository.update(target.id(), target.role(), "LEFT");
        auditService.record(actor.userId(), "PROJECT_MEMBER_LEAVE", "PROJECT",
                project.globalProjectId(), servletRequest);
        return toResponse(updated);
    }

    private ProjectMember reactivate(ProjectMember existing, String role) {
        if (existing.isActive()) {
            throw new ConflictException("User is already an active project member.");
        }
        return memberRepository.update(existing.id(), role, "ACTIVE");
    }

    private void requireTargetContext(Project project, Long userId) {
        if (project.teamId() != null
                && !accessPolicyService.isActiveTeamMember(project.teamId(), userId)) {
            throw new ConflictException("Project member must be an active owning-team member.");
        }
        if (project.teamId() == null && project.organizationId() != null
                && !accessPolicyService.isActiveOrganizationMember(project.organizationId(), userId)) {
            throw new ConflictException("Project member must be an active owning-organization member.");
        }
    }

    private void requireNotManager(Project project, ProjectMember member) {
        if (project.managedByUserId().equals(member.userId()) || "MANAGER".equals(member.role())) {
            throw new ConflictException("Project management must be transferred first.");
        }
    }

    private User requireActiveUser(String globalUserId) {
        User user = userRepository.findByGlobalUserId(globalUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Project member must be an active HDR user.");
        }
        return user;
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        String globalUserId = userRepository.findById(member.userId())
                .orElseThrow().getGlobalUserId();
        return new ProjectMemberResponse(
                globalUserId,
                member.role(),
                member.status(),
                member.joinedAt(),
                member.leftAt()
        );
    }

    private String allowed(String value, Set<String> allowed, String message) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private void requireNormalMutation(Project project) {
        if ("ARCHIVED".equals(project.status())) {
            throw new ConflictException("Archived projects cannot accept member mutations.");
        }
    }
}
