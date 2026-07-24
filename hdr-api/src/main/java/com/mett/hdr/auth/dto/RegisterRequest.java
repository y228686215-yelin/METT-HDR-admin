package com.mett.hdr.auth.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        String email,
        String phone,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
