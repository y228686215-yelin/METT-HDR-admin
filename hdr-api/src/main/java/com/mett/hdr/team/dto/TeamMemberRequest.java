package com.mett.hdr.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamMemberRequest(
        @NotBlank @Size(max = 64) String globalUserId,
        @NotBlank @Size(max = 32) String memberRole
) {
}
