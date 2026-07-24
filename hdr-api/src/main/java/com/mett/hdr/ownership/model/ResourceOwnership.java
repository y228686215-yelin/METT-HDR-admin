package com.mett.hdr.ownership.model;

public record ResourceOwnership(
        Long ownerUserId,
        Long teamId,
        Long organizationId,
        Long createdByUserId,
        Long managedByUserId
) {
}
