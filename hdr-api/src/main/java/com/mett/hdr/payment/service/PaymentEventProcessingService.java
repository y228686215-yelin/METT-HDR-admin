package com.mett.hdr.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.SystemException;
import com.mett.hdr.payment.entity.PaymentAttempt;
import com.mett.hdr.payment.entity.PaymentEvent;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.model.PaymentEventProcessingResult;
import com.mett.hdr.payment.provider.VerifiedPaymentEvent;
import com.mett.hdr.payment.repository.PaymentAttemptRepository;
import com.mett.hdr.payment.repository.PaymentEventRepository;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventProcessingService {

    private final PaymentEventRepository eventRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderStateMachine stateMachine;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentEventProcessingService(
            PaymentEventRepository eventRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentOrderRepository orderRepository,
            PaymentOrderStateMachine stateMachine,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.attemptRepository = attemptRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentEventProcessingResult process(
            String providerCode,
            VerifiedPaymentEvent verified,
            String payloadDigest,
            HttpServletRequest request
    ) {
        PaymentEvent duplicate = eventRepository.findByProviderEvent(
                providerCode, verified.providerEventId()).orElse(null);
        if (duplicate != null) {
            auditService.record(
                    null, "PAYMENT_CALLBACK_DUPLICATE", "PAYMENT_EVENT",
                    providerCode + ":" + verified.providerEventId(), request);
            return new PaymentEventProcessingResult(
                    true,
                    duplicate.processingStatus(),
                    duplicate.id(),
                    duplicate.paymentOrderId(),
                    false
            );
        }

        PaymentAttempt attempt = attemptRepository.lockByProviderAttempt(
                providerCode, verified.providerAttemptId()).orElse(null);
        PaymentOrder order = attempt == null
                ? orderRepository.findByGlobalId(verified.globalOrderId()).orElse(null)
                : orderRepository.lockById(attempt.paymentOrderId()).orElse(null);
        PaymentEvent event = eventRepository.create(
                providerCode,
                verified.providerEventId(),
                verified.eventType(),
                payloadDigest,
                "RECEIVED",
                order == null ? null : order.id(),
                attempt == null ? null : attempt.id(),
                verified.providerTransactionId(),
                sanitized(verified),
                null,
                null,
                now(),
                null
        );
        auditService.record(
                null, "PAYMENT_CALLBACK_RECEIVE", "PAYMENT_EVENT",
                providerCode + ":" + verified.providerEventId(), request);

        if (attempt == null || order == null
                || !order.globalOrderId().equals(verified.globalOrderId())) {
            return review(
                    event, order, "PAYMENT_REFERENCE_MISMATCH",
                    "Verified payment references do not match.", request);
        }

        if ("PENDING".equals(verified.paymentStatus())) {
            if (!"SUCCEEDED".equals(attempt.status())
                    && !"FAILED".equals(attempt.status())) {
                attemptRepository.updateStatus(
                        attempt.id(), "PROCESSING", null,
                        null, null, null, null);
            }
            eventRepository.updateProcessing(
                    event.id(), "PROCESSED", null, null, now());
            return result(event, order, "PROCESSED", false);
        }

        if ("FAILED".equals(verified.paymentStatus())) {
            attemptRepository.updateStatus(
                    attempt.id(), "FAILED", verified.providerTransactionId(),
                    "PROVIDER_PAYMENT_FAILED", "Provider reported payment failure.",
                    null, now());
            if ("PROCESSING".equals(order.paymentStatus())) {
                order = stateMachine.transitionPayment(
                        order, "FAILED", "PROVIDER_PAYMENT_FAILED",
                        null, event.id());
            }
            if ("PAYMENT_PENDING".equals(order.orderStatus())) {
                order = stateMachine.transitionOrder(
                        order, "PAYMENT_FAILED", "PROVIDER_PAYMENT_FAILED",
                        null, event.id());
            }
            eventRepository.updateProcessing(
                    event.id(), "PROCESSED", null, null, now());
            auditService.record(
                    null, "PAYMENT_FAILED", "PAYMENT_ORDER",
                    order.globalOrderId(), request);
            return result(event, order, "PROCESSED", false);
        }

        PaymentAttempt transactionOwner = verified.providerTransactionId() == null
                ? null
                : attemptRepository.findByProviderTransaction(
                providerCode, verified.providerTransactionId()).orElse(null);
        if (transactionOwner != null && !transactionOwner.id().equals(attempt.id())) {
            return review(
                    event, order, "PROVIDER_TRANSACTION_CONFLICT",
                    "Provider transaction is already associated with another attempt.",
                    request);
        }
        if (verified.paidAmountMinor() != attempt.requestedAmountMinor()
                || verified.paidAmountMinor() != order.totalAmountMinor()) {
            return review(
                    event, order, "PAYMENT_AMOUNT_MISMATCH",
                    "Verified payment amount does not match the order.",
                    request);
        }
        if (!attempt.requestedCurrency().equals(verified.currency())
                || !order.currency().equals(verified.currency())) {
            return review(
                    event, order, "PAYMENT_CURRENCY_MISMATCH",
                    "Verified payment currency does not match the order.",
                    request);
        }
        if (java.util.Set.of("CANCELLED", "EXPIRED")
                .contains(order.orderStatus())
                || !now().isBefore(order.expiresAt())) {
            if (!java.util.Set.of("CANCELLED", "EXPIRED")
                    .contains(order.orderStatus())) {
                order = stateMachine.transitionOrder(
                        order, "EXPIRED", "ORDER_EXPIRED_BEFORE_PAYMENT",
                        null, event.id());
            }
            return review(
                    event, order, "INACTIVE_ORDER_PAYMENT",
                    "Verified success was received for an inactive order.",
                    request);
        }
        if ("PAID".equals(order.paymentStatus())
                || "FULFILLED".equals(order.orderStatus())) {
            eventRepository.updateProcessing(
                    event.id(), "IGNORED", null, null, now());
            return result(event, order, "IGNORED", false);
        }

        attemptRepository.updateStatus(
                attempt.id(), "SUCCEEDED", verified.providerTransactionId(),
                null, null, now(), null);
        order = stateMachine.transitionPayment(
                order, "PAID", "VERIFIED_PROVIDER_SUCCESS",
                null, event.id());
        order = stateMachine.transitionOrder(
                order, "PAID", "VERIFIED_PROVIDER_SUCCESS",
                null, event.id());
        eventRepository.updateProcessing(
                event.id(), "PROCESSED", null, null, now());
        auditService.record(
                null, "PAYMENT_VERIFIED", "PAYMENT_ORDER",
                order.globalOrderId(), request);
        return result(event, order, "PROCESSED", true);
    }

    private PaymentEventProcessingResult review(
            PaymentEvent event,
            PaymentOrder order,
            String code,
            String message,
            HttpServletRequest request
    ) {
        if (order != null) {
            if (!java.util.Set.of("PAID", "REVIEW_REQUIRED")
                    .contains(order.paymentStatus())) {
                order = stateMachine.transitionPayment(
                        order, "REVIEW_REQUIRED", code, null, event.id());
            }
            if (!java.util.Set.of(
                    "FULFILLED", "PAID", "PAYMENT_REVIEW_REQUIRED")
                    .contains(order.orderStatus())) {
                order = stateMachine.transitionOrder(
                        order, "PAYMENT_REVIEW_REQUIRED",
                        code, null, event.id());
            }
        }
        eventRepository.updateProcessing(
                event.id(), "REVIEW_REQUIRED", code, message, now());
        auditService.record(
                null, "PAYMENT_REVIEW_REQUIRED", "PAYMENT_ORDER",
                order == null ? event.providerCode() + ":" + event.providerEventId()
                        : order.globalOrderId(),
                request);
        return new PaymentEventProcessingResult(
                false, "REVIEW_REQUIRED", event.id(),
                order == null ? null : order.id(), false);
    }

    private PaymentEventProcessingResult result(
            PaymentEvent event,
            PaymentOrder order,
            String status,
            boolean triggerFulfillment
    ) {
        return new PaymentEventProcessingResult(
                false, status, event.id(), order.id(), triggerFulfillment);
    }

    private String sanitized(VerifiedPaymentEvent event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("paymentStatus", event.paymentStatus());
        value.put("paidAmountMinor", event.paidAmountMinor());
        value.put("currency", event.currency());
        value.put("providerEventTime", event.providerEventTime());
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new SystemException("Unable to sanitize payment event.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
