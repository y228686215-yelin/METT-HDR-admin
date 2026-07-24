package com.mett.hdr.payment.provider;

public record PaymentCallbackVerificationResult(
        boolean verified,
        VerifiedPaymentEvent event,
        String rejectionCode
) {
    public static PaymentCallbackVerificationResult rejected(String code) {
        return new PaymentCallbackVerificationResult(false, null, code);
    }

    public static PaymentCallbackVerificationResult verified(VerifiedPaymentEvent event) {
        return new PaymentCallbackVerificationResult(true, event, null);
    }
}
