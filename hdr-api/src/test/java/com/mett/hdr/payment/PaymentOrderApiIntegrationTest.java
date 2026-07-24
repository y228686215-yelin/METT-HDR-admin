package com.mett.hdr.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.model.MembershipSubject;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.membership.repository.MembershipRepository;
import com.mett.hdr.organization.repository.OrganizationMemberRepository;
import com.mett.hdr.organization.repository.OrganizationRepository;
import com.mett.hdr.payment.entity.MembershipPlanOffer;
import com.mett.hdr.payment.entity.PaymentAttempt;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.repository.MembershipPlanOfferRepository;
import com.mett.hdr.payment.repository.PaymentAttemptRepository;
import com.mett.hdr.payment.repository.PaymentEventRepository;
import com.mett.hdr.payment.repository.PaymentOrderFulfillmentRepository;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import com.mett.hdr.payment.repository.PaymentOrderStateTransitionRepository;
import com.mett.hdr.payment.service.PaymentOrderFulfillmentService;
import com.mett.hdr.payment.service.PaymentOrderStateMachine;
import com.mett.hdr.permission.repository.PermissionRepository;
import com.mett.hdr.team.repository.TeamMemberRepository;
import com.mett.hdr.team.repository.TeamRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentOrderApiIntegrationTest {

    private static final String PASSWORD = "StrongPassword123";
    private static final String SIGNING_SECRET = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("mett.hdr.payment.local-test.enabled", () -> true);
        registry.add(
                "mett.hdr.payment.local-test.signing-secret",
                () -> SIGNING_SECRET
        );
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private GlobalIdService globalIdService;
    @Autowired
    private MembershipPlanOfferRepository offerRepository;
    @Autowired
    private MembershipPlanRepository planRepository;
    @Autowired
    private PaymentOrderRepository orderRepository;
    @Autowired
    private PaymentAttemptRepository attemptRepository;
    @Autowired
    private PaymentEventRepository eventRepository;
    @Autowired
    private PaymentOrderFulfillmentRepository fulfillmentRepository;
    @Autowired
    private PaymentOrderStateTransitionRepository transitionRepository;
    @Autowired
    private PaymentOrderFulfillmentService fulfillmentService;
    @Autowired
    private PaymentOrderStateMachine stateMachine;
    @Autowired
    private MembershipRepository membershipRepository;
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
        clearAll();
    }

    @AfterEach
    void cleanup() {
        clearAll();
    }

    @Test
    void activeOffersAndPersonalOrdersUseServerSnapshotsAndPersistentIdempotency()
            throws Exception {
        TestUser user = registerAndLogin("payment-order@example.com", false);
        MembershipPlanOffer plus = createOffer(
                "TEST_PERSONAL_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1250, "CNY", -1, 1);
        createOffer(
                "TEST_PERSONAL_DRAFT", "PERSONAL_PRO", "DRAFT",
                2200, "CNY", -1, 1);
        createOffer(
                "TEST_PERSONAL_FUTURE", "PERSONAL_PRO", "ACTIVE",
                2300, "CNY", 1, 1);

        mockMvc.perform(get("/api/v1/app/membership-offers")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].globalOfferId")
                        .value(plus.globalOfferId()))
                .andExpect(jsonPath("$.data[0].amountMinor").value(1250));

        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", "personal-order-untrusted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "globalOfferId":"%s",
                                  "subjectType":"USER",
                                  "globalTeamId":null,
                                  "amountMinor":1,
                                  "currency":"USD"
                                }
                                """.formatted(plus.globalOfferId())))
                .andExpect(status().isBadRequest());

        MvcResult created = createOrder(
                user, plus.globalOfferId(), "USER", null, "personal-order-1");
        String globalOrderId = read(created, "/data/globalOrderId");
        assertThat(readLong(created, "/data/amountMinor")).isEqualTo(1250);
        assertThat(read(created, "/data/currency")).isEqualTo("CNY");

        MvcResult replay = createOrder(
                user, plus.globalOfferId(), "USER", null, "personal-order-1");
        assertThat(read(replay, "/data/globalOrderId")).isEqualTo(globalOrderId);
        assertThat(orderRepository.count()).isEqualTo(1);

        MembershipPlanOffer pro = createOffer(
                "TEST_PERSONAL_PRO", "PERSONAL_PRO", "ACTIVE",
                2400, "CNY", -1, 1);
        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", "personal-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(pro.globalOfferId(), "USER", null)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/app/orders/{id}", globalOrderId)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("PERSONAL_PLUS"))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        mockMvc.perform(get("/api/v1/app/orders")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void verifiedPersonalPaymentFulfillsExactlyOnceAndRejectsInvalidSignature()
            throws Exception {
        TestUser user = registerAndLogin("payment-success@example.com", false);
        MembershipPlanOffer offer = createOffer(
                "TEST_SUCCESS_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1500, "CNY", -1, 2);
        MvcResult created = createOrder(
                user, offer.globalOfferId(), "USER", null, "success-order");
        String orderId = read(created, "/data/globalOrderId");
        MvcResult attemptResult = createAttempt(
                user, orderId, "success-attempt");
        PaymentAttempt attempt = attemptRepository.findByGlobalId(
                read(attemptResult, "/data/globalPaymentAttemptId")).orElseThrow();
        String callback = callbackBody(
                "event-success-1",
                attempt.providerAttemptId(),
                "transaction-success-1",
                orderId,
                "SUCCEEDED",
                1500,
                "CNY"
        );

        mockMvc.perform(post("/api/v1/integrations/payments/LOCAL_TEST/callbacks")
                        .header("X-Local-Test-Signature", "00")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callback))
                .andExpect(status().isUnauthorized());
        assertThat(eventRepository.count()).isZero();
        assertThat(orderRepository.findByGlobalId(orderId).orElseThrow().paymentStatus())
                .isEqualTo("PROCESSING");

        String pendingCallback = callbackBody(
                "event-pending-1",
                attempt.providerAttemptId(),
                "transaction-pending-1",
                orderId,
                "PENDING",
                1500,
                "CNY"
        );
        MvcResult pending = sendCallback(
                pendingCallback, signature(pendingCallback), status().isOk());
        assertThat(read(pending, "/data/processingStatus")).isEqualTo("PROCESSED");
        PaymentOrder processing = orderRepository.findByGlobalId(orderId).orElseThrow();
        assertThat(processing.orderStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(processing.paymentStatus()).isEqualTo("PROCESSING");
        assertThat(processing.fulfillmentStatus()).isEqualTo("NOT_STARTED");

        MvcResult success = sendCallback(callback, signature(callback), status().isOk());
        assertThat(read(success, "/data/processingStatus")).isEqualTo("PROCESSED");
        PaymentOrder fulfilled = orderRepository.findByGlobalId(orderId).orElseThrow();
        assertThat(fulfilled.orderStatus()).isEqualTo("FULFILLED");
        assertThat(fulfilled.paymentStatus()).isEqualTo("PAID");
        assertThat(fulfilled.fulfillmentStatus()).isEqualTo("FULFILLED");

        Long userId = userId(user);
        Membership membership = membershipRepository.findCurrentForUser(userId).orElseThrow();
        assertThat(planRepository.findById(membership.membershipPlanId())
                .orElseThrow().planCode()).isEqualTo("PERSONAL_PLUS");
        assertThat(membership.source()).isEqualTo("PAYMENT");
        assertThat(membership.currentPeriodEndAt())
                .isEqualTo(membership.currentPeriodStartAt().plusMonths(1));
        assertThat(membership.expiresAt())
                .isAfter(membership.startedAt().plusMonths(1));
        assertThat(membershipRepository.countCurrent(MembershipSubject.user(userId)))
                .isEqualTo(1);
        assertThat(membershipRepository.findHistory(MembershipSubject.user(userId)))
                .hasSize(2);

        MvcResult replay = sendCallback(callback, signature(callback), status().isOk());
        assertThat(readBoolean(replay, "/data/duplicate")).isTrue();
        assertThat(eventRepository.count()).isEqualTo(2);
        assertThat(fulfillmentRepository.count()).isEqualTo(1);
        assertThat(membershipRepository.findHistory(MembershipSubject.user(userId)))
                .hasSize(2);
        assertThat(transitionRepository.count()).isGreaterThanOrEqualTo(10);
        assertThat(auditLogRepository.countByAction("PAYMENT_VERIFIED")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(
                "PAYMENT_FULFILLMENT_SUCCESS")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(
                "PAYMENT_CALLBACK_DUPLICATE")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", "paid-membership-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(offer.globalOfferId(), "USER", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void trustedPaymentMismatchesAndInactiveOrdersMoveToReview() throws Exception {
        TestUser amountUser = registerAndLogin("payment-amount@example.com", false);
        MembershipPlanOffer offer = createOffer(
                "TEST_REVIEW_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1600, "CNY", -1, 1);
        PaymentFlow amountFlow = createFlow(
                amountUser, offer, "amount-order", "amount-attempt");
        String amountCallback = callbackBody(
                "event-amount-review",
                amountFlow.attempt().providerAttemptId(),
                "transaction-amount-review",
                amountFlow.orderId(),
                "SUCCEEDED",
                1599,
                "CNY"
        );
        sendCallback(amountCallback, signature(amountCallback), status().isOk());
        assertReview(amountFlow.orderId(), "PAYMENT_AMOUNT_MISMATCH");
        assertPersonalFree(amountUser);

        TestUser currencyUser = registerAndLogin("payment-currency@example.com", false);
        PaymentFlow currencyFlow = createFlow(
                currencyUser, offer, "currency-order", "currency-attempt");
        String currencyCallback = callbackBody(
                "event-currency-review",
                currencyFlow.attempt().providerAttemptId(),
                "transaction-currency-review",
                currencyFlow.orderId(),
                "SUCCEEDED",
                1600,
                "USD"
        );
        sendCallback(currencyCallback, signature(currencyCallback), status().isOk());
        assertReview(currencyFlow.orderId(), "PAYMENT_CURRENCY_MISMATCH");
        assertPersonalFree(currencyUser);

        TestUser cancelledUser = registerAndLogin("payment-cancelled@example.com", false);
        PaymentFlow cancelledFlow = createFlow(
                cancelledUser, offer, "cancel-order", "cancel-attempt");
        mockMvc.perform(post("/api/v1/app/orders/{id}/cancel", cancelledFlow.orderId())
                        .header("Authorization", bearer(cancelledUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
        String cancelledCallback = callbackBody(
                "event-cancelled-review",
                cancelledFlow.attempt().providerAttemptId(),
                "transaction-cancelled-review",
                cancelledFlow.orderId(),
                "SUCCEEDED",
                1600,
                "CNY"
        );
        sendCallback(cancelledCallback, signature(cancelledCallback), status().isOk());
        assertThat(orderRepository.findByGlobalId(cancelledFlow.orderId())
                .orElseThrow().orderStatus()).isEqualTo("PAYMENT_REVIEW_REQUIRED");
        assertPersonalFree(cancelledUser);

        TestUser expiredUser = registerAndLogin("payment-expired@example.com", false);
        PaymentFlow expiredFlow = createFlow(
                expiredUser, offer, "expired-order", "expired-attempt");
        jdbcTemplate.update("""
                UPDATE payment_orders SET expires_at = ?
                WHERE global_order_id = ?
                """, LocalDateTime.now().minusMinutes(1), expiredFlow.orderId());
        String expiredCallback = callbackBody(
                "event-expired-review",
                expiredFlow.attempt().providerAttemptId(),
                "transaction-expired-review",
                expiredFlow.orderId(),
                "SUCCEEDED",
                1600,
                "CNY"
        );
        sendCallback(expiredCallback, signature(expiredCallback), status().isOk());
        assertThat(orderRepository.findByGlobalId(expiredFlow.orderId())
                .orElseThrow().orderStatus()).isEqualTo("PAYMENT_REVIEW_REQUIRED");
        assertPersonalFree(expiredUser);
    }

    @Test
    void teamPurchasesRequireContextualAuthorityAndFulfillTeamMembership()
            throws Exception {
        TestUser owner = registerAndLogin("payment-team-owner@example.com", false);
        TestUser member = registerAndLogin("payment-team-member@example.com", false);
        TestUser outsider = registerAndLogin("payment-team-outsider@example.com", false);
        TestUser platformAdmin = registerAndLogin("payment-team-admin@example.com", true);
        String globalTeamId = createTeam(owner, "Payment Team");
        addTeamMember(owner, globalTeamId, member);
        MembershipPlanOffer offer = createOffer(
                "TEST_TEAM_PLUS", "TEAM_PLUS", "ACTIVE",
                3000, "CNY", -1, 3);

        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(member))
                        .header("Idempotency-Key", "team-member-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(
                                offer.globalOfferId(), "TEAM", globalTeamId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(outsider))
                        .header("Idempotency-Key", "team-outsider-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(
                                offer.globalOfferId(), "TEAM", globalTeamId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(platformAdmin))
                        .header("Idempotency-Key", "team-admin-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(
                                offer.globalOfferId(), "TEAM", globalTeamId)))
                .andExpect(status().isNotFound());

        PaymentFlow flow = createFlow(
                owner, offer, "team-order", "team-attempt", globalTeamId);
        String callback = callbackBody(
                "event-team-success",
                flow.attempt().providerAttemptId(),
                "transaction-team-success",
                flow.orderId(),
                "SUCCEEDED",
                3000,
                "CNY"
        );
        sendCallback(callback, signature(callback), status().isOk());
        Long teamId = teamRepository.findByGlobalId(globalTeamId).orElseThrow().id();
        Membership membership = membershipRepository.findCurrentForTeam(teamId).orElseThrow();
        assertThat(planRepository.findById(membership.membershipPlanId())
                .orElseThrow().planCode()).isEqualTo("TEAM_PLUS");
        assertThat(membership.source()).isEqualTo("PAYMENT");
        assertThat(orderRepository.findByGlobalId(flow.orderId())
                .orElseThrow().orderStatus()).isEqualTo("FULFILLED");
    }

    @Test
    void paidFulfillmentFailureRemainsAuditableAndInternalRetryCompletesOnce()
            throws Exception {
        TestUser user = registerAndLogin("payment-retry@example.com", false);
        MembershipPlanOffer offer = createOffer(
                "TEST_RETRY_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1700, "CNY", -1, 1);
        PaymentFlow flow = createFlow(
                user, offer, "retry-order", "retry-attempt");
        jdbcTemplate.update("""
                UPDATE membership_plans SET status = 'DRAFT'
                WHERE plan_code = 'PERSONAL_PLUS' AND version = 1
                """);
        try {
            String callback = callbackBody(
                    "event-retry-success",
                    flow.attempt().providerAttemptId(),
                    "transaction-retry-success",
                    flow.orderId(),
                    "SUCCEEDED",
                    1700,
                    "CNY"
            );
            sendCallback(callback, signature(callback), status().isOk());
            PaymentOrder failed = orderRepository.findByGlobalId(
                    flow.orderId()).orElseThrow();
            assertThat(failed.orderStatus()).isEqualTo("PAID");
            assertThat(failed.paymentStatus()).isEqualTo("PAID");
            assertThat(failed.fulfillmentStatus()).isEqualTo("FAILED");
            assertThat(fulfillmentRepository.findByOrderId(failed.id())
                    .orElseThrow().status()).isEqualTo("FAILED");
            assertPersonalFree(user);
        } finally {
            jdbcTemplate.update("""
                    UPDATE membership_plans SET status = 'ACTIVE'
                    WHERE plan_code = 'PERSONAL_PLUS' AND version = 1
                    """);
        }

        PaymentOrder paid = orderRepository.findByGlobalId(flow.orderId()).orElseThrow();
        fulfillmentService.retry(paid.id(), userId(user), null);
        fulfillmentService.retry(paid.id(), userId(user), null);
        PaymentOrder fulfilled = orderRepository.findByGlobalId(
                flow.orderId()).orElseThrow();
        assertThat(fulfilled.orderStatus()).isEqualTo("FULFILLED");
        assertThat(fulfilled.fulfillmentStatus()).isEqualTo("FULFILLED");
        assertThat(membershipRepository.findHistory(
                MembershipSubject.user(userId(user)))).hasSize(2);
        assertThat(auditLogRepository.countByAction(
                "PAYMENT_FULFILLMENT_FAILED")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(
                "PAYMENT_FULFILLMENT_SUCCESS")).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(
                "PAYMENT_FULFILLMENT_RETRY")).isEqualTo(2);
    }

    @Test
    void providerTransactionCannotBeReusedAcrossPaymentAttempts() throws Exception {
        MembershipPlanOffer offer = createOffer(
                "TEST_TRANSACTION_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1800, "CNY", -1, 1);
        TestUser firstUser = registerAndLogin("payment-transaction-a@example.com", false);
        PaymentFlow first = createFlow(
                firstUser, offer, "transaction-order-a", "transaction-attempt-a");
        String firstCallback = callbackBody(
                "event-transaction-a",
                first.attempt().providerAttemptId(),
                "shared-provider-transaction",
                first.orderId(),
                "SUCCEEDED",
                1800,
                "CNY"
        );
        sendCallback(firstCallback, signature(firstCallback), status().isOk());

        TestUser secondUser = registerAndLogin("payment-transaction-b@example.com", false);
        PaymentFlow second = createFlow(
                secondUser, offer, "transaction-order-b", "transaction-attempt-b");
        String conflictingCallback = callbackBody(
                "event-transaction-b",
                second.attempt().providerAttemptId(),
                "shared-provider-transaction",
                second.orderId(),
                "SUCCEEDED",
                1800,
                "CNY"
        );
        sendCallback(
                conflictingCallback,
                signature(conflictingCallback),
                status().isOk()
        );
        assertReview(second.orderId(), "PROVIDER_TRANSACTION_CONFLICT");
        assertPersonalFree(secondUser);
    }

    @Test
    void stateMachineRejectsInvalidOrderTransition() throws Exception {
        TestUser user = registerAndLogin("payment-state@example.com", false);
        MembershipPlanOffer offer = createOffer(
                "TEST_STATE_PLUS", "PERSONAL_PLUS", "ACTIVE",
                1900, "CNY", -1, 1);
        MvcResult created = createOrder(
                user, offer.globalOfferId(), "USER", null, "state-order");
        PaymentOrder order = orderRepository.findByGlobalId(
                read(created, "/data/globalOrderId")).orElseThrow();

        assertThatThrownBy(() -> stateMachine.transitionOrder(
                order, "FULFILLED", "INVALID_TEST_TRANSITION",
                userId(user), null))
                .isInstanceOf(ConflictException.class);
        assertThat(orderRepository.findById(order.id()).orElseThrow().orderStatus())
                .isEqualTo("CREATED");
    }

    private void assertReview(String orderId, String errorCode) {
        PaymentOrder order = orderRepository.findByGlobalId(orderId).orElseThrow();
        assertThat(order.orderStatus()).isEqualTo("PAYMENT_REVIEW_REQUIRED");
        assertThat(order.paymentStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(order.fulfillmentStatus()).isEqualTo("NOT_STARTED");
        assertThat(eventRepository.findAll().stream()
                .filter(event -> errorCode.equals(event.errorCode()))
                .count()).isEqualTo(1);
    }

    private void assertPersonalFree(TestUser user) {
        Membership membership = membershipRepository.findCurrentForUser(
                userId(user)).orElseThrow();
        assertThat(planRepository.findById(membership.membershipPlanId())
                .orElseThrow().tier()).isEqualTo("FREE");
    }

    private PaymentFlow createFlow(
            TestUser user,
            MembershipPlanOffer offer,
            String orderKey,
            String attemptKey
    ) throws Exception {
        return createFlow(user, offer, orderKey, attemptKey, null);
    }

    private PaymentFlow createFlow(
            TestUser user,
            MembershipPlanOffer offer,
            String orderKey,
            String attemptKey,
            String globalTeamId
    ) throws Exception {
        String subjectType = globalTeamId == null ? "USER" : "TEAM";
        MvcResult order = createOrder(
                user, offer.globalOfferId(), subjectType, globalTeamId, orderKey);
        String globalOrderId = read(order, "/data/globalOrderId");
        MvcResult attemptResult = createAttempt(user, globalOrderId, attemptKey);
        PaymentAttempt attempt = attemptRepository.findByGlobalId(
                read(attemptResult, "/data/globalPaymentAttemptId")).orElseThrow();
        return new PaymentFlow(globalOrderId, attempt);
    }

    private MvcResult createOrder(
            TestUser user,
            String globalOfferId,
            String subjectType,
            String globalTeamId,
            String key
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/app/orders")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(globalOfferId, subjectType, globalTeamId)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult createAttempt(
            TestUser user,
            String globalOrderId,
            String key
    ) throws Exception {
        MvcResult first = mockMvc.perform(post(
                                "/api/v1/app/orders/{id}/payment-attempts",
                                globalOrderId)
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerCode\":\"LOCAL_TEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTION_REQUIRED"))
                .andReturn();
        MvcResult replay = mockMvc.perform(post(
                                "/api/v1/app/orders/{id}/payment-attempts",
                                globalOrderId)
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerCode\":\"LOCAL_TEST\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(read(replay, "/data/globalPaymentAttemptId"))
                .isEqualTo(read(first, "/data/globalPaymentAttemptId"));
        return first;
    }

    private MembershipPlanOffer createOffer(
            String offerCode,
            String planCode,
            String status,
            long amount,
            String currency,
            int validFromOffsetDays,
            int durationMonths
    ) {
        Long planId = planRepository.findActive(planCode, 1).orElseThrow().id();
        LocalDateTime validFrom = validFromOffsetDays == 0
                ? null
                : LocalDateTime.now().plusDays(validFromOffsetDays);
        return offerRepository.create(
                globalIdService.offerId(),
                offerCode,
                1,
                planId,
                status,
                currency,
                amount,
                durationMonths,
                validFrom,
                LocalDateTime.now().plusDays(30)
        );
    }

    private TestUser registerAndLogin(String email, boolean admin) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/app/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","phone":"","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String globalUserId = read(registration, "/data/globalUserId");
        if (admin) {
            permissionRepository.assignRole(
                    userRepository.findByGlobalUserId(globalUserId).orElseThrow().getId(),
                    "ADMIN");
        }
        MvcResult login = mockMvc.perform(post("/api/v1/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(globalUserId, read(login, "/data/accessToken"));
    }

    private String createTeam(TestUser owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "/data/globalTeamId");
    }

    private void addTeamMember(
            TestUser owner,
            String globalTeamId,
            TestUser member
    ) throws Exception {
        mockMvc.perform(post("/api/v1/app/teams/{id}/members", globalTeamId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"globalUserId":"%s","memberRole":"MEMBER"}
                                """.formatted(member.globalUserId())))
                .andExpect(status().isOk());
    }

    private String orderBody(
            String offerId,
            String subjectType,
            String globalTeamId
    ) {
        return """
                {
                  "globalOfferId":"%s",
                  "subjectType":"%s",
                  "globalTeamId":%s
                }
                """.formatted(
                offerId,
                subjectType,
                globalTeamId == null ? "null" : "\"" + globalTeamId + "\""
        );
    }

    private String callbackBody(
            String eventId,
            String providerAttemptId,
            String transactionId,
            String globalOrderId,
            String paymentStatus,
            long amount,
            String currency
    ) {
        return """
                {
                  "providerEventId":"%s",
                  "eventType":"PAYMENT_STATUS_CHANGED",
                  "providerAttemptId":"%s",
                  "providerTransactionId":"%s",
                  "globalOrderId":"%s",
                  "paymentStatus":"%s",
                  "paidAmountMinor":%d,
                  "currency":"%s",
                  "providerEventTime":"%s"
                }
                """.formatted(
                eventId,
                providerAttemptId,
                transactionId,
                globalOrderId,
                paymentStatus,
                amount,
                currency,
                LocalDateTime.now().withNano(0)
        );
    }

    private MvcResult sendCallback(
            String body,
            String signature,
            org.springframework.test.web.servlet.ResultMatcher matcher
    ) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/integrations/payments/LOCAL_TEST/callbacks")
                        .header("X-Local-Test-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(matcher)
                .andReturn();
    }

    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SIGNING_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.accessToken();
    }

    private Long userId(TestUser user) {
        return userRepository.findByGlobalUserId(
                user.globalUserId()).orElseThrow().getId();
    }

    private String read(MvcResult result, String pointer) throws Exception {
        JsonNode node = objectMapper.readTree(
                result.getResponse().getContentAsString()).at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private long readLong(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).at(pointer).asLong();
    }

    private boolean readBoolean(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).at(pointer).asBoolean();
    }

    private void clearAll() {
        jdbcTemplate.update("DELETE FROM payment_order_state_transitions");
        jdbcTemplate.update("DELETE FROM payment_order_fulfillments");
        jdbcTemplate.update("DELETE FROM payment_events");
        jdbcTemplate.update("DELETE FROM payment_attempts");
        jdbcTemplate.update("DELETE FROM payment_order_items");
        jdbcTemplate.update("DELETE FROM payment_orders");
        jdbcTemplate.update("DELETE FROM membership_plan_offers");
        jdbcTemplate.update("""
                UPDATE membership_plans SET status = 'ACTIVE'
                WHERE plan_code IN (
                    'PERSONAL_FREE', 'PERSONAL_PLUS', 'PERSONAL_PRO',
                    'TEAM_PLUS', 'TEAM_PRO'
                )
                """);
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
    }

    private record TestUser(String globalUserId, String accessToken) {
    }

    private record PaymentFlow(String orderId, PaymentAttempt attempt) {
    }
}
