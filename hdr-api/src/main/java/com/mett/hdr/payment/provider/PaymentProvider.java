package com.mett.hdr.payment.provider;

public interface PaymentProvider {

    String code();

    PaymentInitiationResult initiatePayment(PaymentInitiationCommand command);

    PaymentCallbackVerificationResult verifyAndParseCallback(
            String rawBody,
            String signature
    );
}
