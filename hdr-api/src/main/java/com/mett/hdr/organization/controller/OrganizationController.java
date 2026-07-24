package com.mett.hdr.organization.controller;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.organization.dto.OrganizationCreateRequest;
import com.mett.hdr.organization.dto.OrganizationMemberRequest;
import com.mett.hdr.organization.dto.OrganizationMemberResponse;
import com.mett.hdr.organization.dto.OrganizationMemberUpdateRequest;
import com.mett.hdr.organization.dto.OrganizationOwnershipTransferRequest;
import com.mett.hdr.organization.dto.OrganizationResponse;
import com.mett.hdr.organization.dto.OrganizationUpdateRequest;
import com.mett.hdr.organization.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ApiResponse<OrganizationResponse> create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody OrganizationCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.create(authorization, request, servletRequest));
    }

    @GetMapping
    public ApiResponse<List<OrganizationResponse>> list(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(organizationService.list(authorization));
    }

    @GetMapping("/{globalOrganizationId}")
    public ApiResponse<OrganizationResponse> get(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId
    ) {
        return ApiResponse.success(organizationService.get(authorization, globalOrganizationId));
    }

    @PatchMapping("/{globalOrganizationId}")
    public ApiResponse<OrganizationResponse> update(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId,
            @Valid @RequestBody OrganizationUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.update(
                authorization, globalOrganizationId, request, servletRequest));
    }

    @GetMapping("/{globalOrganizationId}/members")
    public ApiResponse<List<OrganizationMemberResponse>> members(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId
    ) {
        return ApiResponse.success(organizationService.members(authorization, globalOrganizationId));
    }

    @PostMapping("/{globalOrganizationId}/members")
    public ApiResponse<OrganizationMemberResponse> addMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId,
            @Valid @RequestBody OrganizationMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.addMember(
                authorization, globalOrganizationId, request, servletRequest));
    }

    @PatchMapping("/{globalOrganizationId}/members/{globalUserId}")
    public ApiResponse<OrganizationMemberResponse> updateMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId,
            @PathVariable String globalUserId,
            @Valid @RequestBody OrganizationMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.updateMember(
                authorization, globalOrganizationId, globalUserId, request, servletRequest));
    }

    @DeleteMapping("/{globalOrganizationId}/members/{globalUserId}")
    public ApiResponse<OrganizationMemberResponse> removeMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId,
            @PathVariable String globalUserId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.removeMember(
                authorization, globalOrganizationId, globalUserId, servletRequest));
    }

    @PostMapping("/{globalOrganizationId}/ownership-transfer")
    public ApiResponse<OrganizationResponse> transferOwnership(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalOrganizationId,
            @Valid @RequestBody OrganizationOwnershipTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(organizationService.transferOwnership(
                authorization, globalOrganizationId, request, servletRequest));
    }
}
