package com.mett.hdr.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "mett.hdr.payment.local-test.enabled",
        havingValue = "true"
)
public class LocalTestPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_CODE = "LOCAL_TEST";

    private final ObjectMapper objectMapper;
    private final byte[] signingSecret;

    public LocalTestPaymentProvider(
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${mett.hdr.payment.local-test.signing-secret:}") String signingSecret
    ) {
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new IllegalStateException(
                    "LOCAL_TEST payment provider is restricted to local and test profiles.");
        }
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException(
                    "LOCAL_TEST payment signing secret must be provided.");
        }
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String code() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentInitiationResult initiatePayment(PaymentInitiationCommand command) {
        return new PaymentInitiationResult(
                "local_attempt_" + command.globalPaymentAttemptId(),
                "ACTION_REQUIRED",
                "LOCAL_TEST_CALLBACK",
                "local_order_" + command.globalOrderId()
        );
    }

    @Override
    public PaymentCallbackVerificationResult verifyAndParseCallback(
            String rawBody,
            String signature
    ) {
        if (rawBody == null || signature == null || signature.isBlank()) {
            return PaymentCallbackVerificationResult.rejected("INVALID_SIGNATURE");
        }
        byte[] expected = hmac(rawBody);
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException ex) {
            return PaymentCallbackVerificationResult.rejected("INVALID_SIGNATURE");
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            return PaymentCallbackVerificationResult.rejected("INVALID_SIGNATURE");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String status = required(root, "paymentStatus").toUpperCase(Locale.ROOT);
            if (!java.util.Set.of("PENDING", "SUCCEEDED", "FAILED").contains(status)) {
                return PaymentCallbackVerificationResult.rejected("MALFORMED_CALLBACK");
            }
            if (!root.has("paidAmountMinor")
                    || !root.get("paidAmountMinor").isIntegralNumber()) {
                return PaymentCallbackVerificationResult.rejected("MALFORMED_CALLBACK");
            }
            VerifiedPaymentEvent event = new VerifiedPaymentEvent(
                    required(root, "providerEventId"),
                    required(root, "eventType"),
                    required(root, "providerAttemptId"),
                    text(root, "providerTransactionId"),
                    required(root, "globalOrderId"),
                    status,
                    root.path("paidAmountMinor").longValue(),
                    required(root, "currency").toUpperCase(Locale.ROOT),
                    LocalDateTime.parse(required(root, "providerEventTime"))
            );
            if (event.paidAmountMinor() < 0) {
                return PaymentCallbackVerificationResult.rejected("MALFORMED_CALLBACK");
            }
            return PaymentCallbackVerificationResult.verified(event);
        } catch (RuntimeException | java.io.IOException ex) {
            return PaymentCallbackVerificationResult.rejected("MALFORMED_CALLBACK");
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to verify local payment callback.", ex);
        }
    }

    private String required(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing callback field.");
        }
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
