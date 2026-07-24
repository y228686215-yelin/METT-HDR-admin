package com.mett.hdr.ownership.model;

public record OwnershipAssignmentRequest(
        OwnershipScope scope,
        Long teamId,
        Long organizationId,
        Long managedByUserId
) {
}
