package com.mett.hdr.team.entity;

import java.time.LocalDateTime;

public record Team(
        Long id,
        String globalTeamId,
        Long organizationId,
        String name,
        String description,
        String status,
        Long ownerUserId,
        Long managedByUserId,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
