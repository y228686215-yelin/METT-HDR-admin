package com.mett.hdr.payment.provider;

import java.time.LocalDateTime;

public record VerifiedPaymentEvent(
        String providerEventId,
        String eventType,
        String providerAttemptId,
        String providerTransactionId,
        String globalOrderId,
        String paymentStatus,
        long paidAmountMinor,
        String currency,
        LocalDateTime providerEventTime
) {
}
