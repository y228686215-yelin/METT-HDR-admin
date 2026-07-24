package com.mett.hdr.payment.controller;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.payment.dto.PaymentCallbackResponse;
import com.mett.hdr.payment.service.PaymentCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/payments")
public class PaymentCallbackController {

    private final PaymentCallbackService callbackService;

    public PaymentCallbackController(PaymentCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/{providerCode}/callbacks")
    public ApiResponse<PaymentCallbackResponse> callback(
            @PathVariable String providerCode,
            @RequestHeader(
                    name = "X-Local-Test-Signature",
                    required = false
            ) String signature,
            @RequestBody String rawBody,
            HttpServletRequest request
    ) {
        return ApiResponse.success(callbackService.process(
                providerCode, rawBody, signature, request));
    }
}
