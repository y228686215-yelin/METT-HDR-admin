package com.mett.hdr.team.dto;

import jakarta.validation.constraints.Size;

public record TeamUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 32) String status
) {
}
