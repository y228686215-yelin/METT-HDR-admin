package com.mett.hdr.project.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectSpace(
        Long id,
        String globalSpaceId,
        Long projectId,
        Long parentSpaceId,
        String name,
        String spaceLevel,
        String usageCode,
        String geometryType,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal area,
        BigDecimal volume,
        String orientation,
        String status,
        int sortOrder,
        Long createdByUserId,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
