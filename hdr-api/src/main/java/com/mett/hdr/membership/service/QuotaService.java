package com.mett.hdr.membership.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.entity.MembershipPlanEntitlement;
import com.mett.hdr.membership.entity.MembershipUsage;
import com.mett.hdr.membership.entity.QuotaTransaction;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.model.QuotaCommand;
import com.mett.hdr.membership.model.QuotaMutationResult;
import com.mett.hdr.membership.repository.EntitlementDefinitionRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.repository.MembershipUsageRepository;
import com.mett.hdr.membership.repository.QuotaTransactionRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {

    private final MembershipRepository membershipRepository;
    private final EntitlementDefinitionRepository entitlementRepository;
    private final MembershipUsageRepository usageRepository;
    private final QuotaTransactionRepository transactionRepository;
    private final UsageCycleService usageCycleService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public QuotaService(
            MembershipRepository membershipRepository,
            EntitlementDefinitionRepository entitlementRepository,
            MembershipUsageRepository usageRepository,
            QuotaTransactionRepository transactionRepository,
            UsageCycleService usageCycleService,
            AuditService auditService,
            UserRepository userRepository,
            TeamRepository teamRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.entitlementRepository = entitlementRepository;
        this.usageRepository = usageRepository;
        this.transactionRepository = transactionRepository;
        this.usageCycleService = usageCycleService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public QuotaMutationResult consume(QuotaCommand command) {
        return mutate(command, "CONSUME", true, false, null);
    }

    @Transactional
    public QuotaMutationResult restore(QuotaCommand command) {
        return mutate(command, "RESTORE", false, false, null);
    }

    @Transactional
    public QuotaMutationResult reserve(QuotaCommand command) {
        requireReservationKey(command);
        return mutate(command, "RESERVE", false, true, null);
    }

    @Transactional
    public QuotaMutationResult commitReservation(QuotaCommand command) {
        requireReservationKey(command);
        return mutate(command, "COMMIT", true, false, "COMMIT");
    }

    @Transactional
    public QuotaMutationResult releaseReservation(QuotaCommand command) {
        requireReservationKey(command);
        return mutate(command, "RELEASE", false, false, "RELEASE");
    }

    @Transactional
    public QuotaMutationResult adjust(
            QuotaCommand command,
            boolean increase,
            HttpServletRequest request
    ) {
        QuotaMutationResult result = mutate(
                command, "ADJUST", increase, false, increase ? "ADJUST_UP" : "ADJUST_DOWN");
        if (!result.idempotentReplay()) {
            auditService.record(
                    command.actorUserId(),
                    "QUOTA_ADJUST",
                    "MEMBERSHIP",
                    subjectResourceId(command.subject()),
                    request
            );
        }
        return result;
    }

    private QuotaMutationResult mutate(
            QuotaCommand command,
            String changeType,
            boolean increaseUsed,
            boolean increaseReserved,
            String specialMode
    ) {
        validateCommand(command);
        QuotaTransaction replay = transactionRepository.findByOperationKey(
                command.operationKey()).orElse(null);
        if (replay != null) {
            return replay(replay, command, changeType);
        }

        Membership membership = membershipRepository.lockCurrent(command.subject())
                .orElseThrow(() -> new NotFoundException("Current membership not found."));
        if (!membership.providesAccess()) {
            throw new ForbiddenException("Membership does not provide quota access.");
        }
        membership = usageCycleService.rolloverIfNeeded(membership, null);

        replay = transactionRepository.findByOperationKey(command.operationKey()).orElse(null);
        if (replay != null) {
            return replay(replay, command, changeType);
        }

        MembershipPlanEntitlement entitlement = entitlementRepository.findForPlanAndCode(
                        membership.membershipPlanId(), command.entitlementCode())
                .filter(MembershipPlanEntitlement::available)
                .filter(value -> "QUOTA".equals(value.entitlementType()))
                .orElseThrow(() -> new NotFoundException("Quota entitlement not found."));
        MembershipUsage usage = usageRepository.lockForCycle(
                        membership.id(),
                        entitlement.entitlementDefinitionId(),
                        membership.currentPeriodStartAt())
                .orElseThrow(() -> new IllegalStateException(
                        "Quota usage has not been initialized."));

        long usedBefore = usage.usedCount();
        long reservedBefore = usage.reservedCount();
        long usedAfter = usedBefore;
        long reservedAfter = reservedBefore;

        if ("RESTORE".equals(changeType)
                || ("ADJUST".equals(changeType) && "ADJUST_DOWN".equals(specialMode))) {
            usedAfter = subtract(usedBefore, command.amount(), "Quota usage cannot become negative.");
        } else if ("RESERVE".equals(changeType)) {
            if (transactionRepository.reservationExists(
                    membership.id(),
                    entitlement.entitlementDefinitionId(),
                    command.reservationKey())) {
                throw new ConflictException("Reservation key is already in use.");
            }
            reservedAfter = add(reservedBefore, command.amount());
        } else if ("COMMIT".equals(changeType)) {
            requireOutstandingReservation(membership, entitlement, command);
            reservedAfter = subtract(
                    reservedBefore, command.amount(), "Reserved quota cannot become negative.");
            usedAfter = add(usedBefore, command.amount());
        } else if ("RELEASE".equals(changeType)) {
            requireOutstandingReservation(membership, entitlement, command);
            reservedAfter = subtract(
                    reservedBefore, command.amount(), "Reserved quota cannot become negative.");
        } else if (increaseUsed) {
            usedAfter = add(usedBefore, command.amount());
        } else if (increaseReserved) {
            reservedAfter = add(reservedBefore, command.amount());
        }

        enforceCapacity(usage, usedAfter, reservedAfter);
        MembershipUsage updated = usageRepository.updateCounts(
                usage.id(), usedAfter, reservedAfter);
        QuotaTransaction transaction = transactionRepository.create(
                membership.id(),
                usage.id(),
                entitlement.entitlementDefinitionId(),
                command.operationKey(),
                changeType,
                command.amount(),
                usedBefore,
                usedAfter,
                reservedBefore,
                reservedAfter,
                usage.quotaLimitSnapshot(),
                command.reservationKey(),
                trimToNull(command.relatedObjectType()),
                trimToNull(command.relatedObjectId()),
                trimToNull(command.reason()),
                command.actorUserId()
        );
        return result(transaction, updated, false);
    }

    private QuotaMutationResult replay(
            QuotaTransaction transaction,
            QuotaCommand command,
            String changeType
    ) {
        Membership membership = membershipRepository.findById(transaction.membershipId())
                .orElseThrow();
        boolean subjectMatches = "USER".equals(command.subject().subjectType())
                ? Objects.equals(membership.userId(), command.subject().userId())
                : Objects.equals(membership.teamId(), command.subject().teamId());
        MembershipPlanEntitlement entitlement = entitlementRepository.findForPlanAndCode(
                        membership.membershipPlanId(), command.entitlementCode())
                .orElse(null);
        boolean matches = subjectMatches
                && entitlement != null
                && entitlement.entitlementDefinitionId().equals(
                        transaction.entitlementDefinitionId())
                && transaction.changeType().equals(changeType)
                && transaction.amount() == command.amount()
                && Objects.equals(transaction.reservationKey(), trimToNull(command.reservationKey()))
                && Objects.equals(transaction.relatedObjectType(), trimToNull(command.relatedObjectType()))
                && Objects.equals(transaction.relatedObjectId(), trimToNull(command.relatedObjectId()))
                && Objects.equals(transaction.reason(), trimToNull(command.reason()))
                && Objects.equals(transaction.actorUserId(), command.actorUserId());
        if (!matches) {
            throw new ConflictException("Operation key conflicts with an existing quota mutation.");
        }
        Long remaining = transaction.quotaLimitSnapshot() == null
                ? null
                : transaction.quotaLimitSnapshot()
                - transaction.usedAfter()
                - transaction.reservedAfter();
        return new QuotaMutationResult(
                transaction.operationKey(),
                transaction.changeType(),
                transaction.usedAfter(),
                transaction.reservedAfter(),
                remaining,
                true
        );
    }

    private void requireOutstandingReservation(
            Membership membership,
            MembershipPlanEntitlement entitlement,
            QuotaCommand command
    ) {
        long outstanding = transactionRepository.outstandingReservation(
                membership.id(),
                entitlement.entitlementDefinitionId(),
                command.reservationKey()
        );
        if (outstanding < command.amount()) {
            throw new ConflictException("Reservation does not have enough outstanding quota.");
        }
    }

    private void enforceCapacity(
            MembershipUsage usage,
            long usedAfter,
            long reservedAfter
    ) {
        if (!usage.unlimited()
                && (usedAfter > usage.quotaLimitSnapshot()
                || reservedAfter > usage.quotaLimitSnapshot() - usedAfter)) {
            throw new ConflictException("Quota is exhausted.");
        }
    }

    private long add(long current, long amount) {
        if (amount > Long.MAX_VALUE - current) {
            throw new BadRequestException("Quota amount is too large.");
        }
        return current + amount;
    }

    private long subtract(long current, long amount, String message) {
        if (amount > current) {
            throw new ConflictException(message);
        }
        return current - amount;
    }

    private void validateCommand(QuotaCommand command) {
        if (command == null || command.subject() == null
                || command.subject().subjectId() == null) {
            throw new BadRequestException("Membership subject is required.");
        }
        if (!"USER".equals(command.subject().subjectType())
                && !"TEAM".equals(command.subject().subjectType())) {
            throw new BadRequestException("Invalid membership subject.");
        }
        if (command.entitlementCode() == null || command.entitlementCode().isBlank()) {
            throw new BadRequestException("Entitlement code is required.");
        }
        if (command.amount() <= 0) {
            throw new BadRequestException("Quota amount must be positive.");
        }
        if (command.operationKey() == null || command.operationKey().isBlank()
                || command.operationKey().length() > 128) {
            throw new BadRequestException("Valid operation key is required.");
        }
    }

    private void requireReservationKey(QuotaCommand command) {
        if (command.reservationKey() == null || command.reservationKey().isBlank()
                || command.reservationKey().length() > 128) {
            throw new BadRequestException("Valid reservation key is required.");
        }
    }

    private QuotaMutationResult result(
            QuotaTransaction transaction,
            MembershipUsage usage,
            boolean replay
    ) {
        return new QuotaMutationResult(
                transaction.operationKey(),
                transaction.changeType(),
                usage.usedCount(),
                usage.reservedCount(),
                usage.remaining(),
                replay
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String subjectResourceId(MembershipSubject subject) {
        if ("USER".equals(subject.subjectType())) {
            return userRepository.findById(subject.userId()).orElseThrow().getGlobalUserId();
        }
        return teamRepository.findById(subject.teamId()).orElseThrow().globalTeamId();
    }
}
