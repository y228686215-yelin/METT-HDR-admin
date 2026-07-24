package com.mett.hdr.membership.controller;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.membership.dto.EntitlementResponse;
import com.mett.hdr.membership.dto.MembershipResponse;
import com.mett.hdr.membership.dto.UsageResponse;
import com.mett.hdr.membership.service.MembershipQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class MembershipController {

    private final MembershipQueryService membershipQueryService;

    public MembershipController(MembershipQueryService membershipQueryService) {
        this.membershipQueryService = membershipQueryService;
    }

    @GetMapping("/memberships/me")
    public ApiResponse<MembershipResponse> personalMembership(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(
                membershipQueryService.personalMembership(authorization));
    }

    @GetMapping("/memberships/me/entitlements")
    public ApiResponse<List<EntitlementResponse>> personalEntitlements(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(
                membershipQueryService.personalEntitlements(authorization));
    }

    @GetMapping("/memberships/me/usage")
    public ApiResponse<List<UsageResponse>> personalUsage(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(
                membershipQueryService.personalUsage(authorization));
    }

    @GetMapping("/teams/{globalTeamId}/membership")
    public ApiResponse<MembershipResponse> teamMembership(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId
    ) {
        return ApiResponse.success(
                membershipQueryService.teamMembership(authorization, globalTeamId));
    }

    @GetMapping("/teams/{globalTeamId}/membership/entitlements")
    public ApiResponse<List<EntitlementResponse>> teamEntitlements(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId
    ) {
        return ApiResponse.success(
                membershipQueryService.teamEntitlements(authorization, globalTeamId));
    }

    @GetMapping("/teams/{globalTeamId}/membership/usage")
    public ApiResponse<List<UsageResponse>> teamUsage(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId
    ) {
        return ApiResponse.success(
                membershipQueryService.teamUsage(authorization, globalTeamId));
    }
}
