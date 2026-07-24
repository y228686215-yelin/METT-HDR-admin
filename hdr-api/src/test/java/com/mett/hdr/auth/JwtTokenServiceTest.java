package com.mett.hdr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mett.hdr.auth.token.AuthenticatedUser;
import com.mett.hdr.auth.token.JwtTokenService;
import com.mett.hdr.auth.token.TokenIssueResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    @Test
    void generatesAndParsesJwtAccessToken() {
        JwtTokenService service = new JwtTokenService(new ObjectMapper(), "unit-test-signing-secret", 3600);

        TokenIssueResult issued = service.issue(10L, "usr_abc", List.of("USER"));
        AuthenticatedUser parsed = service.parse("Bearer " + issued.accessToken());

        assertThat(issued.accessToken()).contains(".");
        assertThat(issued.expiresIn()).isEqualTo(3600);
        assertThat(parsed.userId()).isEqualTo(10L);
        assertThat(parsed.globalUserId()).isEqualTo("usr_abc");
        assertThat(parsed.roles()).containsExactly("USER");
    }
}
