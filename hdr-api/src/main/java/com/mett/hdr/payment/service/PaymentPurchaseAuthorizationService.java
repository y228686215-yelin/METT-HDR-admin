package com.mett.hdr.payment.service;

import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.payment.dto.PaymentOrderCreateRequest;
import com.mett.hdr.payment.model.PurchaseSubject;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.entity.TeamMember;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PaymentPurchaseAuthorizationService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;

    public PaymentPurchaseAuthorizationService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            OrganizationMemberRepository organizationMemberRepository,
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
    }

    public PurchaseSubject authorize(
            AuthenticatedUser actor,
            PaymentOrderCreateRequest request,
            MembershipPlan plan
    ) {
        if (request.subjectType() == null || request.subjectType().isBlank()) {
            throw new BadRequestException("Order subject type is required.");
        }
        String subjectType = request.subjectType().trim().toUpperCase(Locale.ROOT);
        PurchaseSubject subject;
        if ("USER".equals(subjectType)) {
            if (!"PERSONAL".equals(plan.audienceType())) {
                throw new ConflictException("Offer audience does not match order subject.");
            }
            if (request.globalTeamId() != null && !request.globalTeamId().isBlank()) {
                throw new BadRequestException("Personal order cannot target a team.");
            }
            subject = new PurchaseSubject("USER", actor.userId(), null, null);
        } else if ("TEAM".equals(subjectType)) {
            if (!"TEAM".equals(plan.audienceType())) {
                throw new ConflictException("Offer audience does not match order subject.");
            }
            Team team = requirePurchasableTeam(request.globalTeamId(), actor.userId());
            subject = new PurchaseSubject(
                    "TEAM", null, team.id(), team.globalTeamId());
        } else {
            throw new BadRequestException("Invalid order subject type.");
        }
        requireEligible(subject, plan);
        return subject;
    }

    public void requireEligible(PurchaseSubject subject, MembershipPlan targetPlan) {
        if (!targetPlan.audienceType().equals(
                "USER".equals(subject.subjectType()) ? "PERSONAL" : "TEAM")) {
            throw new ConflictException("Plan audience does not match membership subject.");
        }
        Membership current = membershipRepository.findCurrent(
                "USER".equals(subject.subjectType())
                        ? MembershipSubject.user(subject.userId())
                        : MembershipSubject.team(subject.teamId()))
                .orElse(null);
        if ("TEAM".equals(subject.subjectType())) {
            if (current != null) {
                throw new ConflictException(
                        "An active paid team membership already exists.");
            }
            return;
        }
        if (current == null) {
            throw new ConflictException("Current personal membership is unavailable.");
        }
        MembershipPlan currentPlan = planRepository.findById(
                current.membershipPlanId()).orElseThrow();
        if (current.providesAccess()
                && Set.of("PLUS", "PRO").contains(currentPlan.tier())) {
            throw new ConflictException(
                    "An active paid personal membership already exists.");
        }
        if (!"FREE".equals(currentPlan.tier()) || !current.providesAccess()) {
            throw new ConflictException(
                    "Current personal membership is not eligible for purchase.");
        }
    }

    public boolean canViewTeamOrder(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return false;
        }
        return teamMemberRepository.find(teamId, userId)
                .filter(TeamMember::isActive)
                .isPresent()
                || hasOrganizationAuthority(team, userId);
    }

    private Team requirePurchasableTeam(String globalTeamId, Long userId) {
        if (globalTeamId == null || globalTeamId.isBlank()) {
            throw new BadRequestException("Global team ID is required.");
        }
        Team team = teamRepository.findByGlobalId(globalTeamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        TeamMember member = teamMemberRepository.find(team.id(), userId)
                .filter(TeamMember::isActive)
                .orElse(null);
        boolean organizationAuthority = hasOrganizationAuthority(team, userId);
        if (member == null && !organizationAuthority) {
            throw new NotFoundException("Team not found.");
        }
        boolean manager = team.managedByUserId().equals(userId);
        boolean lead = member != null && "LEAD".equals(member.memberRole());
        if (!manager && !lead && !organizationAuthority) {
            throw new ForbiddenException("Team purchasing authority is required.");
        }
        return team;
    }

    private boolean hasOrganizationAuthority(Team team, Long userId) {
        return team.organizationId() != null
                && organizationMemberRepository.find(team.organizationId(), userId)
                .filter(value -> value.isActive()
                        && Set.of("OWNER", "ADMIN").contains(value.memberRole()))
                .isPresent();
    }
}
