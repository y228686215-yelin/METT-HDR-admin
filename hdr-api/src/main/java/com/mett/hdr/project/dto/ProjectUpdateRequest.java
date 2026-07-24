package com.mett.hdr.project.dto;

import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 64) String projectType,
        @Size(max = 2000) String description,
        @Size(max = 128) String city,
        @Size(max = 64) String timezone
) {
}
