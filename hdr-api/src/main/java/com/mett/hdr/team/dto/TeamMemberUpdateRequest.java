package com.mett.hdr.team.dto;

import jakarta.validation.constraints.Size;

public record TeamMemberUpdateRequest(
        @Size(max = 32) String memberRole,
        @Size(max = 32) String status
) {
}
