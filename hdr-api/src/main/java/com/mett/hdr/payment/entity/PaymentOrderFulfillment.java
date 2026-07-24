package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentOrderFulfillment(
        Long id,
        Long paymentOrderId,
        String fulfillmentType,
        String status,
        String operationKey,
        int attemptCount,
        Long fulfilledMembershipId,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
