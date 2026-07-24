package com.mett.hdr.membership.model;

public record QuotaMutationResult(
        String operationKey,
        String changeType,
        long used,
        long reserved,
        Long remaining,
        boolean idempotentReplay
) {
}
