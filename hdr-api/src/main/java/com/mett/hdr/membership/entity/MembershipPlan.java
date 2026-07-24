package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record MembershipPlan(
        Long id,
        String planCode,
        int version,
        String name,
        String audienceType,
        String tier,
        String usageCycle,
        String status,
        boolean defaultPlan,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
