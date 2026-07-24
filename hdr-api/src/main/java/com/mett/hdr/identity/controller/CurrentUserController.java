package com.mett.hdr.identity.controller;

import com.mett.hdr.auth.service.AuthService;
import com.mett.hdr.common.response.ApiResponse;
import com.mett.hdr.identity.dto.CurrentUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/users")
public class CurrentUserController {

    private final AuthService authService;

    public CurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        return ApiResponse.success(authService.currentUser(authorizationHeader));
    }
}
