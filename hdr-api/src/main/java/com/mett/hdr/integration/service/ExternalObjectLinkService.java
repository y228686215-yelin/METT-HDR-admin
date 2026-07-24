package com.mett.hdr.integration.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.integration.entity.ExternalObjectLink;
import com.mett.hdr.integration.repository.ExternalObjectLinkRepository;
import com.mett.hdr.project.entity.Project;
import com.mett.hdr.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalObjectLinkService {

    private static final Set<String> RELATION_TYPES = Set.of("SOURCE", "MIRROR", "REFERENCE");

    private final ExternalObjectLinkRepository linkRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public ExternalObjectLinkService(
            ExternalObjectLinkRepository linkRepository,
            ProjectRepository projectRepository,
            AuditService auditService
    ) {
        this.linkRepository = linkRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ExternalObjectLink createLink(
            Long actorUserId,
            String localProjectGlobalId,
            String externalSystem,
            String externalObjectType,
            String externalObjectId,
            String externalGlobalId,
            String relationType,
            HttpServletRequest request
    ) {
        Project project = projectRepository.findByGlobalId(localProjectGlobalId)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        String system = requireText(externalSystem, "External system is required.");
        String objectType = requireText(externalObjectType, "External object type is required.");
        String relation = upper(relationType);
        if (!RELATION_TYPES.contains(relation)) {
            throw new BadRequestException("Invalid external object relation type.");
        }
        if (!hasText(externalObjectId) && !hasText(externalGlobalId)) {
            throw new BadRequestException("An external object identifier is required.");
        }
        if (linkRepository.find("PROJECT", localProjectGlobalId, system, objectType, relation).isPresent()) {
            throw new ConflictException("External object link already exists.");
        }
        ExternalObjectLink link = linkRepository.create(
                "PROJECT",
                project.id(),
                project.globalProjectId(),
                system,
                objectType,
                trimToNull(externalObjectId),
                trimToNull(externalGlobalId),
                relation
        );
        auditService.record(actorUserId, "EXTERNAL_OBJECT_LINK_CREATE", "PROJECT",
                project.globalProjectId(), request);
        return link;
    }

    public List<ExternalObjectLink> findByLocalGlobalId(String localGlobalId) {
        return linkRepository.findByLocalGlobalId(localGlobalId);
    }

    public List<ExternalObjectLink> findByExternalGlobalId(
            String externalSystem,
            String externalGlobalId
    ) {
        return linkRepository.findByExternalGlobalId(externalSystem, externalGlobalId);
    }

    @Transactional
    public ExternalObjectLink deactivateLink(
            Long actorUserId,
            Long linkId,
            HttpServletRequest request
    ) {
        ExternalObjectLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new NotFoundException("External object link not found."));
        if ("INACTIVE".equals(link.status())) {
            throw new ConflictException("External object link is already inactive.");
        }
        ExternalObjectLink updated = linkRepository.updateStatus(link.id(), "INACTIVE");
        auditService.record(actorUserId, "EXTERNAL_OBJECT_LINK_DEACTIVATE", "PROJECT",
                link.localGlobalId(), request);
        return updated;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
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
