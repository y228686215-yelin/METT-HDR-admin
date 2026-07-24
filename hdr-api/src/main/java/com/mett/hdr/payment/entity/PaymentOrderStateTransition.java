package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentOrderStateTransition(
        Long id,
        Long paymentOrderId,
        String stateType,
        String fromState,
        String toState,
        String reason,
        Long actorUserId,
        Long paymentEventId,
        LocalDateTime createdAt
) {
}
