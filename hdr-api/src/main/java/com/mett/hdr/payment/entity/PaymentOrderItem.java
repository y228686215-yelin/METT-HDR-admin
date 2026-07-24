package com.mett.hdr.payment.entity;

import java.time.LocalDateTime;

public record PaymentOrderItem(
        Long id,
        Long paymentOrderId,
        String itemType,
        Long membershipPlanOfferId,
        Long membershipPlanId,
        int quantity,
        String currency,
        long unitAmountMinor,
        long totalAmountMinor,
        String planCodeSnapshot,
        int planVersionSnapshot,
        String tierSnapshot,
        String offerCodeSnapshot,
        int offerVersionSnapshot,
        int membershipDurationMonths,
        String itemSnapshot,
        LocalDateTime createdAt
) {
}
