package com.mett.hdr.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationOwnershipTransferRequest(
        @NotBlank @Size(max = 64) String newOwnerGlobalUserId
) {
}
