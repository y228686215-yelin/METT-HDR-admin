package com.mett.hdr.organization.entity;

import java.time.LocalDateTime;

public record OrganizationMember(
        Long id,
        Long organizationId,
        Long userId,
        String memberRole,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
