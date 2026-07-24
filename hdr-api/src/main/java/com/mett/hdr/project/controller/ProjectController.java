package com.mett.hdr.project.controller;

import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.project.dto.ProjectCreateRequest;
import com.mett.hdr.project.dto.ProjectManagerTransferRequest;
import com.mett.hdr.project.dto.ProjectMemberRequest;
import com.mett.hdr.project.dto.ProjectMemberResponse;
import com.mett.hdr.project.dto.ProjectMemberUpdateRequest;
import com.mett.hdr.project.dto.ProjectResponse;
import com.mett.hdr.project.dto.ProjectSpaceCreateRequest;
import com.mett.hdr.project.dto.ProjectSpaceResponse;
import com.mett.hdr.project.dto.ProjectSpaceUpdateRequest;
import com.mett.hdr.project.dto.ProjectUpdateRequest;
import com.mett.hdr.project.service.ProjectMemberService;
import com.mett.hdr.project.service.ProjectService;
import com.mett.hdr.project.service.ProjectSpaceService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService memberService;
    private final ProjectSpaceService spaceService;

    public ProjectController(
            ProjectService projectService,
            ProjectMemberService memberService,
            ProjectSpaceService spaceService
    ) {
        this.projectService = projectService;
        this.memberService = memberService;
        this.spaceService = spaceService;
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProjectCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.create(authorization, request, servletRequest));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String ownershipScope
    ) {
        return ApiResponse.success(projectService.list(
                authorization, status, projectType, ownershipScope));
    }

    @GetMapping("/{globalProjectId}")
    public ApiResponse<ProjectResponse> get(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId
    ) {
        return ApiResponse.success(projectService.get(authorization, globalProjectId));
    }

    @PatchMapping("/{globalProjectId}")
    public ApiResponse<ProjectResponse> update(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @Valid @RequestBody ProjectUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.update(
                authorization, globalProjectId, request, servletRequest));
    }

    @PostMapping("/{globalProjectId}/activate")
    public ApiResponse<ProjectResponse> activate(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.activate(
                authorization, globalProjectId, servletRequest));
    }

    @PostMapping("/{globalProjectId}/archive")
    public ApiResponse<ProjectResponse> archive(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.archive(
                authorization, globalProjectId, servletRequest));
    }

    @PostMapping("/{globalProjectId}/restore")
    public ApiResponse<ProjectResponse> restore(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.restore(
                authorization, globalProjectId, servletRequest));
    }

    @PostMapping("/{globalProjectId}/manager-transfer")
    public ApiResponse<ProjectResponse> transferManager(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @Valid @RequestBody ProjectManagerTransferRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(projectService.transferManager(
                authorization, globalProjectId, request, servletRequest));
    }

    @GetMapping("/{globalProjectId}/members")
    public ApiResponse<List<ProjectMemberResponse>> members(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId
    ) {
        return ApiResponse.success(memberService.list(authorization, globalProjectId));
    }

    @PostMapping("/{globalProjectId}/members")
    public ApiResponse<ProjectMemberResponse> addMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @Valid @RequestBody ProjectMemberRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(memberService.add(
                authorization, globalProjectId, request, servletRequest));
    }

    @PatchMapping("/{globalProjectId}/members/{globalUserId}")
    public ApiResponse<ProjectMemberResponse> updateMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalUserId,
            @Valid @RequestBody ProjectMemberUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(memberService.update(
                authorization, globalProjectId, globalUserId, request, servletRequest));
    }

    @DeleteMapping("/{globalProjectId}/members/{globalUserId}")
    public ApiResponse<ProjectMemberResponse> removeMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalUserId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(memberService.remove(
                authorization, globalProjectId, globalUserId, servletRequest));
    }

    @GetMapping("/{globalProjectId}/spaces")
    public ApiResponse<List<ProjectSpaceResponse>> spaces(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId
    ) {
        return ApiResponse.success(spaceService.list(authorization, globalProjectId));
    }

    @PostMapping("/{globalProjectId}/spaces")
    public ApiResponse<ProjectSpaceResponse> createSpace(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @Valid @RequestBody ProjectSpaceCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(spaceService.create(
                authorization, globalProjectId, request, servletRequest));
    }

    @GetMapping("/{globalProjectId}/spaces/{globalSpaceId}")
    public ApiResponse<ProjectSpaceResponse> getSpace(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalSpaceId
    ) {
        return ApiResponse.success(spaceService.get(
                authorization, globalProjectId, globalSpaceId));
    }

    @PatchMapping("/{globalProjectId}/spaces/{globalSpaceId}")
    public ApiResponse<ProjectSpaceResponse> updateSpace(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalSpaceId,
            @Valid @RequestBody ProjectSpaceUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(spaceService.update(
                authorization, globalProjectId, globalSpaceId, request, servletRequest));
    }

    @PostMapping("/{globalProjectId}/spaces/{globalSpaceId}/archive")
    public ApiResponse<ProjectSpaceResponse> archiveSpace(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalSpaceId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(spaceService.archive(
                authorization, globalProjectId, globalSpaceId, servletRequest));
    }

    @PostMapping("/{globalProjectId}/spaces/{globalSpaceId}/restore")
    public ApiResponse<ProjectSpaceResponse> restoreSpace(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String globalProjectId,
            @PathVariable String globalSpaceId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(spaceService.restore(
                authorization, globalProjectId, globalSpaceId, servletRequest));
    }
}
