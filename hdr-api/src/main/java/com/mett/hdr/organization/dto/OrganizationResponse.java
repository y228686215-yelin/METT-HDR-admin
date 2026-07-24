package com.mett.hdr.organization.dto;

public record OrganizationResponse(
        String globalOrganizationId,
        String globalCompanyId,
        String name,
        String organizationType,
        String description,
        String status,
        String currentUserRole,
        String ownerGlobalUserId,
        String managerGlobalUserId
) {
}
