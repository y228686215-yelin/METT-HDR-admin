package com.mett.hdr.auth.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String signingSecret;
    private final long accessTokenTtlSeconds;

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${mett.hdr.auth.jwt.signing-secret:local-development-signing-key-change-me}") String signingSecret,
            @Value("${mett.hdr.auth.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public TokenIssueResult issue(Long userId, String globalUserId, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "userId", userId,
                "globalUserId", globalUserId,
                "roles", roles,
                "iat", now,
                "exp", now + accessTokenTtlSeconds
        );
        String unsignedToken = encodeJson(header) + "." + encodeJson(claims);
        return new TokenIssueResult(unsignedToken + "." + sign(unsignedToken), accessTokenTtlSeconds);
    }

    public AuthenticatedUser parse(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing access token.");
        }
        String token = bearerToken.substring("Bearer ".length());
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid access token.");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new UnauthorizedException("Invalid access token signature.");
        }
        Map<String, Object> claims = decodeJson(parts[1]);
        long exp = ((Number) claims.get("exp")).longValue();
        if (Instant.now().getEpochSecond() >= exp) {
            throw new UnauthorizedException("Access token expired.");
        }
        Long userId = ((Number) claims.get("userId")).longValue();
        String globalUserId = String.valueOf(claims.get("globalUserId"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        return new AuthenticatedUser(userId, globalUserId, roles);
    }

    private String encodeJson(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encode JWT JSON.", ex);
        }
    }

    private Map<String, Object> decodeJson(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid access token payload.");
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign JWT.", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
