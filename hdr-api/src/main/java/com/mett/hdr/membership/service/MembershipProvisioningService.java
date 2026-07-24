package com.mett.hdr.membership.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipProvisioningService {

    private static final Set<String> SOURCES = Set.of(
            "SYSTEM_DEFAULT", "MANUAL", "PAYMENT", "ADMIN");

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final UsageCycleService usageCycleService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AuditService auditService;

    public MembershipProvisioningService(
            MembershipRepository membershipRepository,
            MembershipPlanRepository planRepository,
            UsageCycleService usageCycleService,
            UserRepository userRepository,
            TeamRepository teamRepository,
            AuditService auditService
    ) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.usageCycleService = usageCycleService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Membership provisionDefaultPersonalFreeMembership(
            Long userId,
            HttpServletRequest request
    ) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        MembershipSubject subject = MembershipSubject.user(userId);
        Membership existing = membershipRepository.findCurrent(subject).orElse(null);
        if (existing != null) {
            return existing;
        }
        MembershipPlan plan = planRepository.findDefaultPersonal()
                .orElseThrow(() -> new IllegalStateException(
                        "Default personal membership plan is unavailable."));
        Membership membership;
        try {
            membership = createCurrent(subject, plan, "SYSTEM_DEFAULT", null);
        } catch (ConflictException ex) {
            membership = membershipRepository.findCurrent(subject).orElseThrow(() -> ex);
        }
        auditService.record(
                userId,
                "MEMBERSHIP_DEFAULT_PROVISION",
                "USER",
                userRepository.findById(userId).orElseThrow().getGlobalUserId(),
                request
        );
        return membership;
    }

    @Transactional
    public Membership activateUserPlan(
            Long userId,
            String planCode,
            int version,
            String source,
            Long actorUserId,
            HttpServletRequest request
    ) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        return activate(
                MembershipSubject.user(userId),
                planCode,
                version,
                source,
                actorUserId,
                request
        );
    }

    @Transactional
    public Membership activateTeamPlan(
            Long teamId,
            String planCode,
            int version,
            String source,
            Long actorUserId,
            HttpServletRequest request
    ) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
        return activate(
                MembershipSubject.team(teamId),
                planCode,
                version,
                source,
                actorUserId,
                request
        );
    }

    @Transactional
    public Membership replaceCurrentMembership(
            MembershipSubject subject,
            String planCode,
            int version,
            String source,
            Long actorUserId,
            HttpServletRequest request
    ) {
        Membership current = membershipRepository.lockCurrent(subject)
                .orElseThrow(() -> new NotFoundException("Current membership not found."));
        MembershipPlan plan = requirePlan(planCode, version, subject);
        if (current.membershipPlanId().equals(plan.id())) {
            throw new ConflictException("The selected plan is already current.");
        }
        usageCycleService.snapshotBeforeReplacement(current);
        membershipRepository.replace(current.id(), now());
        Membership replacement = createCurrent(subject, plan, requireSource(source), actorUserId);
        auditService.record(
                actorUserId,
                "MEMBERSHIP_PLAN_REPLACE",
                "MEMBERSHIP",
                subjectResourceId(subject),
                request
        );
        return replacement;
    }

    @Transactional
    public Membership suspendMembership(
            MembershipSubject subject,
            Long actorUserId,
            HttpServletRequest request
    ) {
        Membership current = requireCurrentLocked(subject);
        if (!"ACTIVE".equals(current.status())) {
            throw new ConflictException("Only an active membership may be suspended.");
        }
        Membership updated = membershipRepository.updateStatus(
                current.id(), "SUSPENDED", now(), null);
        auditService.record(actorUserId, "MEMBERSHIP_SUSPEND", "MEMBERSHIP",
                subjectResourceId(subject), request);
        return updated;
    }

    @Transactional
    public Membership reactivateMembership(
            MembershipSubject subject,
            Long actorUserId,
            HttpServletRequest request
    ) {
        Membership current = requireCurrentLocked(subject);
        if (!"SUSPENDED".equals(current.status())) {
            throw new ConflictException("Only a suspended membership may be reactivated.");
        }
        Membership updated = membershipRepository.updateStatus(
                current.id(), "ACTIVE", null, null);
        auditService.record(actorUserId, "MEMBERSHIP_REACTIVATE", "MEMBERSHIP",
                subjectResourceId(subject), request);
        return updated;
    }

    @Transactional
    public Membership expireMembership(
            MembershipSubject subject,
            Long actorUserId,
            HttpServletRequest request
    ) {
        Membership current = requireCurrentLocked(subject);
        Membership updated = membershipRepository.updateStatus(
                current.id(), "EXPIRED", current.suspendedAt(), now());
        auditService.record(actorUserId, "MEMBERSHIP_EXPIRE", "MEMBERSHIP",
                subjectResourceId(subject), request);
        return updated;
    }

    private Membership activate(
            MembershipSubject subject,
            String planCode,
            int version,
            String source,
            Long actorUserId,
            HttpServletRequest request
    ) {
        if (membershipRepository.lockCurrent(subject).isPresent()) {
            return replaceCurrentMembership(
                    subject, planCode, version, source, actorUserId, request);
        }
        MembershipPlan plan = requirePlan(planCode, version, subject);
        Membership membership = createCurrent(
                subject, plan, requireSource(source), actorUserId);
        auditService.record(
                actorUserId,
                "MEMBERSHIP_PLAN_ACTIVATE",
                "MEMBERSHIP",
                subjectResourceId(subject),
                request
        );
        return membership;
    }

    private Membership createCurrent(
            MembershipSubject subject,
            MembershipPlan plan,
            String source,
            Long actorUserId
    ) {
        validateAudience(plan, subject);
        LocalDateTime start = now();
        try {
            Membership membership = membershipRepository.create(
                    subject,
                    plan.id(),
                    "ACTIVE",
                    source,
                    start,
                    start,
                    start.plusMonths(1),
                    actorUserId
            );
            usageCycleService.initializeUsageRows(membership);
            return membership;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A current membership already exists.");
        }
    }

    private MembershipPlan requirePlan(
            String planCode,
            int version,
            MembershipSubject subject
    ) {
        MembershipPlan plan = planRepository.findActive(planCode, version)
                .orElseThrow(() -> new NotFoundException("Membership plan not found."));
        validateAudience(plan, subject);
        return plan;
    }

    private void validateAudience(MembershipPlan plan, MembershipSubject subject) {
        if (!plan.audienceType().equals(subject.planAudience())) {
            throw new ConflictException("Membership plan audience does not match the subject.");
        }
    }

    private Membership requireCurrentLocked(MembershipSubject subject) {
        return membershipRepository.lockCurrent(subject)
                .orElseThrow(() -> new NotFoundException("Current membership not found."));
    }

    private String requireSource(String source) {
        String normalized = source == null ? "" : source.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SOURCES.contains(normalized)) {
            throw new ConflictException("Invalid membership source.");
        }
        return normalized;
    }

    private String subjectResourceId(MembershipSubject subject) {
        if ("USER".equals(subject.subjectType())) {
            return userRepository.findById(subject.userId()).orElseThrow().getGlobalUserId();
        }
        return teamRepository.findById(subject.teamId()).orElseThrow().globalTeamId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
