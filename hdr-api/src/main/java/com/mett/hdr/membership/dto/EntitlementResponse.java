package com.mett.hdr.membership.dto;

public record EntitlementResponse(
        String code,
        String type,
        boolean enabled,
        boolean unlimited,
        Long quotaLimit,
        Long used,
        Long reserved,
        Long remaining
) {
}
