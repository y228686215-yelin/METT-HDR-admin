package com.mett.hdr.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectMemberRequest(
        @NotBlank @Size(max = 64) String globalUserId,
        @NotBlank @Size(max = 32) String memberRole
) {
}
