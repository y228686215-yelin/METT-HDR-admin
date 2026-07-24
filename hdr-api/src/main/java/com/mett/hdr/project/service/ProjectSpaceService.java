package com.mett.hdr.project.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.project.dto.ProjectSpaceCreateRequest;
import com.mett.hdr.project.dto.ProjectSpaceResponse;
import com.mett.hdr.project.dto.ProjectSpaceUpdateRequest;
import com.mett.hdr.project.entity.Project;
import com.mett.hdr.project.entity.ProjectSpace;
import com.mett.hdr.project.repository.ProjectSpaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSpaceService {

    private static final Set<String> LEVELS = Set.of("FLOOR", "ROOM", "ZONE");
    private static final Set<String> GEOMETRIES = Set.of("RECTANGLE", "UNSPECIFIED");
    private static final Set<String> ORIENTATIONS =
            Set.of("N", "NE", "E", "SE", "S", "SW", "W", "NW", "INTERNAL", "UNKNOWN");

    private final CurrentActorService currentActorService;
    private final ProjectService projectService;
    private final ProjectAccessPolicyService accessPolicyService;
    private final ProjectSpaceRepository spaceRepository;
    private final GlobalIdService globalIdService;
    private final AuditService auditService;

    public ProjectSpaceService(
            CurrentActorService currentActorService,
            ProjectService projectService,
            ProjectAccessPolicyService accessPolicyService,
            ProjectSpaceRepository spaceRepository,
            GlobalIdService globalIdService,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.projectService = projectService;
        this.accessPolicyService = accessPolicyService;
        this.spaceRepository = spaceRepository;
        this.globalIdService = globalIdService;
        this.auditService = auditService;
    }

    public List<ProjectSpaceResponse> list(String authorization, String globalProjectId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        return spaceRepository.findAll(project.id()).stream().map(this::toResponse).toList();
    }

    public ProjectSpaceResponse get(
            String authorization,
            String globalProjectId,
            String globalSpaceId
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        return toResponse(requireSpace(project, globalSpaceId));
    }

    @Transactional
    public ProjectSpaceResponse create(
            String authorization,
            String globalProjectId,
            ProjectSpaceCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireActiveProject(project);
        accessPolicyService.requireEdit(project, actor.userId());
        ProjectSpace parent = resolveParent(project, request.parentGlobalSpaceId(), null);
        Geometry geometry = geometry(
                request.geometryType(), request.lengthM(), request.widthM(), request.heightM());
        ProjectSpace created = spaceRepository.create(
                globalIdService.spaceId(),
                project.id(),
                parent == null ? null : parent.id(),
                request.name().trim(),
                allowed(request.spaceLevelType(), LEVELS, "Invalid space level type."),
                trimToNull(request.usageCode()),
                geometry.type(),
                geometry.length(),
                geometry.width(),
                geometry.height(),
                geometry.area(),
                geometry.volume(),
                optionalAllowed(request.orientationCode(), ORIENTATIONS, "Invalid orientation code."),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                actor.userId()
        );
        auditService.record(actor.userId(), "PROJECT_SPACE_CREATE", "PROJECT_SPACE",
                created.globalSpaceId(), servletRequest);
        return toResponse(created);
    }

    @Transactional
    public ProjectSpaceResponse update(
            String authorization,
            String globalProjectId,
            String globalSpaceId,
            ProjectSpaceUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireActiveProject(project);
        accessPolicyService.requireEdit(project, actor.userId());
        ProjectSpace space = requireSpace(project, globalSpaceId);
        if ("ARCHIVED".equals(space.status())) {
            throw new ConflictException("Archived spaces cannot be modified.");
        }
        ProjectSpace parent = request.parentGlobalSpaceIdPresent()
                ? resolveParent(project, request.parentGlobalSpaceId(), space.id())
                : parent(space);
        String geometryType = request.geometryType() == null
                ? space.geometryType() : request.geometryType();
        Geometry geometry = geometry(
                geometryType,
                request.lengthM() == null ? space.length() : request.lengthM(),
                request.widthM() == null ? space.width() : request.widthM(),
                request.heightM() == null ? space.height() : request.heightM()
        );
        ProjectSpace updated = spaceRepository.update(
                space.id(),
                parent == null ? null : parent.id(),
                request.name() == null ? space.name() : requireNonBlank(request.name()),
                request.spaceLevelType() == null
                        ? space.spaceLevel()
                        : allowed(request.spaceLevelType(), LEVELS, "Invalid space level type."),
                request.usageCode() == null ? space.usageCode() : trimToNull(request.usageCode()),
                geometry.type(),
                geometry.length(),
                geometry.width(),
                geometry.height(),
                geometry.area(),
                geometry.volume(),
                request.orientationCode() == null
                        ? space.orientation()
                        : optionalAllowed(request.orientationCode(), ORIENTATIONS,
                                "Invalid orientation code."),
                request.sortOrder() == null ? space.sortOrder() : request.sortOrder()
        );
        auditService.record(actor.userId(), "PROJECT_SPACE_UPDATE", "PROJECT_SPACE",
                space.globalSpaceId(), servletRequest);
        return toResponse(updated);
    }

    @Transactional
    public ProjectSpaceResponse archive(
            String authorization,
            String globalProjectId,
            String globalSpaceId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireActiveProject(project);
        accessPolicyService.requireEdit(project, actor.userId());
        ProjectSpace space = requireSpace(project, globalSpaceId);
        if (!"ACTIVE".equals(space.status())) {
            throw new ConflictException("Space is already archived.");
        }
        if (spaceRepository.countActiveChildren(space.id()) > 0) {
            throw new ConflictException("Active child spaces must be archived first.");
        }
        ProjectSpace updated = spaceRepository.updateStatus(space.id(), "ARCHIVED");
        auditService.record(actor.userId(), "PROJECT_SPACE_ARCHIVE", "PROJECT_SPACE",
                space.globalSpaceId(), servletRequest);
        return toResponse(updated);
    }

    @Transactional
    public ProjectSpaceResponse restore(
            String authorization,
            String globalProjectId,
            String globalSpaceId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        accessPolicyService.requirePersonalAccess(actor.userId());
        Project project = projectService.requireVisible(globalProjectId, actor.userId());
        requireActiveProject(project);
        accessPolicyService.requireEdit(project, actor.userId());
        ProjectSpace space = requireSpace(project, globalSpaceId);
        if (!"ARCHIVED".equals(space.status())) {
            throw new ConflictException("Space is already active.");
        }
        ProjectSpace parent = parent(space);
        if (parent != null && !"ACTIVE".equals(parent.status())) {
            throw new ConflictException("Parent space must be active before restoration.");
        }
        ProjectSpace updated = spaceRepository.updateStatus(space.id(), "ACTIVE");
        auditService.record(actor.userId(), "PROJECT_SPACE_RESTORE", "PROJECT_SPACE",
                space.globalSpaceId(), servletRequest);
        return toResponse(updated);
    }

    private ProjectSpace resolveParent(Project project, String globalParentId, Long movingSpaceId) {
        if (globalParentId == null || globalParentId.isBlank()) {
            return null;
        }
        ProjectSpace parent = spaceRepository.findByGlobalId(globalParentId)
                .orElseThrow(() -> new NotFoundException("Parent space not found."));
        if (!project.id().equals(parent.projectId())) {
            throw new BadRequestException("Parent space must belong to the same project.");
        }
        if (!"ACTIVE".equals(parent.status())) {
            throw new ConflictException("Parent space must be active.");
        }
        Long cursor = parent.id();
        while (cursor != null) {
            if (cursor.equals(movingSpaceId)) {
                throw new ConflictException("Space hierarchy cycle is not allowed.");
            }
            cursor = spaceRepository.findById(cursor).map(ProjectSpace::parentSpaceId).orElse(null);
        }
        return parent;
    }

    private ProjectSpace parent(ProjectSpace space) {
        return space.parentSpaceId() == null
                ? null : spaceRepository.findById(space.parentSpaceId()).orElseThrow();
    }

    private ProjectSpace requireSpace(Project project, String globalSpaceId) {
        ProjectSpace space = spaceRepository.findByGlobalId(globalSpaceId)
                .orElseThrow(() -> new NotFoundException("Space not found."));
        if (!project.id().equals(space.projectId())) {
            throw new NotFoundException("Space not found.");
        }
        return space;
    }

    private Geometry geometry(
            String geometryType,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height
    ) {
        String type = allowed(geometryType, GEOMETRIES, "Invalid geometry type.");
        requirePositive(length, "Length must be positive.");
        requirePositive(width, "Width must be positive.");
        requirePositive(height, "Height must be positive.");
        BigDecimal area = null;
        BigDecimal volume = null;
        if ("RECTANGLE".equals(type)) {
            if (length == null || width == null) {
                throw new BadRequestException("Rectangle length and width are required.");
            }
            area = length.multiply(width).setScale(3, RoundingMode.HALF_UP);
            if (height != null) {
                volume = area.multiply(height).setScale(3, RoundingMode.HALF_UP);
            }
        }
        return new Geometry(type, scaled(length), scaled(width), scaled(height), area, volume);
    }

    private ProjectSpaceResponse toResponse(ProjectSpace space) {
        String parentGlobalId = space.parentSpaceId() == null
                ? null : spaceRepository.findById(space.parentSpaceId()).orElseThrow().globalSpaceId();
        return new ProjectSpaceResponse(
                space.globalSpaceId(),
                parentGlobalId,
                space.name(),
                space.spaceLevel(),
                space.usageCode(),
                space.geometryType(),
                space.length(),
                space.width(),
                space.height(),
                space.area(),
                space.volume(),
                space.orientation(),
                space.status(),
                space.sortOrder(),
                space.archivedAt(),
                space.createdAt(),
                space.updatedAt()
        );
    }

    private String allowed(String value, Set<String> allowed, String message) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String optionalAllowed(String value, Set<String> allowed, String message) {
        return value == null || value.isBlank() ? null : allowed(value, allowed, message);
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value != null && value.signum() <= 0) {
            throw new BadRequestException(message);
        }
    }

    private BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private String requireNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Space name is required.");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireActiveProject(Project project) {
        if (!"ACTIVE".equals(project.status())) {
            throw new ConflictException("Project must be active.");
        }
    }

    private record Geometry(
            String type,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            BigDecimal area,
            BigDecimal volume
    ) {
    }
}
