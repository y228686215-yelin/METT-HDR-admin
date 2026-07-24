package com.mett.hdr.payment.dto;

public record PaymentAttemptResponse(
        String globalPaymentAttemptId,
        String providerCode,
        String status,
        String nextActionType,
        String redirectUrl
) {
}
