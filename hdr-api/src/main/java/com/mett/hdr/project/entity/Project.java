package com.mett.hdr.project.entity;

import java.time.LocalDateTime;

public record Project(
        Long id,
        String globalProjectId,
        String projectNumber,
        String name,
        String projectType,
        String description,
        String status,
        String originSystem,
        String city,
        String timezone,
        Long ownerUserId,
        Long teamId,
        Long organizationId,
        Long createdByUserId,
        Long managedByUserId,
        LocalDateTime activatedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
