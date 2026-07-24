package com.mett.hdr.organization.dto;

import jakarta.validation.constraints.Size;

public record OrganizationUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 32) String status,
        @Size(max = 64) String managerGlobalUserId
) {
}
