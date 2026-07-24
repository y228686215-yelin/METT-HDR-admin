package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record QuotaTransaction(
        Long id,
        Long membershipId,
        Long membershipUsageId,
        Long entitlementDefinitionId,
        String operationKey,
        String changeType,
        long amount,
        long usedBefore,
        long usedAfter,
        long reservedBefore,
        long reservedAfter,
        Long quotaLimitSnapshot,
        String reservationKey,
        String relatedObjectType,
        String relatedObjectId,
        String reason,
        Long actorUserId,
        LocalDateTime createdAt
) {
}
