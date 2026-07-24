package com.mett.hdr.auth.security;

import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.auth.token.JwtTokenService;
import com.mett.hdr.common.exception.UnauthorizedException;
import com.mett.hdr.identity.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class CurrentActorService {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public CurrentActorService(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    public AuthenticatedUser require(String authorizationHeader) {
        AuthenticatedUser actor = jwtTokenService.parse(authorizationHeader);
        if (userRepository.findById(actor.userId()).isEmpty()) {
            throw new UnauthorizedException("Invalid access token user.");
        }
        return actor;
    }
}
