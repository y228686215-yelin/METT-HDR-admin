package com.mett.hdr.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String organizationType,
        @Size(max = 1000) String description
) {
}
