package com.mett.hdr.membership.service;

import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.membership.dto.EntitlementResponse;
import com.mett.hdr.membership.dto.MembershipResponse;
import com.mett.hdr.membership.dto.UsageResponse;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.entity.MembershipPlanEntitlement;
import com.mett.hdr.membership.entity.MembershipUsage;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.repository.EntitlementDefinitionRepository;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.repository.MembershipUsageRepository;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MembershipQueryService {

    private final CurrentActorService currentActorService;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final EntitlementDefinitionRepository entitlementRepository;
    private final MembershipUsageRepository usageRepository;
    private final EntitlementResolutionService entitlementResolutionService;
    private final UsageCycleService usageCycleService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public MembershipQueryService(
            CurrentActorService currentActorService,
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository,
            EntitlementDefinitionRepository entitlementRepository,
            MembershipUsageRepository usageRepository,
            EntitlementResolutionService entitlementResolutionService,
            UsageCycleService usageCycleService,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository
    ) {
        this.currentActorService = currentActorService;
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.entitlementRepository = entitlementRepository;
        this.usageRepository = usageRepository;
        this.entitlementResolutionService = entitlementResolutionService;
        this.usageCycleService = usageCycleService;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    public MembershipResponse personalMembership(String authorization) {
        return toResponse(requirePersonal(authorization));
    }

    public List<EntitlementResponse> personalEntitlements(String authorization) {
        return entitlementResolutionService.resolveEntitlements(requirePersonal(authorization));
    }

    public List<UsageResponse> personalUsage(String authorization) {
        return usage(requirePersonal(authorization));
    }

    public MembershipResponse teamMembership(String authorization, String globalTeamId) {
        return toResponse(requireTeam(authorization, globalTeamId));
    }

    public List<EntitlementResponse> teamEntitlements(
            String authorization,
            String globalTeamId
    ) {
        return entitlementResolutionService.resolveEntitlements(
                requireTeam(authorization, globalTeamId));
    }

    public List<UsageResponse> teamUsage(String authorization, String globalTeamId) {
        return usage(requireTeam(authorization, globalTeamId));
    }

    private Membership requirePersonal(String authorization) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Membership membership = membershipRepository.findCurrentForUser(actor.userId())
                .orElseThrow(() -> new NotFoundException("Current membership not found."));
        return usageCycleService.rolloverIfNeeded(membership, null);
    }

    private Membership requireTeam(String authorization, String globalTeamId) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        Team team = teamRepository.findByGlobalId(globalTeamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        teamMemberRepository.find(team.id(), actor.userId())
                .filter(value -> value.isActive())
                .orElseThrow(() -> new NotFoundException("Team not found."));
        Membership membership = membershipRepository.findCurrentForTeam(team.id())
                .orElseThrow(() -> new NotFoundException("Current team membership not found."));
        return usageCycleService.rolloverIfNeeded(membership, null);
    }

    private MembershipResponse toResponse(Membership membership) {
        MembershipPlan plan = planRepository.findById(membership.membershipPlanId()).orElseThrow();
        return new MembershipResponse(
                membership.subjectType(),
                plan.planCode(),
                plan.version(),
                plan.tier(),
                membership.status(),
                membership.currentPeriodStartAt(),
                membership.currentPeriodEndAt()
        );
    }

    private List<UsageResponse> usage(Membership membership) {
        return entitlementRepository.findForPlan(membership.membershipPlanId())
                .stream()
                .filter(value -> "QUOTA".equals(value.entitlementType()))
                .filter(MembershipPlanEntitlement::available)
                .map(value -> toUsage(membership, value))
                .toList();
    }

    private UsageResponse toUsage(
            Membership membership,
            MembershipPlanEntitlement entitlement
    ) {
        MembershipUsage usage = usageRepository.findForCycle(
                        membership.id(),
                        entitlement.entitlementDefinitionId(),
                        membership.currentPeriodStartAt())
                .orElseThrow(() -> new IllegalStateException(
                        "Quota usage has not been initialized."));
        return new UsageResponse(
                entitlement.entitlementCode(),
                usage.unlimited(),
                usage.quotaLimitSnapshot(),
                usage.usedCount(),
                usage.reservedCount(),
                usage.remaining(),
                usage.cycleStartAt(),
                usage.cycleEndAt()
        );
    }
}
