package com.mett.hdr.identity.provider;

import com.mett.hdr.auth.security.PasswordService;
import com.mett.hdr.common.exception.UnauthorizedException;
import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.entity.UserStatus;
import com.mett.hdr.identity.repository.UserRepository;
import com.mett.hdr.permission.service.PermissionService;
import org.springframework.stereotype.Component;

@Component
public class HdrLocalIdentityProvider implements IdentityProvider {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final PermissionService permissionService;

    public HdrLocalIdentityProvider(UserRepository userRepository, PasswordService passwordService, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.permissionService = permissionService;
    }

    @Override
    public UserIdentity authenticate(Credential credential) {
        User user = userRepository.findByAccount(credential.account())
                .orElseThrow(() -> new UnauthorizedException("Invalid account or password."));
        if (user.getStatus() != UserStatus.ACTIVE || !passwordService.matches(credential.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid account or password.");
        }
        return new UserIdentity(
                user.getId(),
                user.getGlobalUserId(),
                user.getIdentitySource().name(),
                permissionService.rolesForUser(user.getId())
        );
    }
}
