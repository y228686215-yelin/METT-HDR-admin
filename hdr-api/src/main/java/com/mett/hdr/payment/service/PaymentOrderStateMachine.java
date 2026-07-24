package com.mett.hdr.payment.service;

import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import com.mett.hdr.payment.repository.PaymentOrderStateTransitionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrderStateMachine {

    private static final Map<String, Set<String>> ORDER_TRANSITIONS = Map.of(
            "CREATED", Set.of(
                    "PAYMENT_PENDING", "CANCELLED", "EXPIRED",
                    "PAYMENT_REVIEW_REQUIRED"),
            "PAYMENT_PENDING", Set.of(
                    "PAID", "PAYMENT_FAILED", "CANCELLED", "EXPIRED",
                    "PAYMENT_REVIEW_REQUIRED"),
            "PAYMENT_FAILED", Set.of(
                    "PAYMENT_PENDING", "PAID", "PAYMENT_REVIEW_REQUIRED"),
            "CANCELLED", Set.of("PAYMENT_REVIEW_REQUIRED"),
            "EXPIRED", Set.of("PAYMENT_REVIEW_REQUIRED"),
            "PAID", Set.of("FULFILLED"),
            "PAYMENT_REVIEW_REQUIRED", Set.of(),
            "FULFILLED", Set.of()
    );

    private static final Map<String, Set<String>> PAYMENT_TRANSITIONS = Map.of(
            "UNPAID", Set.of("PROCESSING", "FAILED", "REVIEW_REQUIRED"),
            "PROCESSING", Set.of("PAID", "FAILED", "REVIEW_REQUIRED"),
            "FAILED", Set.of("PROCESSING", "PAID", "REVIEW_REQUIRED"),
            "PAID", Set.of(),
            "REVIEW_REQUIRED", Set.of()
    );

    private static final Map<String, Set<String>> FULFILLMENT_TRANSITIONS = Map.of(
            "NOT_STARTED", Set.of("PROCESSING", "FAILED"),
            "PROCESSING", Set.of("FULFILLED", "FAILED"),
            "FAILED", Set.of("PROCESSING"),
            "FULFILLED", Set.of()
    );

    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderStateTransitionRepository transitionRepository;

    public PaymentOrderStateMachine(
            PaymentOrderRepository orderRepository,
            PaymentOrderStateTransitionRepository transitionRepository
    ) {
        this.orderRepository = orderRepository;
        this.transitionRepository = transitionRepository;
    }

    public void recordInitial(PaymentOrder order, Long actorUserId) {
        transitionRepository.create(
                order.id(), "ORDER", null, order.orderStatus(),
                "ORDER_CREATED", actorUserId, null);
        transitionRepository.create(
                order.id(), "PAYMENT", null, order.paymentStatus(),
                "ORDER_CREATED", actorUserId, null);
        transitionRepository.create(
                order.id(), "FULFILLMENT", null, order.fulfillmentStatus(),
                "ORDER_CREATED", actorUserId, null);
    }

    public PaymentOrder transitionOrder(
            PaymentOrder order,
            String toState,
            String reason,
            Long actorUserId,
            Long eventId
    ) {
        requireTransition(
                ORDER_TRANSITIONS, order.orderStatus(), toState, "order");
        LocalDateTime now = now();
        PaymentOrder updated = orderRepository.updateOrderStatus(
                order.id(),
                toState,
                "PAID".equals(toState) ? now : null,
                "FULFILLED".equals(toState) ? now : null,
                "CANCELLED".equals(toState) ? now : null
        );
        transitionRepository.create(
                order.id(), "ORDER", order.orderStatus(), toState,
                reason, actorUserId, eventId);
        return updated;
    }

    public PaymentOrder transitionPayment(
            PaymentOrder order,
            String toState,
            String reason,
            Long actorUserId,
            Long eventId
    ) {
        requireTransition(
                PAYMENT_TRANSITIONS, order.paymentStatus(), toState, "payment");
        PaymentOrder updated = orderRepository.updatePaymentStatus(
                order.id(),
                toState,
                "PAID".equals(toState) ? now() : null
        );
        transitionRepository.create(
                order.id(), "PAYMENT", order.paymentStatus(), toState,
                reason, actorUserId, eventId);
        return updated;
    }

    public PaymentOrder transitionFulfillment(
            PaymentOrder order,
            String toState,
            String reason,
            Long actorUserId,
            Long eventId
    ) {
        requireTransition(
                FULFILLMENT_TRANSITIONS,
                order.fulfillmentStatus(),
                toState,
                "fulfillment"
        );
        PaymentOrder updated = orderRepository.updateFulfillmentStatus(
                order.id(),
                toState,
                "FULFILLED".equals(toState) ? now() : null
        );
        transitionRepository.create(
                order.id(), "FULFILLMENT", order.fulfillmentStatus(), toState,
                reason, actorUserId, eventId);
        return updated;
    }

    private void requireTransition(
            Map<String, Set<String>> transitions,
            String from,
            String to,
            String type
    ) {
        if (from.equals(to)) {
            return;
        }
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException(
                    "Invalid " + type + " state transition.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
