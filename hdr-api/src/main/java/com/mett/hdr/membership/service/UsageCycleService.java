package com.mett.hdr.membership.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.SystemException;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.entity.EntitlementDefinition;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.entity.MembershipPlanEntitlement;
import com.mett.hdr.membership.entity.MembershipUsage;
import com.mett.hdr.membership.repository.EntitlementDefinitionRepository;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.repository.MembershipUsageRepository;
import com.mett.hdr.membership.repository.UsageSnapshotRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageCycleService {

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final EntitlementDefinitionRepository entitlementRepository;
    private final MembershipUsageRepository usageRepository;
    private final UsageSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UsageCycleService(
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository,
            EntitlementDefinitionRepository entitlementRepository,
            MembershipUsageRepository usageRepository,
            UsageSnapshotRepository snapshotRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.entitlementRepository = entitlementRepository;
        this.usageRepository = usageRepository;
        this.snapshotRepository = snapshotRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Membership rolloverIfNeeded(
            Membership candidate,
            HttpServletRequest request
    ) {
        Membership membership = membershipRepository.lockById(candidate.id()).orElseThrow();
        if (!membership.isCurrent() || !"ACTIVE".equals(membership.status())) {
            return membership;
        }
        LocalDateTime now = now();
        while (!now.isBefore(membership.currentPeriodEndAt())) {
            createSnapshot(membership, false);
            LocalDateTime nextStart = membership.currentPeriodEndAt();
            LocalDateTime nextEnd = nextPeriodEnd(nextStart);
            membership = membershipRepository.updatePeriod(membership.id(), nextStart, nextEnd);
            initializeUsageRows(membership);
            auditService.record(
                    null,
                    "USAGE_CYCLE_ROLLOVER",
                    "MEMBERSHIP",
                    subjectResourceId(membership),
                    request
            );
        }
        return membership;
    }

    @Transactional
    public void snapshotBeforeReplacement(Membership membership) {
        Membership locked = membershipRepository.lockById(membership.id()).orElseThrow();
        createSnapshot(locked, true);
    }

    public void initializeUsageRows(Membership membership) {
        for (MembershipPlanEntitlement entitlement
                : entitlementRepository.findForPlan(membership.membershipPlanId())) {
            if (!entitlement.available() || !"QUOTA".equals(entitlement.entitlementType())) {
                continue;
            }
            if (!entitlement.unlimited() && entitlement.quotaLimit() == null) {
                throw new IllegalStateException(
                        "Limited quota entitlement requires a quota limit.");
            }
            if (usageRepository.findForCycle(
                    membership.id(),
                    entitlement.entitlementDefinitionId(),
                    membership.currentPeriodStartAt()).isEmpty()) {
                usageRepository.create(
                        membership.id(),
                        entitlement.entitlementDefinitionId(),
                        membership.currentPeriodStartAt(),
                        membership.currentPeriodEndAt(),
                        entitlement.quotaLimit(),
                        entitlement.unlimited()
                );
            }
        }
    }

    private void createSnapshot(Membership membership, boolean onlyWhenUsageExists) {
        List<MembershipUsage> usages = usageRepository.findForCycle(
                membership.id(), membership.currentPeriodStartAt());
        if (onlyWhenUsageExists && usages.isEmpty()) {
            return;
        }
        if (snapshotRepository.findCycle(
                membership.id(),
                membership.currentPeriodStartAt(),
                membership.currentPeriodEndAt()).isPresent()) {
            return;
        }
        MembershipPlan plan = planRepository.findById(membership.membershipPlanId()).orElseThrow();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("subject", Map.of(
                "type", membership.subjectType(),
                "globalId", subjectResourceId(membership)
        ));
        snapshot.put("planCode", plan.planCode());
        snapshot.put("planVersion", plan.version());
        snapshot.put("cycleStartAt", membership.currentPeriodStartAt());
        snapshot.put("cycleEndAt", membership.currentPeriodEndAt());
        snapshot.put("generatedAt", now());
        snapshot.put("entitlements", usages.stream().map(this::usageSnapshot).toList());
        try {
            snapshotRepository.create(
                    membership.id(),
                    membership.currentPeriodStartAt(),
                    membership.currentPeriodEndAt(),
                    objectMapper.writeValueAsString(snapshot)
            );
        } catch (JsonProcessingException ex) {
            throw new SystemException("Unable to create usage snapshot.");
        }
    }

    private Map<String, Object> usageSnapshot(MembershipUsage usage) {
        EntitlementDefinition definition = entitlementRepository.findById(
                usage.entitlementDefinitionId()).orElseThrow();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", definition.code());
        value.put("quotaLimit", usage.quotaLimitSnapshot());
        value.put("unlimited", usage.unlimited());
        value.put("used", usage.usedCount());
        value.put("reserved", usage.reservedCount());
        value.put("cycleStartAt", usage.cycleStartAt());
        value.put("cycleEndAt", usage.cycleEndAt());
        return value;
    }

    private String subjectResourceId(Membership membership) {
        if ("USER".equals(membership.subjectType())) {
            return userRepository.findById(membership.userId()).orElseThrow().getGlobalUserId();
        }
        return teamRepository.findById(membership.teamId()).orElseThrow().globalTeamId();
    }

    private LocalDateTime nextPeriodEnd(LocalDateTime start) {
        return start.plusMonths(1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
