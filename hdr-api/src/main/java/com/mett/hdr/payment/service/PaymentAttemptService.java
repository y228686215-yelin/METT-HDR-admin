package com.mett.hdr.payment.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.payment.dto.PaymentAttemptRequest;
import com.mett.hdr.payment.dto.PaymentAttemptResponse;
import com.mett.hdr.payment.entity.PaymentAttempt;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.provider.PaymentInitiationCommand;
import com.mett.hdr.payment.provider.PaymentInitiationResult;
import com.mett.hdr.payment.provider.PaymentProvider;
import com.mett.hdr.payment.provider.PaymentProviderRegistry;
import com.mett.hdr.payment.repository.PaymentAttemptRepository;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {

    private final CurrentActorService currentActorService;
    private final PaymentOrderRepository orderRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentOrderService orderService;
    private final PaymentPurchaseAuthorizationService authorizationService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentOrderStateMachine stateMachine;
    private final GlobalIdService globalIdService;
    private final AuditService auditService;

    public PaymentAttemptService(
            CurrentActorService currentActorService,
            PaymentOrderRepository orderRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentOrderService orderService,
            PaymentPurchaseAuthorizationService authorizationService,
            PaymentProviderRegistry providerRegistry,
            PaymentOrderStateMachine stateMachine,
            GlobalIdService globalIdService,
            AuditService auditService
    ) {
        this.currentActorService = currentActorService;
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.orderService = orderService;
        this.authorizationService = authorizationService;
        this.providerRegistry = providerRegistry;
        this.stateMachine = stateMachine;
        this.globalIdService = globalIdService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentAttemptResponse create(
            String authorization,
            String globalOrderId,
            String idempotencyKey,
            PaymentAttemptRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        String key = requireKey(idempotencyKey);
        String providerCode = normalizeProvider(request.providerCode());
        PaymentOrder order = orderRepository.lockByGlobalId(globalOrderId)
                .orElseThrow(() -> new NotFoundException("Payment order not found."));
        requireVisible(order, actor.userId());

        PaymentAttempt existing = attemptRepository.findByOrderAndKey(
                order.id(), key).orElse(null);
        if (existing != null) {
            if (!existing.providerCode().equals(providerCode)) {
                throw new ConflictException(
                        "Idempotency key conflicts with an existing payment attempt.");
            }
            return toResponse(existing);
        }

        order = orderService.expireIfNeeded(order, servletRequest);
        if (Set.of("CANCELLED", "EXPIRED", "PAID", "FULFILLED",
                "PAYMENT_REVIEW_REQUIRED").contains(order.orderStatus())
                || Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus())) {
            throw new ConflictException("Order cannot accept a payment attempt.");
        }
        PaymentProvider provider = providerRegistry.require(providerCode);
        PaymentAttempt attempt = attemptRepository.create(
                globalIdService.paymentAttemptId(),
                order.id(),
                provider.code(),
                key,
                order.totalAmountMinor(),
                order.currency(),
                actor.userId()
        );
        try {
            PaymentInitiationResult initiated = provider.initiatePayment(
                    new PaymentInitiationCommand(
                            order.globalOrderId(),
                            attempt.globalPaymentAttemptId(),
                            order.totalAmountMinor(),
                            order.currency()
                    ));
            if (!Set.of("ACTION_REQUIRED", "PROCESSING").contains(initiated.status())) {
                throw new IllegalStateException("Provider returned an invalid attempt status.");
            }
            attempt = attemptRepository.updateInitiated(
                    attempt.id(),
                    initiated.providerAttemptId(),
                    initiated.status(),
                    initiated.nextActionType(),
                    initiated.providerReference()
            );
            if (!"PAYMENT_PENDING".equals(order.orderStatus())) {
                order = stateMachine.transitionOrder(
                        order, "PAYMENT_PENDING", "PAYMENT_ATTEMPT_CREATED",
                        actor.userId(), null);
            }
            if (!"PROCESSING".equals(order.paymentStatus())) {
                stateMachine.transitionPayment(
                        order, "PROCESSING", "PAYMENT_ATTEMPT_CREATED",
                        actor.userId(), null);
            }
            auditService.record(
                    actor.userId(),
                    "PAYMENT_ATTEMPT_CREATE",
                    "PAYMENT_ORDER",
                    order.globalOrderId(),
                    servletRequest
            );
            return toResponse(attempt);
        } catch (RuntimeException ex) {
            attempt = attemptRepository.updateStatus(
                    attempt.id(), "FAILED", null,
                    "PROVIDER_INITIATION_FAILED", "Payment initiation failed.",
                    null, now());
            if (!"FAILED".equals(order.paymentStatus())) {
                order = stateMachine.transitionPayment(
                        order, "FAILED", "PAYMENT_ATTEMPT_FAILED",
                        actor.userId(), null);
            }
            if (!"PAYMENT_FAILED".equals(order.orderStatus())) {
                stateMachine.transitionOrder(
                        order, "PAYMENT_FAILED", "PAYMENT_ATTEMPT_FAILED",
                        actor.userId(), null);
            }
            auditService.record(
                    actor.userId(),
                    "PAYMENT_ATTEMPT_FAILED",
                    "PAYMENT_ORDER",
                    order.globalOrderId(),
                    servletRequest
            );
            return toResponse(attempt);
        }
    }

    private PaymentAttemptResponse toResponse(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.globalPaymentAttemptId(),
                attempt.providerCode(),
                attempt.status(),
                attempt.nextActionType(),
                null
        );
    }

    private void requireVisible(PaymentOrder order, Long userId) {
        if (!order.purchaserUserId().equals(userId)
                || ("TEAM".equals(order.subjectType())
                && !authorizationService.canViewTeamOrder(order.teamId(), userId))) {
            throw new NotFoundException("Payment order not found.");
        }
    }

    private String requireKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 128) {
            throw new BadRequestException("Valid Idempotency-Key is required.");
        }
        return value.trim();
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Payment provider is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
