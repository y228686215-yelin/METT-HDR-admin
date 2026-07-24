package com.mett.hdr.payment.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.UnauthorizedException;
import com.mett.hdr.payment.dto.PaymentCallbackResponse;
import com.mett.hdr.payment.model.PaymentEventProcessingResult;
import com.mett.hdr.payment.provider.PaymentCallbackVerificationResult;
import com.mett.hdr.payment.provider.PaymentProvider;
import com.mett.hdr.payment.provider.PaymentProviderRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class PaymentCallbackService {

    private final PaymentProviderRegistry providerRegistry;
    private final PaymentEventProcessingService eventProcessingService;
    private final PaymentOrderFulfillmentService fulfillmentService;
    private final AuditService auditService;

    public PaymentCallbackService(
            PaymentProviderRegistry providerRegistry,
            PaymentEventProcessingService eventProcessingService,
            PaymentOrderFulfillmentService fulfillmentService,
            AuditService auditService
    ) {
        this.providerRegistry = providerRegistry;
        this.eventProcessingService = eventProcessingService;
        this.fulfillmentService = fulfillmentService;
        this.auditService = auditService;
    }

    public PaymentCallbackResponse process(
            String providerCode,
            String rawBody,
            String signature,
            HttpServletRequest request
    ) {
        PaymentProvider provider = providerRegistry.require(providerCode);
        PaymentCallbackVerificationResult verification =
                provider.verifyAndParseCallback(rawBody, signature);
        if (!verification.verified()) {
            auditService.record(
                    null,
                    "PAYMENT_CALLBACK_REJECT",
                    "PAYMENT_PROVIDER",
                    provider.code(),
                    request
            );
            if ("INVALID_SIGNATURE".equals(verification.rejectionCode())) {
                throw new UnauthorizedException("Invalid payment callback signature.");
            }
            throw new BadRequestException("Malformed payment callback.");
        }
        PaymentEventProcessingResult processed = eventProcessingService.process(
                provider.code(),
                verification.event(),
                digest(rawBody),
                request
        );
        if (processed.triggerFulfillment()) {
            fulfillmentService.fulfill(
                    processed.paymentOrderId(),
                    processed.paymentEventId(),
                    request
            );
        }
        return new PaymentCallbackResponse(
                "ACKNOWLEDGED",
                processed.duplicate(),
                processed.processingStatus()
        );
    }

    private String digest(String rawBody) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(
                    rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
