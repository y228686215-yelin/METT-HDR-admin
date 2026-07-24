package com.mett.hdr.membership.entity;

import java.time.LocalDateTime;

public record UsageSnapshot(
        Long id,
        Long membershipId,
        LocalDateTime cycleStartAt,
        LocalDateTime cycleEndAt,
        String snapshotData,
        LocalDateTime createdAt
) {
}
