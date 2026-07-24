package com.mett.hdr.membership.dto;

import java.time.LocalDateTime;

public record UsageResponse(
        String code,
        boolean unlimited,
        Long quotaLimit,
        long used,
        long reserved,
        Long remaining,
        LocalDateTime cycleStartAt,
        LocalDateTime cycleEndAt
) {
}
