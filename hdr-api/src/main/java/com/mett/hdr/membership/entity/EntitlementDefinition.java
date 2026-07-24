package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record EntitlementDefinition(
        Long id,
        String code,
        String name,
        String description,
        String entitlementType,
        String quotaUnit,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isQuota() {
        return "QUOTA".equals(entitlementType);
    }
}
