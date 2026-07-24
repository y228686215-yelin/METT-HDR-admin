package com.mett.hdr.team.controller;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.team.dto.TeamCreateRequest;
import com.mett.hdr.team.dto.TeamManagerTransferRequest;
import com.mett.hdr.team.dto.TeamMemberRequest;
import com.mett.hdr.team.dto.TeamMemberResponse;
import com.mett.hdr.team.dto.TeamMemberUpdateRequest;
import com.mett.hdr.team.dto.TeamResponse;
import com.mett.hdr.team.dto.TeamUpdateRequest;
import com.mett.hdr.team.service.TeamService;
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
@RequestMapping("/api/v1/app/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ApiResponse<TeamResponse> create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody TeamCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.create(authorization, request, servletRequest));
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> list(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.success(teamService.list(authorization));
    }

    @GetMapping("/{globalTeamId}")
    public ApiResponse<TeamResponse> get(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId
    ) {
        return ApiResponse.success(teamService.get(authorization, globalTeamId));
    }

    @PatchMapping("/{globalTeamId}")
    public ApiResponse<TeamResponse> update(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId,
            @Valid @RequestBody TeamUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.update(authorization, globalTeamId, request, servletRequest));
    }

    @GetMapping("/{globalTeamId}/members")
    public ApiResponse<List<TeamMemberResponse>> members(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId
    ) {
        return ApiResponse.success(teamService.members(authorization, globalTeamId));
    }

    @PostMapping("/{globalTeamId}/members")
    public ApiResponse<TeamMemberResponse> addMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId,
            @Valid @RequestBody TeamMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.addMember(
                authorization, globalTeamId, request, servletRequest));
    }

    @PatchMapping("/{globalTeamId}/members/{globalUserId}")
    public ApiResponse<TeamMemberResponse> updateMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId,
            @PathVariable String globalUserId,
            @Valid @RequestBody TeamMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.updateMember(
                authorization, globalTeamId, globalUserId, request, servletRequest));
    }

    @DeleteMapping("/{globalTeamId}/members/{globalUserId}")
    public ApiResponse<TeamMemberResponse> removeMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId,
            @PathVariable String globalUserId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.removeMember(
                authorization, globalTeamId, globalUserId, servletRequest));
    }

    @PostMapping("/{globalTeamId}/manager-transfer")
    public ApiResponse<TeamResponse> transferManager(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalTeamId,
            @Valid @RequestBody TeamManagerTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(teamService.transferManager(
                authorization, globalTeamId, request, servletRequest));
    }
}
