package com.mett.hdr.payment.dto;

import java.time.LocalDateTime;

public record PaymentOrderResponse(
        String globalOrderId,
        String orderNumber,
        String subjectType,
        String globalTeamId,
        String planCode,
        int planVersion,
        String tier,
        String offerCode,
        int offerVersion,
        long amountMinor,
        String currency,
        String orderStatus,
        String paymentStatus,
        String fulfillmentStatus,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
