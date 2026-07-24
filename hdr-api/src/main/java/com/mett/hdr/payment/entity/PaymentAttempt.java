package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentAttempt(
        Long id,
        String globalPaymentAttemptId,
        Long paymentOrderId,
        String providerCode,
        String providerAttemptId,
        String providerTransactionId,
        String idempotencyKey,
        String status,
        long requestedAmountMinor,
        String requestedCurrency,
        String nextActionType,
        String providerReference,
        String failureCode,
        String failureMessage,
        LocalDateTime expiresAt,
        LocalDateTime succeededAt,
        LocalDateTime failedAt,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
