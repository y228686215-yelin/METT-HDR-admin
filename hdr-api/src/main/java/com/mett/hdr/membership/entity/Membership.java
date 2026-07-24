package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record Membership(
        Long id,
        String subjectType,
        Long userId,
        Long teamId,
        Long membershipPlanId,
        String status,
        String source,
        Integer currentMarker,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodStartAt,
        LocalDateTime currentPeriodEndAt,
        LocalDateTime expiresAt,
        LocalDateTime suspendedAt,
        LocalDateTime cancelledAt,
        LocalDateTime replacedAt,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isCurrent() {
        return currentMarker != null && currentMarker == 1;
    }

    public boolean providesAccess() {
        return isCurrent() && "ACTIVE".equals(status);
    }
}
