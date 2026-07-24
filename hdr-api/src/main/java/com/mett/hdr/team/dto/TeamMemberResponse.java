package com.mett.hdr.team.dto;

import java.time.LocalDateTime;

public record TeamMemberResponse(
        String globalUserId,
        String memberRole,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {
}
