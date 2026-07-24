package com.mett.hdr.auth.service;

import com.mett.hdr.auth.entity.AuthRefreshToken;
import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final long ttlSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${mett.hdr.auth.refresh-token-ttl-seconds:2592000}") long ttlSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long userId) {
        String rawToken = generateRawToken();
        AuthRefreshToken token = new AuthRefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    public AuthRefreshToken requireValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token.");
        }
        AuthRefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token is no longer valid.");
        }
        return token;
    }

    public void revoke(String rawToken) {
        AuthRefreshToken token = requireValid(rawToken);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token.", ex);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
