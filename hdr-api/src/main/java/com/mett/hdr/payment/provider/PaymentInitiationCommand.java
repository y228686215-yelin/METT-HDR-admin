package com.mett.hdr.payment.provider;

public record PaymentInitiationCommand(
        String globalOrderId,
        String globalPaymentAttemptId,
        long amountMinor,
        String currency
) {
}
