package com.mett.hdr.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectManagerTransferRequest(
        @NotBlank @Size(max = 64) String newManagerGlobalUserId
) {
}
