package com.mett.hdr.auth.repository;

import com.mett.hdr.auth.entity.AuthRefreshToken;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthRefreshToken save(AuthRefreshToken token) {
        if (token.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO auth_refresh_tokens (user_id, token_hash, expires_at, revoked_at)
                        VALUES (?, ?, ?, ?)
                        """, new String[]{"id"});
                statement.setLong(1, token.getUserId());
                statement.setString(2, token.getTokenHash());
                statement.setObject(3, token.getExpiresAt());
                statement.setObject(4, token.getRevokedAt());
                return statement;
            }, keyHolder);
            token.setId(keyHolder.getKey().longValue());
        } else {
            jdbcTemplate.update("""
                            UPDATE auth_refresh_tokens
                            SET user_id = ?, token_hash = ?, expires_at = ?, revoked_at = ?
                            WHERE id = ?
                            """,
                    token.getUserId(),
                    token.getTokenHash(),
                    token.getExpiresAt(),
                    token.getRevokedAt(),
                    token.getId());
        }
        return findById(token.getId()).orElseThrow();
    }

    public Optional<AuthRefreshToken> findByTokenHash(String tokenHash) {
        return jdbcTemplate.query("""
                        SELECT * FROM auth_refresh_tokens WHERE token_hash = ?
                        """, this::mapToken, tokenHash)
                .stream()
                .findFirst();
    }

    public Collection<AuthRefreshToken> findAll() {
        return jdbcTemplate.query("SELECT * FROM auth_refresh_tokens ORDER BY id", this::mapToken);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM auth_refresh_tokens");
    }

    private Optional<AuthRefreshToken> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM auth_refresh_tokens WHERE id = ?", this::mapToken, id)
                .stream()
                .findFirst();
    }

    private AuthRefreshToken mapToken(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setId(resultSet.getLong("id"));
        token.setUserId(resultSet.getLong("user_id"));
        token.setTokenHash(resultSet.getString("token_hash"));
        token.setExpiresAt(resultSet.getTimestamp("expires_at").toLocalDateTime());
        java.sql.Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
        token.setRevokedAt(revokedAt == null ? null : revokedAt.toLocalDateTime());
        token.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        return token;
    }
}
