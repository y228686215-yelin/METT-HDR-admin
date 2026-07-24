package com.mett.hdr.payment.provider;

public record PaymentInitiationResult(
        String providerAttemptId,
        String status,
        String nextActionType,
        String providerReference
) {
}
