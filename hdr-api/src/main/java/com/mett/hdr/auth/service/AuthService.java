package com.mett.hdr.auth.service;

import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.auth.dto.CurrentTokenBundle;
import com.mett.hdr.auth.dto.LoginRequest;
import com.mett.hdr.auth.dto.LoginResponse;
import com.mett.hdr.auth.dto.RefreshResponse;
import com.mett.hdr.auth.dto.RegisterRequest;
import com.mett.hdr.auth.dto.RegisterResponse;
import com.mett.hdr.auth.entity.AuthRefreshToken;
import com.mett.hdr.auth.security.PasswordService;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.auth.token.JwtTokenService;
import com.mett.hdr.auth.token.TokenIssueResult;
import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.common.exception.UnauthorizedException;
import com.mett.hdr.foundation.id.GlobalIdService;
import com.mett.hdr.identity.dto.CurrentUserResponse;
import com.mett.hdr.identity.entity.IdentitySource;
import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.entity.UserStatus;
import com.mett.hdr.identity.provider.Credential;
import com.mett.hdr.identity.provider.HdrLocalIdentityProvider;
import com.mett.hdr.identity.provider.UserIdentity;
import com.mett.hdr.identity.repository.UserProfileRepository;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.membership.service.MembershipProvisioningService;
import com.mett.hdr.permission.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordService passwordService;
    private final GlobalIdService globalIdService;
    private final PermissionService permissionService;
    private final HdrLocalIdentityProvider hdrLocalIdentityProvider;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final MembershipProvisioningService membershipProvisioningService;

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            PasswordService passwordService,
            GlobalIdService globalIdService,
            PermissionService permissionService,
            HdrLocalIdentityProvider hdrLocalIdentityProvider,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            MembershipProvisioningService membershipProvisioningService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordService = passwordService;
        this.globalIdService = globalIdService;
        this.permissionService = permissionService;
        this.hdrLocalIdentityProvider = hdrLocalIdentityProvider;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.membershipProvisioningService = membershipProvisioningService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
        if (isBlank(request.email()) && isBlank(request.phone())) {
            throw new BadRequestException("Email or phone is required.");
        }
        if (!isBlank(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already registered.");
        }
        if (!isBlank(request.phone()) && userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("Phone is already registered.");
        }
        User user = new User();
        user.setGlobalUserId(globalIdService.userId());
        user.setIdentitySource(IdentitySource.HDR);
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        user.setUsername(blankToNull(request.email()));
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        userProfileRepository.createDefault(saved.getId(), defaultDisplayName(saved));
        permissionService.assignDefaultUserRole(saved.getId());
        auditService.record(saved.getId(), "REGISTER", "USER", saved.getGlobalUserId(), servletRequest);
        membershipProvisioningService.provisionDefaultPersonalFreeMembership(
                saved.getId(), servletRequest);
        return new RegisterResponse(saved.getGlobalUserId(), "REGISTER_SUCCESS");
    }

    public CurrentTokenBundle login(LoginRequest request, HttpServletRequest servletRequest) {
        try {
            UserIdentity identity = hdrLocalIdentityProvider.authenticate(new Credential(request.account(), request.password()));
            User user = userRepository.findById(identity.userId())
                    .orElseThrow(() -> new UnauthorizedException("Invalid account or password."));
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            TokenIssueResult token = jwtTokenService.issue(identity.userId(), identity.globalUserId(), identity.roles());
            String refreshToken = refreshTokenService.issue(identity.userId());
            auditService.record(identity.userId(), "LOGIN_SUCCESS", "USER", identity.globalUserId(), servletRequest);
            return new CurrentTokenBundle(new LoginResponse(token.accessToken(), token.expiresIn()), refreshToken);
        } catch (UnauthorizedException ex) {
            auditService.record(null, "LOGIN_FAILED", "USER", request.account(), servletRequest);
            throw ex;
        }
    }

    public CurrentTokenBundle refresh(String rawRefreshToken, HttpServletRequest servletRequest) {
        AuthRefreshToken storedRefreshToken = refreshTokenService.requireValid(rawRefreshToken);
        User user = userRepository.findById(storedRefreshToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));
        List<String> roles = permissionService.rolesForUser(user.getId());
        TokenIssueResult token = jwtTokenService.issue(user.getId(), user.getGlobalUserId(), roles);
        String newRefreshToken = refreshTokenService.issue(user.getId());
        refreshTokenService.revoke(rawRefreshToken);
        auditService.record(user.getId(), "TOKEN_REFRESH", "USER", user.getGlobalUserId(), servletRequest);
        return new CurrentTokenBundle(new RefreshResponse(token.accessToken(), token.expiresIn()), newRefreshToken);
    }

    public void logout(String rawRefreshToken, HttpServletRequest servletRequest) {
        AuthRefreshToken token = refreshTokenService.requireValid(rawRefreshToken);
        refreshTokenService.revoke(rawRefreshToken);
        auditService.record(token.getUserId(), "LOGOUT", "USER", token.getUserId().toString(), servletRequest);
    }

    public CurrentUserResponse currentUser(String authorizationHeader) {
        AuthenticatedUser authenticatedUser = jwtTokenService.parse(authorizationHeader);
        User user = userRepository.findById(authenticatedUser.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid access token user."));
        return new CurrentUserResponse(user.getGlobalUserId(), user.getIdentitySource().name(), authenticatedUser.roles());
    }

    private String defaultDisplayName(User user) {
        if (!isBlank(user.getEmail())) {
            return user.getEmail();
        }
        if (!isBlank(user.getPhone())) {
            return user.getPhone();
        }
        return user.getGlobalUserId();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
