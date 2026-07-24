package com.mett.hdr.payment.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.service.MembershipProvisioningService;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.entity.PaymentOrderFulfillment;
import com.mett.hdr.payment.entity.PaymentOrderItem;
import com.mett.hdr.payment.model.PurchaseSubject;
import com.mett.hdr.payment.repository.PaymentOrderFulfillmentRepository;
import com.mett.hdr.payment.repository.PaymentOrderItemRepository;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentOrderFulfillmentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderItemRepository itemRepository;
    private final PaymentOrderFulfillmentRepository fulfillmentRepository;
    private final MembershipPlanRepository planRepository;
    private final PaymentPurchaseAuthorizationService authorizationService;
    private final MembershipProvisioningService membershipProvisioningService;
    private final PaymentOrderStateMachine stateMachine;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public PaymentOrderFulfillmentService(
            PaymentOrderRepository orderRepository,
            PaymentOrderItemRepository itemRepository,
            PaymentOrderFulfillmentRepository fulfillmentRepository,
            MembershipPlanRepository planRepository,
            PaymentPurchaseAuthorizationService authorizationService,
            MembershipProvisioningService membershipProvisioningService,
            PaymentOrderStateMachine stateMachine,
            AuditService auditService,
            PlatformTransactionManager transactionManager
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.planRepository = planRepository;
        this.authorizationService = authorizationService;
        this.membershipProvisioningService = membershipProvisioningService;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void fulfill(
            Long orderId,
            Long paymentEventId,
            HttpServletRequest request
    ) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    fulfillInTransaction(orderId, paymentEventId, request));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status ->
                    recordFailure(orderId, paymentEventId, request));
        }
    }

    public void retry(
            Long orderId,
            Long actorUserId,
            HttpServletRequest request
    ) {
        PaymentOrder order = orderRepository.findById(orderId).orElseThrow();
        if (!"PAID".equals(order.paymentStatus())) {
            throw new ConflictException("Only a paid order may retry fulfillment.");
        }
        auditService.record(
                actorUserId,
                "PAYMENT_FULFILLMENT_RETRY",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                request
        );
        fulfill(orderId, null, request);
    }

    private void fulfillInTransaction(
            Long orderId,
            Long paymentEventId,
            HttpServletRequest request
    ) {
        PaymentOrder order = orderRepository.lockById(orderId).orElseThrow();
        if ("FULFILLED".equals(order.fulfillmentStatus())
                || "FULFILLED".equals(order.orderStatus())) {
            return;
        }
        if (!"PAID".equals(order.paymentStatus())
                || !"PAID".equals(order.orderStatus())) {
            throw new ConflictException("Order is not ready for fulfillment.");
        }
        PaymentOrderItem item = itemRepository.findByOrderId(order.id()).orElseThrow();
        MembershipPlan plan = planRepository.findById(
                item.membershipPlanId()).orElseThrow();
        PurchaseSubject subject = new PurchaseSubject(
                order.subjectType(),
                order.userId(),
                order.teamId(),
                null
        );
        authorizationService.requireEligible(subject, plan);

        PaymentOrderFulfillment fulfillment = fulfillmentRepository
                .lockByOrderId(order.id()).orElse(null);
        if (fulfillment == null) {
            fulfillment = fulfillmentRepository.create(
                    order.id(), "payment-fulfillment:" + order.globalOrderId());
        }
        if ("FULFILLED".equals(fulfillment.status())) {
            return;
        }
        fulfillment = fulfillmentRepository.markProcessing(
                fulfillment.id(), now());
        order = stateMachine.transitionFulfillment(
                order, "PROCESSING", "PAYMENT_FULFILLMENT_START",
                null, paymentEventId);
        auditService.record(
                null,
                "PAYMENT_FULFILLMENT_START",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                request
        );

        Membership membership;
        if ("USER".equals(order.subjectType())) {
            membership = membershipProvisioningService.activatePurchasedUserPlan(
                    order.userId(),
                    item.planCodeSnapshot(),
                    item.planVersionSnapshot(),
                    item.membershipDurationMonths(),
                    order.purchaserUserId(),
                    request
            );
        } else {
            membership = membershipProvisioningService.activatePurchasedTeamPlan(
                    order.teamId(),
                    item.planCodeSnapshot(),
                    item.planVersionSnapshot(),
                    item.membershipDurationMonths(),
                    order.purchaserUserId(),
                    request
            );
        }
        fulfillmentRepository.markFulfilled(
                fulfillment.id(), membership.id(), now());
        order = stateMachine.transitionFulfillment(
                order, "FULFILLED", "PAYMENT_FULFILLMENT_SUCCESS",
                null, paymentEventId);
        order = stateMachine.transitionOrder(
                order, "FULFILLED", "PAYMENT_FULFILLMENT_SUCCESS",
                null, paymentEventId);
        auditService.record(
                null,
                "PAYMENT_FULFILLMENT_SUCCESS",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                request
        );
    }

    private void recordFailure(
            Long orderId,
            Long paymentEventId,
            HttpServletRequest request
    ) {
        PaymentOrder order = orderRepository.lockById(orderId).orElseThrow();
        if ("FULFILLED".equals(order.fulfillmentStatus())) {
            return;
        }
        PaymentOrderFulfillment fulfillment = fulfillmentRepository
                .lockByOrderId(order.id()).orElse(null);
        if (fulfillment == null) {
            fulfillment = fulfillmentRepository.create(
                    order.id(), "payment-fulfillment:" + order.globalOrderId());
        }
        fulfillment = fulfillmentRepository.markProcessing(
                fulfillment.id(), now());
        if (!"PROCESSING".equals(order.fulfillmentStatus())) {
            order = stateMachine.transitionFulfillment(
                    order, "PROCESSING", "PAYMENT_FULFILLMENT_START",
                    null, paymentEventId);
        }
        fulfillmentRepository.markFailed(
                fulfillment.id(),
                "FULFILLMENT_ERROR",
                "Membership fulfillment failed.",
                now()
        );
        stateMachine.transitionFulfillment(
                order, "FAILED", "PAYMENT_FULFILLMENT_FAILED",
                null, paymentEventId);
        auditService.record(
                null,
                "PAYMENT_FULFILLMENT_FAILED",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                request
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
