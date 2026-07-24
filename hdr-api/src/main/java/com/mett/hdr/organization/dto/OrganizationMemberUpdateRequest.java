package com.mett.hdr.organization.dto;

import jakarta.validation.constraints.Size;

public record OrganizationMemberUpdateRequest(
        @Size(max = 32) String memberRole,
        @Size(max = 32) String status
) {
}
