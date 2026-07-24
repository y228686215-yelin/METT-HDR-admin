package com.mett.hdr.payment.dto;

public record MembershipOfferResponse(
        String globalOfferId,
        String offerCode,
        int offerVersion,
        String planCode,
        int planVersion,
        String tier,
        String audienceType,
        long amountMinor,
        String currency,
        int membershipDurationMonths
) {
}
