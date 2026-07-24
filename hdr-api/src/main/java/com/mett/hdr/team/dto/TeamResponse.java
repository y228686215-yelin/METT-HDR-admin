package com.mett.hdr.team.dto;

public record TeamResponse(
        String globalTeamId,
        String name,
        String description,
        String status,
        String globalOrganizationId,
        String currentUserRole,
        String ownerGlobalUserId,
        String managerGlobalUserId
) {
}
