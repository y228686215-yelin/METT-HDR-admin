package com.mett.hdr.membership.model;

public record QuotaCommand(
        MembershipSubject subject,
        String entitlementCode,
        long amount,
        String operationKey,
        String reservationKey,
        String relatedObjectType,
        String relatedObjectId,
        String reason,
        Long actorUserId
) {
    public static QuotaCommand of(
            MembershipSubject subject,
            String entitlementCode,
            long amount,
            String operationKey
    ) {
        return new QuotaCommand(
                subject, entitlementCode, amount, operationKey,
                null, null, null, null, null);
    }

    public QuotaCommand withReservationKey(String value) {
        return new QuotaCommand(
                subject, entitlementCode, amount, operationKey,
                value, relatedObjectType, relatedObjectId, reason, actorUserId);
    }
}
