package com.mett.hdr.identity.entity;

import java.time.LocalDateTime;

public record UserProfile(
        Long id,
        Long userId,
        String displayName,
        String avatarFileId,
        String companyName,
        String country,
        String language,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
