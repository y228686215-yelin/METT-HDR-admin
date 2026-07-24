package com.mett.hdr.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.common.exception.SystemException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.payment.dto.PaymentOrderCreateRequest;
import com.mett.hdr.payment.dto.PaymentOrderResponse;
import com.mett.hdr.payment.entity.MembershipPlanOffer;
import com.mett.hdr.payment.entity.PaymentOrder;
import com.mett.hdr.payment.entity.PaymentOrderItem;
import com.mett.hdr.payment.model.PurchaseSubject;
import com.mett.hdr.payment.repository.MembershipPlanOfferRepository;
import com.mett.hdr.payment.repository.PaymentOrderItemRepository;
import com.mett.hdr.payment.repository.PaymentOrderRepository;
import com.mett.hdr.team.repository.TeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderService {

    private final CurrentActorService currentActorService;
    private final MembershipOfferQueryService offerQueryService;
    private final PaymentPurchaseAuthorizationService authorizationService;
    private final MembershipPlanOfferRepository offerRepository;
    private final MembershipPlanRepository planRepository;
    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderItemRepository itemRepository;
    private final PaymentOrderStateMachine stateMachine;
    private final TeamRepository teamRepository;
    private final GlobalIdService globalIdService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final long orderExpiryMinutes;

    public PaymentOrderService(
            CurrentActorService currentActorService,
            MembershipOfferQueryService offerQueryService,
            PaymentPurchaseAuthorizationService authorizationService,
            MembershipPlanOfferRepository offerRepository,
            MembershipPlanRepository planRepository,
            PaymentOrderRepository orderRepository,
            PaymentOrderItemRepository itemRepository,
            PaymentOrderStateMachine stateMachine,
            TeamRepository teamRepository,
            GlobalIdService globalIdService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${mett.hdr.payment.order-expiry-minutes:30}") long orderExpiryMinutes
    ) {
        if (orderExpiryMinutes <= 0) {
            throw new IllegalStateException("Payment order expiry must be positive.");
        }
        this.currentActorService = currentActorService;
        this.offerQueryService = offerQueryService;
        this.authorizationService = authorizationService;
        this.offerRepository = offerRepository;
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.stateMachine = stateMachine;
        this.teamRepository = teamRepository;
        this.globalIdService = globalIdService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.orderExpiryMinutes = orderExpiryMinutes;
    }

    @Transactional
    public PaymentOrderResponse create(
            String authorization,
            String idempotencyKey,
            PaymentOrderCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        String key = requireIdempotencyKey(idempotencyKey);
        PaymentOrder existing = orderRepository.findByPurchaserAndKey(
                actor.userId(), key).orElse(null);
        if (existing != null) {
            requireSameRequest(existing, request);
            requireVisible(existing, actor.userId());
            return toResponse(existing);
        }

        MembershipPlanOffer offer = offerQueryService.requireAvailable(
                request.globalOfferId());
        MembershipPlan plan = planRepository.findById(
                offer.membershipPlanId()).orElseThrow();
        PurchaseSubject subject = authorizationService.authorize(actor, request, plan);
        PaymentOrder order = orderRepository.create(
                globalIdService.orderId(),
                globalIdService.orderNumber(),
                subject.subjectType(),
                subject.userId(),
                subject.teamId(),
                actor.userId(),
                offer.currency(),
                offer.amountMinor(),
                key,
                now().plusMinutes(orderExpiryMinutes)
        );
        itemRepository.create(
                order.id(),
                offer.id(),
                plan.id(),
                offer.currency(),
                offer.amountMinor(),
                plan.planCode(),
                plan.version(),
                plan.tier(),
                offer.offerCode(),
                offer.version(),
                offer.membershipDurationMonths(),
                itemSnapshot(offer, plan)
        );
        stateMachine.recordInitial(order, actor.userId());
        auditService.record(
                actor.userId(),
                "PAYMENT_ORDER_CREATE",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                servletRequest
        );
        return toResponse(order);
    }

    public List<PaymentOrderResponse> list(String authorization) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        return orderRepository.findByPurchaser(actor.userId()).stream()
                .filter(order -> "USER".equals(order.subjectType())
                        || authorizationService.canViewTeamOrder(
                        order.teamId(), actor.userId()))
                .map(this::toResponse)
                .toList();
    }

    public PaymentOrderResponse detail(
            String authorization,
            String globalOrderId
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        PaymentOrder order = orderRepository.findByGlobalId(globalOrderId)
                .orElseThrow(() -> new NotFoundException("Payment order not found."));
        requireVisible(order, actor.userId());
        return toResponse(order);
    }

    @Transactional
    public PaymentOrderResponse cancel(
            String authorization,
            String globalOrderId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        PaymentOrder order = orderRepository.lockByGlobalId(globalOrderId)
                .orElseThrow(() -> new NotFoundException("Payment order not found."));
        requireVisible(order, actor.userId());
        order = expireIfNeeded(order, servletRequest);
        if (java.util.Set.of("PAID", "FULFILLED").contains(order.orderStatus())
                || "PAID".equals(order.paymentStatus())) {
            throw new ConflictException("Paid order cannot be cancelled.");
        }
        if ("CANCELLED".equals(order.orderStatus())) {
            return toResponse(order);
        }
        if (!java.util.Set.of(
                "CREATED", "PAYMENT_PENDING", "PAYMENT_FAILED")
                .contains(order.orderStatus())) {
            throw new ConflictException("Order cannot be cancelled.");
        }
        order = stateMachine.transitionOrder(
                order, "CANCELLED", "USER_CANCELLED",
                actor.userId(), null);
        auditService.record(
                actor.userId(),
                "PAYMENT_ORDER_CANCEL",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                servletRequest
        );
        return toResponse(order);
    }

    public PaymentOrder requireVisibleOrder(
            String authorization,
            String globalOrderId
    ) {
        AuthenticatedUser actor = currentActorService.require(authorization);
        PaymentOrder order = orderRepository.findByGlobalId(globalOrderId)
                .orElseThrow(() -> new NotFoundException("Payment order not found."));
        requireVisible(order, actor.userId());
        return order;
    }

    public PaymentOrderResponse toResponse(PaymentOrder order) {
        PaymentOrderItem item = itemRepository.findByOrderId(order.id()).orElseThrow();
        String globalTeamId = order.teamId() == null
                ? null
                : teamRepository.findById(order.teamId()).orElseThrow().globalTeamId();
        return new PaymentOrderResponse(
                order.globalOrderId(),
                order.orderNumber(),
                order.subjectType(),
                globalTeamId,
                item.planCodeSnapshot(),
                item.planVersionSnapshot(),
                item.tierSnapshot(),
                item.offerCodeSnapshot(),
                item.offerVersionSnapshot(),
                order.totalAmountMinor(),
                order.currency(),
                order.orderStatus(),
                order.paymentStatus(),
                order.fulfillmentStatus(),
                order.createdAt(),
                order.expiresAt()
        );
    }

    public PaymentOrder expireIfNeeded(
            PaymentOrder order,
            HttpServletRequest servletRequest
    ) {
        if (now().isBefore(order.expiresAt())
                || !java.util.Set.of(
                "CREATED", "PAYMENT_PENDING", "PAYMENT_FAILED")
                .contains(order.orderStatus())) {
            return order;
        }
        PaymentOrder expired = stateMachine.transitionOrder(
                order, "EXPIRED", "ORDER_EXPIRED", null, null);
        auditService.record(
                null,
                "PAYMENT_ORDER_EXPIRE",
                "PAYMENT_ORDER",
                order.globalOrderId(),
                servletRequest
        );
        return expired;
    }

    private void requireSameRequest(
            PaymentOrder existing,
            PaymentOrderCreateRequest request
    ) {
        PaymentOrderItem item = itemRepository.findByOrderId(existing.id()).orElseThrow();
        MembershipPlanOffer offer = offerRepository.findById(
                item.membershipPlanOfferId()).orElseThrow();
        String requestedType = request.subjectType() == null
                ? ""
                : request.subjectType().trim().toUpperCase(Locale.ROOT);
        boolean teamMatches = "USER".equals(existing.subjectType())
                ? request.globalTeamId() == null || request.globalTeamId().isBlank()
                : teamRepository.findById(existing.teamId())
                .map(team -> team.globalTeamId().equals(request.globalTeamId()))
                .orElse(false);
        if (!offer.globalOfferId().equals(request.globalOfferId())
                || !existing.subjectType().equals(requestedType)
                || !teamMatches) {
            throw new ConflictException(
                    "Idempotency key conflicts with an existing order.");
        }
    }

    private void requireVisible(PaymentOrder order, Long actorUserId) {
        if (!order.purchaserUserId().equals(actorUserId)) {
            throw new NotFoundException("Payment order not found.");
        }
        if ("TEAM".equals(order.subjectType())
                && !authorizationService.canViewTeamOrder(
                order.teamId(), actorUserId)) {
            throw new NotFoundException("Payment order not found.");
        }
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 128) {
            throw new BadRequestException("Valid Idempotency-Key is required.");
        }
        return key.trim();
    }

    private String itemSnapshot(
            MembershipPlanOffer offer,
            MembershipPlan plan
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("offerCode", offer.offerCode());
        snapshot.put("offerVersion", offer.version());
        snapshot.put("planCode", plan.planCode());
        snapshot.put("planVersion", plan.version());
        snapshot.put("tier", plan.tier());
        snapshot.put("audienceType", plan.audienceType());
        snapshot.put("currency", offer.currency());
        snapshot.put("amountMinor", offer.amountMinor());
        snapshot.put("membershipDurationMonths", offer.membershipDurationMonths());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new SystemException("Unable to create order item snapshot.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
