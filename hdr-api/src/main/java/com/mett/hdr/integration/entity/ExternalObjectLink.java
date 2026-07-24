package com.mett.hdr.integration.entity;

import java.time.LocalDateTime;

public record ExternalObjectLink(
        Long id,
        String localObjectType,
        Long localObjectId,
        String localGlobalId,
        String externalSystem,
        String externalObjectType,
        String externalObjectId,
        String externalGlobalId,
        String relationType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
