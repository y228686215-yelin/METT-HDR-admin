package com.mett.hdr.project.entity;

import java.time.LocalDateTime;

public record ProjectMember(
        Long id,
        Long projectId,
        Long userId,
        String role,
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
