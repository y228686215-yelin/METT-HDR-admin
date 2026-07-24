package com.mett.hdr.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.ForbiddenException;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.model.QuotaCommand;
import com.mett.hdr.membership.model.QuotaMutationResult;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.membership.repository.MembershipUsageRepository;
import com.mett.hdr.membership.repository.QuotaTransactionRepository;
import com.mett.hdr.membership.repository.UsageSnapshotRepository;
import com.mett.hdr.membership.service.EntitlementResolutionService;
import com.mett.hdr.membership.service.MembershipProvisioningService;
import com.mett.hdr.membership.service.QuotaService;
import com.mett.hdr.membership.service.UsageCycleService;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.permission.repository.PermissionRepository;
import com.mett.hdr.team.entity.Team;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaMembershipServiceIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";
    private static final String LIMITED_CODE = "ANALYSIS_RUN_COUNT";
    private static final String UNLIMITED_CODE = "PRODUCT_COMPARE_COUNT";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private MembershipPlanRepository planRepository;
    @Autowired
    private MembershipUsageRepository usageRepository;
    @Autowired
    private QuotaTransactionRepository transactionRepository;
    @Autowired
    private UsageSnapshotRepository snapshotRepository;
    @Autowired
    private MembershipProvisioningService provisioningService;
    @Autowired
    private EntitlementResolutionService entitlementService;
    @Autowired
    private UsageCycleService usageCycleService;
    @Autowired
    private QuotaService quotaService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserIdentityLinkRepository userIdentityLinkRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void reset() {
        auditLogRepository.clear();
        refreshTokenRepository.clear();
        membershipRepository.clearAllMembershipData();
        teamMemberRepository.clear();
        teamRepository.clear();
        organizationMemberRepository.clear();
        organizationRepository.clear();
        userIdentityLinkRepository.clear();
        userProfileRepository.clear();
        permissionRepository.clearAssignments();
        userRepository.clear();
        jdbcTemplate.update("""
                DELETE FROM membership_plan_entitlements
                WHERE entitlement_definition_id IN (
                    SELECT id FROM entitlement_definitions
                    WHERE entitlement_type = 'QUOTA'
                )
                """);
    }

    @AfterEach
    void clearTestPlanEntitlements() {
        jdbcTemplate.update("""
                DELETE FROM membership_plan_entitlements
                WHERE entitlement_definition_id IN (
                    SELECT id FROM entitlement_definitions
                    WHERE entitlement_type = 'QUOTA'
                )
                """);
    }

    @Test
    void quotaLedgerSupportsIdempotentConsumeRestoreAndReservationFlows() throws Exception {
        TestMembership test = registerWithQuota("quota-ledger@example.com", 10L, false);
        MembershipSubject subject = MembershipSubject.user(test.userId());

        QuotaMutationResult consume = quotaService.consume(
                command(subject, LIMITED_CODE, 3, "consume-1", null));
        assertThat(consume.used()).isEqualTo(3);
        assertThat(consume.remaining()).isEqualTo(7);

        QuotaMutationResult replay = quotaService.consume(
                command(subject, LIMITED_CODE, 3, "consume-1", null));
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThatThrownBy(() -> quotaService.consume(
                command(subject, LIMITED_CODE, 4, "consume-1", null)))
                .isInstanceOf(ConflictException.class);

        assertThat(quotaService.reserve(
                command(subject, LIMITED_CODE, 4, "reserve-1", "reservation-a")).reserved())
                .isEqualTo(4);
        QuotaMutationResult commit = quotaService.commitReservation(
                command(subject, LIMITED_CODE, 2, "commit-1", "reservation-a"));
        assertThat(commit.used()).isEqualTo(5);
        assertThat(commit.reserved()).isEqualTo(2);
        assertThat(quotaService.releaseReservation(
                command(subject, LIMITED_CODE, 2, "release-1", "reservation-a")).reserved())
                .isZero();

        assertThat(quotaService.restore(
                command(subject, LIMITED_CODE, 3, "restore-1", null)).used())
                .isEqualTo(2);
        assertThat(quotaService.consume(
                command(subject, LIMITED_CODE, 8, "consume-2", null)).used())
                .isEqualTo(10);
        assertThatThrownBy(() -> quotaService.consume(
                command(subject, LIMITED_CODE, 1, "consume-over", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exhausted");
        assertThatThrownBy(() -> quotaService.restore(
                command(subject, LIMITED_CODE, 11, "restore-negative", null)))
                .isInstanceOf(ConflictException.class);

        assertThat(transactionRepository.count()).isEqualTo(6);
        assertThat(usageRepository.findForCycle(
                test.membership().id(),
                definitionId(LIMITED_CODE),
                test.membership().currentPeriodStartAt()).orElseThrow().usedCount())
                .isEqualTo(10);
    }

    @Test
    void entitlementResolutionSupportsBooleanDisabledMissingAndUnlimitedQuota() throws Exception {
        TestMembership test = registerWithQuota("quota-unlimited@example.com", null, true);
        Membership membership = test.membership();

        assertThat(entitlementService.hasEntitlement(membership, "HDR_ACCESS")).isTrue();
        assertThat(entitlementService.hasEntitlement(membership, "MISSING_CODE")).isFalse();

        attachQuota("SPACE_COUNT", 2L, false, false);
        assertThat(entitlementService.hasEntitlement(membership, "SPACE_COUNT")).isFalse();
        assertThatThrownBy(() -> entitlementService.requireEntitlement(
                membership, "SPACE_COUNT"))
                .isInstanceOf(ForbiddenException.class);
        var disabledQuota = entitlementService.resolveEntitlements(membership)
                .stream()
                .filter(value -> "SPACE_COUNT".equals(value.code()))
                .findFirst()
                .orElseThrow();
        assertThat(disabledQuota.enabled()).isFalse();
        assertThat(disabledQuota.used()).isNull();

        QuotaMutationResult result = quotaService.consume(command(
                MembershipSubject.user(test.userId()),
                UNLIMITED_CODE,
                1_000_000,
                "unlimited-consume",
                null
        ));
        assertThat(result.used()).isEqualTo(1_000_000);
        assertThat(result.remaining()).isNull();
        assertThat(entitlementService.resolveQuota(
                membership, UNLIMITED_CODE).unlimited()).isTrue();
    }

    @Test
    void replacementPreservesHistoryAndPlanAudienceIsEnforced() throws Exception {
        TestMembership test = registerWithQuota("membership-replace@example.com", 10L, false);
        quotaService.consume(command(
                MembershipSubject.user(test.userId()),
                LIMITED_CODE,
                2,
                "replace-consume",
                null
        ));

        Membership replacement = provisioningService.replaceCurrentMembership(
                MembershipSubject.user(test.userId()),
                "PERSONAL_PLUS",
                1,
                "MANUAL",
                test.userId(),
                null
        );
        assertThat(planRepository.findById(replacement.membershipPlanId()).orElseThrow().planCode())
                .isEqualTo("PERSONAL_PLUS");
        assertThat(membershipRepository.findHistory(MembershipSubject.user(test.userId())))
                .hasSize(2);
        assertThat(membershipRepository.countCurrent(MembershipSubject.user(test.userId())))
                .isEqualTo(1);
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(membershipRepository.findHistory(MembershipSubject.user(test.userId())).get(0).status())
                .isEqualTo("REPLACED");

        assertThatThrownBy(() -> provisioningService.activateUserPlan(
                test.userId(), "TEAM_PLUS", 1, "MANUAL", test.userId(), null))
                .isInstanceOf(ConflictException.class);

        Team team = teamRepository.create(
                "team_membership_audience_test", null, "Audience Team", null, test.userId());
        teamMemberRepository.create(team.id(), test.userId(), "LEAD", test.userId());
        assertThatThrownBy(() -> provisioningService.activateTeamPlan(
                team.id(), "PERSONAL_PLUS", 1, "MANUAL", test.userId(), null))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> membershipRepository.create(
                MembershipSubject.user(test.userId()),
                replacement.membershipPlanId(),
                "ACTIVE",
                "MANUAL",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1),
                test.userId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredUsageCycleCreatesSnapshotAndNextCycleUsageOnce() throws Exception {
        TestMembership test = registerWithQuota("quota-rollover@example.com", 10L, false);
        quotaService.consume(command(
                MembershipSubject.user(test.userId()),
                LIMITED_CODE,
                4,
                "rollover-consume",
                null
        ));

        LocalDateTime cycleEnd = LocalDateTime.now().minusSeconds(1).withNano(0);
        LocalDateTime cycleStart = cycleEnd.minusMonths(1);
        jdbcTemplate.update("""
                UPDATE memberships
                SET current_period_start_at = ?, current_period_end_at = ?
                WHERE id = ?
                """, cycleStart, cycleEnd, test.membership().id());
        jdbcTemplate.update("""
                UPDATE membership_usage
                SET cycle_start_at = ?, cycle_end_at = ?
                WHERE membership_id = ?
                """, cycleStart, cycleEnd, test.membership().id());

        Membership expired = membershipRepository.findById(test.membership().id()).orElseThrow();
        Membership rolled = usageCycleService.rolloverIfNeeded(expired, null);
        usageCycleService.rolloverIfNeeded(rolled, null);

        assertThat(snapshotRepository.count()).isEqualTo(1);
        var snapshotJson = objectMapper.readTree(
                snapshotRepository.findAll().get(0).snapshotData());
        if (snapshotJson.isTextual()) {
            snapshotJson = objectMapper.readTree(snapshotJson.asText());
        }
        assertThat(snapshotJson.at("/entitlements/0/code").asText())
                .isEqualTo(LIMITED_CODE);
        assertThat(snapshotJson.at("/entitlements/0/used").asLong()).isEqualTo(4);
        assertThat(usageRepository.findAllForMembership(test.membership().id())).hasSize(2);
        assertThat(usageRepository.findForCycle(
                test.membership().id(),
                definitionId(LIMITED_CODE),
                rolled.currentPeriodStartAt()).orElseThrow().usedCount()).isZero();
        assertThat(auditLogRepository.countByAction("USAGE_CYCLE_ROLLOVER")).isEqualTo(1);
    }

    @Test
    void lifecycleAndControlledAdjustmentPreserveEntitlementBoundaries() throws Exception {
        TestMembership test = registerWithQuota("membership-lifecycle@example.com", 10L, false);
        MembershipSubject subject = MembershipSubject.user(test.userId());

        provisioningService.provisionDefaultPersonalFreeMembership(test.userId(), null);
        assertThat(membershipRepository.countCurrent(subject)).isEqualTo(1);

        Membership suspended = provisioningService.suspendMembership(
                subject, test.userId(), null);
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(entitlementService.hasEntitlement(suspended, "HDR_ACCESS")).isFalse();

        Membership active = provisioningService.reactivateMembership(
                subject, test.userId(), null);
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(entitlementService.hasEntitlement(active, "HDR_ACCESS")).isTrue();

        assertThat(quotaService.adjust(
                command(subject, LIMITED_CODE, 2, "adjust-up", null),
                true,
                null).used()).isEqualTo(2);
        assertThat(quotaService.adjust(
                command(subject, LIMITED_CODE, 1, "adjust-down", null),
                false,
                null).used()).isEqualTo(1);

        Membership expired = provisioningService.expireMembership(
                subject, test.userId(), null);
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(membershipRepository.findCurrent(subject)).isEmpty();
        assertThat(auditLogRepository.countByAction("MEMBERSHIP_SUSPEND")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("MEMBERSHIP_REACTIVATE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("MEMBERSHIP_EXPIRE")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction("QUOTA_ADJUST")).isEqualTo(2);
    }

    private TestMembership registerWithQuota(
            String email,
            Long quotaLimit,
            boolean unlimited
    ) throws Exception {
        String code = unlimited ? UNLIMITED_CODE : LIMITED_CODE;
        attachQuota(code, quotaLimit, unlimited, true);
        MvcResult registration = mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","phone":"","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String globalUserId = objectMapper.readTree(
                registration.getResponse().getContentAsString())
                .at("/data/globalUserId").asText();
        Long userId = userRepository.findByGlobalUserId(globalUserId).orElseThrow().getId();
        Membership membership = membershipRepository.findCurrentForUser(userId).orElseThrow();
        return new TestMembership(userId, membership);
    }

    private void attachQuota(
            String code,
            Long quotaLimit,
            boolean unlimited,
            boolean enabled
    ) {
        jdbcTemplate.update("""
                INSERT INTO membership_plan_entitlements (
                    membership_plan_id, entitlement_definition_id,
                    enabled, quota_limit, is_unlimited
                )
                SELECT p.id, d.id, ?, ?, ?
                FROM membership_plans p
                JOIN entitlement_definitions d ON d.code = ?
                WHERE p.plan_code = 'PERSONAL_FREE' AND p.version = 1
                """, enabled, quotaLimit, unlimited, code);
    }

    private Long definitionId(String code) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM entitlement_definitions WHERE code = ?
                """, Long.class, code);
    }

    private QuotaCommand command(
            MembershipSubject subject,
            String code,
            long amount,
            String operationKey,
            String reservationKey
    ) {
        return new QuotaCommand(
                subject,
                code,
                amount,
                operationKey,
                reservationKey,
                "TEST_RESOURCE",
                "test-resource",
                "Test-only quota operation",
                subject.userId()
        );
    }

    private record TestMembership(Long userId, Membership membership) {
    }
}
