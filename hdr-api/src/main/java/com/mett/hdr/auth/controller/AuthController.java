package com.mett.hdr.auth.controller;

import com.mett.hdr.auth.dto.CurrentTokenBundle;
import com.mett.hdr.auth.dto.LoginRequest;
import com.mett.hdr.auth.dto.LogoutResponse;
import com.mett.hdr.auth.dto.RegisterRequest;
import com.mett.hdr.auth.service.AuthService;
import com.mett.hdr.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/auth")
public class AuthController {

    public static final String REFRESH_TOKEN_COOKIE = "HDR_REFRESH_TOKEN";

    private final AuthService authService;
    private final boolean refreshCookieSecure;

    public AuthController(
            AuthService authService,
            @Value("${mett.hdr.auth.refresh-cookie.secure:false}") boolean refreshCookieSecure
    ) {
        this.authService = authService;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.register(request, servletRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        CurrentTokenBundle bundle = authService.login(request, servletRequest);
        return withRefreshCookie(bundle);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest
    ) {
        CurrentTokenBundle bundle = authService.refresh(refreshToken, servletRequest);
        return withRefreshCookie(bundle);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest
    ) {
        authService.logout(refreshToken, servletRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success(new LogoutResponse("LOGOUT_SUCCESS")));
    }

    private ResponseEntity<ApiResponse<Object>> withRefreshCookie(CurrentTokenBundle bundle) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(bundle.refreshToken()).toString())
                .body(ApiResponse.success(bundle.response()));
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/app/auth")
                .maxAge(Duration.ofDays(30))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/app/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
