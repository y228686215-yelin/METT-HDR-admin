package com.mett.hdr.organization.dto;

import java.time.LocalDateTime;

public record OrganizationMemberResponse(
        String globalUserId,
        String memberRole,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {
}
