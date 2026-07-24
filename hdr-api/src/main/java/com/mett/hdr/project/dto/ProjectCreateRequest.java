package com.mett.hdr.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String projectType,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 32) String ownershipScope,
        @Size(max = 64) String globalTeamId,
        @Size(max = 64) String globalOrganizationId,
        @Size(max = 128) String city,
        @Size(max = 64) String timezone
) {
}
