package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentEvent(
        Long id,
        String providerCode,
        String providerEventId,
        String eventType,
        String payloadDigest,
        String processingStatus,
        Long paymentOrderId,
        Long paymentAttemptId,
        String providerTransactionId,
        String sanitizedEventData,
        String errorCode,
        String errorMessage,
        LocalDateTime receivedAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
}
