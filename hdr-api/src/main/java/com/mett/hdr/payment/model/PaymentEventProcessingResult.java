package com.mett.hdr.payment.model;

public record PaymentEventProcessingResult(
        boolean duplicate,
        String processingStatus,
        Long paymentEventId,
        Long paymentOrderId,
        boolean triggerFulfillment
) {
}
