package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record MembershipUsage(
        Long id,
        Long membershipId,
        Long entitlementDefinitionId,
        LocalDateTime cycleStartAt,
        LocalDateTime cycleEndAt,
        Long quotaLimitSnapshot,
        boolean unlimited,
        long usedCount,
        long reservedCount,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public Long remaining() {
        return unlimited ? null : quotaLimitSnapshot - usedCount - reservedCount;
    }
}
