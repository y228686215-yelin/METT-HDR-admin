package com.mett.hdr.project.dto;

import java.time.LocalDateTime;

public record ProjectResponse(
        String globalProjectId,
        String projectNumber,
        String name,
        String projectType,
        String description,
        String status,
        String originSystem,
        String ownershipScope,
        String globalTeamId,
        String globalOrganizationId,
        String ownerGlobalUserId,
        String managerGlobalUserId,
        String currentUserRole,
        String city,
        String timezone,
        LocalDateTime activatedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
