package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record MembershipPlanOffer(
        Long id,
        String globalOfferId,
        String offerCode,
        int version,
        Long membershipPlanId,
        String status,
        String purchaseMode,
        String currency,
        long amountMinor,
        int membershipDurationMonths,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean availableAt(LocalDateTime time) {
        return "ACTIVE".equals(status)
                && (validFrom == null || !time.isBefore(validFrom))
                && (validUntil == null || time.isBefore(validUntil));
    }
}
