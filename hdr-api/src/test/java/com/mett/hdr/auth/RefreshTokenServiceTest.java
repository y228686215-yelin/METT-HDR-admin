package com.mett.hdr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mett.hdr.auth.repository.RefreshTokenRepository;
import com.mett.hdr.auth.service.RefreshTokenService;
import com.mett.hdr.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class RefreshTokenServiceTest {

    @Test
    void storesHashAndRevokesRefreshToken() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test.sql")
                .build());
        jdbcTemplate.update("""
                INSERT INTO users (
                    global_user_id, identity_source, email, username, password_hash, status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, "usr_refresh_test", "HDR", "refresh-test@example.com",
                "refresh-test@example.com", "hash", "ACTIVE");
        RefreshTokenRepository repository = new RefreshTokenRepository(jdbcTemplate);
        RefreshTokenService service = new RefreshTokenService(repository, 3600);

        String rawToken = service.issue(1L);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().iterator().next().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(service.requireValid(rawToken).getUserId()).isEqualTo(1L);

        service.revoke(rawToken);

        assertThat(repository.findAll().iterator().next().getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> service.requireValid(rawToken)).isInstanceOf(UnauthorizedException.class);
    }
}
