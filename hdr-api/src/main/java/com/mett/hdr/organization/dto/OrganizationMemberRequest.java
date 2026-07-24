package com.mett.hdr.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationMemberRequest(
        @NotBlank @Size(max = 64) String globalUserId,
        @NotBlank @Size(max = 32) String memberRole
) {
}
