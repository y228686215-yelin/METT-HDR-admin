package com.mett.hdr.project.dto;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        String globalUserId,
        String memberRole,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {
}
