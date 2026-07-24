package com.mett.hdr.payment.model;

public record PurchaseSubject(
        String subjectType,
        Long userId,
        Long teamId,
        String globalTeamId
) {
    public Long subjectId() {
        return "USER".equals(subjectType) ? userId : teamId;
    }
}
