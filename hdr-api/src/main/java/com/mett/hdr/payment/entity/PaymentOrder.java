package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentOrder(
        Long id,
        String globalOrderId,
        String orderNumber,
        String subjectType,
        Long userId,
        Long teamId,
        Long purchaserUserId,
        String orderStatus,
        String paymentStatus,
        String fulfillmentStatus,
        String currency,
        long totalAmountMinor,
        String requestIdempotencyKey,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        LocalDateTime fulfilledAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public Long subjectId() {
        return "USER".equals(subjectType) ? userId : teamId;
    }
}
