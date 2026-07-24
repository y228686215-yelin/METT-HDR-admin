package com.mett.hdr.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectSpaceResponse(
        String globalSpaceId,
        String parentGlobalSpaceId,
        String name,
        String spaceLevelType,
        String usageCode,
        String geometryType,
        BigDecimal lengthM,
        BigDecimal widthM,
        BigDecimal heightM,
        BigDecimal floorAreaM2,
        BigDecimal volumeM3,
        String orientationCode,
        String status,
        int sortOrder,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
