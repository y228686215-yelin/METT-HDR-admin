package com.mett.hdr.audit.service;

import com.mett.hdr.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(Long actorUserId, String action, String resourceType, String resourceId, HttpServletRequest request) {
        auditLogRepository.append(
                actorUserId,
                action,
                resourceType,
                resourceId,
                "HDR",
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : sanitizeUserAgent(request.getHeader("User-Agent"))
        );
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String sanitized = userAgent.replaceAll("[\\r\\n]", "");
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }
}
