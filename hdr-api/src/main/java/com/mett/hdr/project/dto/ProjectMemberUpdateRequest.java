package com.mett.hdr.project.dto;

import jakarta.validation.constraints.Size;

public record ProjectMemberUpdateRequest(
        @Size(max = 32) String memberRole,
        @Size(max = 32) String status
) {
}
