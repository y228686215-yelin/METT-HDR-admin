package com.mett.hdr.membership.service;

import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.membership.dto.EntitlementResponse;
import com.mett.hdr.membership.entity.EntitlementDefinition;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlanEntitlement;
import com.mett.hdr.membership.entity.MembershipUsage;
import com.mett.hdr.membership.repository.EntitlementDefinitionRepository;
import com.mett.hdr.membership.repository.MembershipUsageRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EntitlementResolutionService {

    private final EntitlementDefinitionRepository entitlementRepository;
    private final MembershipUsageRepository usageRepository;

    public EntitlementResolutionService(
            EntitlementDefinitionRepository entitlementRepository,
            MembershipUsageRepository usageRepository
    ) {
        this.entitlementRepository = entitlementRepository;
        this.usageRepository = usageRepository;
    }

    public boolean hasEntitlement(Membership membership, String entitlementCode) {
        return entitlementRepository.findForPlanAndCode(
                        membership.membershipPlanId(), entitlementCode)
                .filter(MembershipPlanEntitlement::available)
                .filter(value -> membership.providesAccess())
                .isPresent();
    }

    public void requireEntitlement(Membership membership, String entitlementCode) {
        entitlementRepository.findByCode(entitlementCode)
                .orElseThrow(() -> new NotFoundException("Entitlement not found."));
        if (!hasEntitlement(membership, entitlementCode)) {
            throw new ForbiddenException("Membership entitlement is unavailable.");
        }
    }

    public List<EntitlementResponse> resolveEntitlements(Membership membership) {
        return entitlementRepository.findForPlan(membership.membershipPlanId())
                .stream()
                .map(value -> resolve(membership, value))
                .toList();
    }

    public EntitlementResponse resolveQuota(
            Membership membership,
            String entitlementCode
    ) {
        EntitlementDefinition definition = entitlementRepository.findByCode(entitlementCode)
                .orElseThrow(() -> new NotFoundException("Entitlement not found."));
        if (!definition.isQuota()) {
            throw new NotFoundException("Quota entitlement not found.");
        }
        MembershipPlanEntitlement entitlement = entitlementRepository.findForPlanAndCode(
                        membership.membershipPlanId(), entitlementCode)
                .orElseThrow(() -> new NotFoundException("Quota entitlement not found."));
        return resolve(membership, entitlement);
    }

    private EntitlementResponse resolve(
            Membership membership,
            MembershipPlanEntitlement entitlement
    ) {
        boolean enabled = membership.providesAccess() && entitlement.available();
        if (!"QUOTA".equals(entitlement.entitlementType())) {
            return new EntitlementResponse(
                    entitlement.entitlementCode(),
                    entitlement.entitlementType(),
                    enabled,
                    false,
                    null,
                    null,
                    null,
                    null
            );
        }
        Optional<MembershipUsage> usage = usageRepository.findForCycle(
                membership.id(),
                entitlement.entitlementDefinitionId(),
                membership.currentPeriodStartAt());
        if (usage.isEmpty()) {
            if (enabled) {
                throw new IllegalStateException("Quota usage has not been initialized.");
            }
            return new EntitlementResponse(
                    entitlement.entitlementCode(),
                    entitlement.entitlementType(),
                    false,
                    entitlement.unlimited(),
                    entitlement.quotaLimit(),
                    null,
                    null,
                    null
            );
        }
        MembershipUsage currentUsage = usage.orElseThrow();
        return new EntitlementResponse(
                entitlement.entitlementCode(),
                entitlement.entitlementType(),
                enabled,
                currentUsage.unlimited(),
                currentUsage.quotaLimitSnapshot(),
                currentUsage.usedCount(),
                currentUsage.reservedCount(),
                currentUsage.remaining()
        );
    }
}
