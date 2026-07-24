package com.mett.hdr.membership.dto;

import java.time.LocalDateTime;

public record MembershipResponse(
        String subjectType,
        String planCode,
        int planVersion,
        String tier,
        String status,
        LocalDateTime periodStartAt,
        LocalDateTime periodEndAt
) {
}
