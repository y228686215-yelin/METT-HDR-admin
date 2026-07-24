package com.mett.hdr.audit.entity;

import java.time.LocalDateTime;

public record AuditLog(
        Long id,
        Long actorUserId,
        String action,
        String resourceType,
        String resourceId,
        String sourceSystem,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {
}
