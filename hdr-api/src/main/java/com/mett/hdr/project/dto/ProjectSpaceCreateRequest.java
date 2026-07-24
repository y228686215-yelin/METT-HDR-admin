package com.mett.hdr.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProjectSpaceCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 64) String parentGlobalSpaceId,
        @NotBlank @Size(max = 32) String spaceLevelType,
        @Size(max = 64) String usageCode,
        @NotBlank @Size(max = 32) String geometryType,
        BigDecimal lengthM,
        BigDecimal widthM,
        BigDecimal heightM,
        @Size(max = 32) String orientationCode,
        Integer sortOrder
) {
}
