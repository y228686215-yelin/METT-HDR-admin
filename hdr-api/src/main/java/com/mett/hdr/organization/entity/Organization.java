package com.mett.hdr.organization.entity;

import java.time.LocalDateTime;

public record Organization(
        Long id,
        String globalOrganizationId,
        String globalCompanyId,
        String name,
        String organizationType,
        String description,
        String status,
        Long ownerUserId,
        Long managedByUserId,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
