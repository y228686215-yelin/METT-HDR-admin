package com.mett.hdr.identity.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.identity.entity.UserIdentityLink;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class IdentityLinkService {

    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final AuditService auditService;

    public IdentityLinkService(UserIdentityLinkRepository userIdentityLinkRepository, AuditService auditService) {
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.auditService = auditService;
    }

    public UserIdentityLink link(
            Long actorUserId,
            String globalUserId,
            String sourceSystem,
            String externalUserId,
            String externalGlobalUserId,
            String identityType,
            HttpServletRequest request
    ) {
        userIdentityLinkRepository.findBySourceAndExternalUserId(sourceSystem, externalUserId)
                .ifPresent(existing -> {
                    throw new ConflictException("External identity is already linked.");
                });
        UserIdentityLink link = new UserIdentityLink();
        link.setGlobalUserId(globalUserId);
        link.setSourceSystem(sourceSystem);
        link.setExternalUserId(externalUserId);
        link.setExternalGlobalUserId(externalGlobalUserId);
        link.setIdentityType(identityType);
        link.setStatus("ACTIVE");
        UserIdentityLink saved = userIdentityLinkRepository.save(link);
        auditService.record(actorUserId, "IDENTITY_BIND", "USER_IDENTITY_LINK", saved.getId().toString(), request);
        return saved;
    }
}
