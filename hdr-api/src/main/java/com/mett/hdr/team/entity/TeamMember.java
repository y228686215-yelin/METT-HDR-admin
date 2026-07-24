package com.mett.hdr.team.entity;

import java.time.LocalDateTime;

public record TeamMember(
        Long id,
        Long teamId,
        Long userId,
        String memberRole,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
