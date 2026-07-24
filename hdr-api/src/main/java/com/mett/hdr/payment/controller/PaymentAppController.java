package com.mett.hdr.payment.controller;

import com.mett.hdr.auth.security.CurrentActorService;
import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.payment.dto.MembershipOfferResponse;
import com.mett.hdr.payment.dto.PaymentAttemptRequest;
import com.mett.hdr.payment.dto.PaymentAttemptResponse;
import com.mett.hdr.payment.dto.PaymentOrderCreateRequest;
import com.mett.hdr.payment.dto.PaymentOrderResponse;
import com.mett.hdr.payment.service.MembershipOfferQueryService;
import com.mett.hdr.payment.service.PaymentAttemptService;
import com.mett.hdr.payment.service.PaymentOrderService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class PaymentAppController {

    private final CurrentActorService currentActorService;
    private final MembershipOfferQueryService offerQueryService;
    private final PaymentOrderService orderService;
    private final PaymentAttemptService attemptService;

    public PaymentAppController(
            CurrentActorService currentActorService,
            MembershipOfferQueryService offerQueryService,
            PaymentOrderService orderService,
            PaymentAttemptService attemptService
    ) {
        this.currentActorService = currentActorService;
        this.offerQueryService = offerQueryService;
        this.orderService = orderService;
        this.attemptService = attemptService;
    }

    @GetMapping("/membership-offers")
    public ApiResponse<List<MembershipOfferResponse>> offers(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "audienceType", required = false) String audienceType
    ) {
        currentActorService.require(authorization);
        return ApiResponse.success(offerQueryService.available(audienceType));
    }

    @PostMapping("/orders")
    public ApiResponse<PaymentOrderResponse> createOrder(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentOrderCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(orderService.create(
                authorization, idempotencyKey, request, servletRequest));
    }

    @GetMapping("/orders")
    public ApiResponse<List<PaymentOrderResponse>> orders(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(orderService.list(authorization));
    }

    @GetMapping("/orders/{globalOrderId}")
    public ApiResponse<PaymentOrderResponse> order(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrderId
    ) {
        return ApiResponse.success(orderService.detail(authorization, globalOrderId));
    }

    @PostMapping("/orders/{globalOrderId}/payment-attempts")
    public ApiResponse<PaymentAttemptResponse> createAttempt(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String globalOrderId,
            @RequestBody PaymentAttemptRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(attemptService.create(
                authorization,
                globalOrderId,
                idempotencyKey,
                request,
                servletRequest
        ));
    }

    @PostMapping("/orders/{globalOrderId}/cancel")
    public ApiResponse<PaymentOrderResponse> cancel(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrderId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(orderService.cancel(
                authorization, globalOrderId, servletRequest));
    }
}
