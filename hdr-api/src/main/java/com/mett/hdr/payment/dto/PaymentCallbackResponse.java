package com.mett.hdr.payment.dto;

public record PaymentCallbackResponse(
        String acknowledgement,
        boolean duplicate,
        String processingStatus
) {
}
