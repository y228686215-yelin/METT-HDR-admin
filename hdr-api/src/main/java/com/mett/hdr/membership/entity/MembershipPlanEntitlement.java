package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record MembershipPlanEntitlement(
        Long id,
        Long membershipPlanId,
        Long entitlementDefinitionId,
        boolean enabled,
        Long quotaLimit,
        boolean unlimited,
        String entitlementCode,
        String entitlementType,
        String entitlementStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean available() {
        return enabled && "ACTIVE".equals(entitlementStatus);
    }
}
