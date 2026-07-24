package com.mett.hdr.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamManagerTransferRequest(
        @NotBlank @Size(max = 64) String newManagerGlobalUserId
) {
}
