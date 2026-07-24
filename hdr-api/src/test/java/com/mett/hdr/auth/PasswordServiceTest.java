package com.mett.hdr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mett.hdr.auth.security.PasswordService;
import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashesPasswordsWithBcryptAndMatchesRawPassword() {
        String hash = passwordService.hash("StrongPassword123");

        assertThat(hash).startsWith("$2");
        assertThat(hash).isNotEqualTo("StrongPassword123");
        assertThat(passwordService.matches("StrongPassword123", hash)).isTrue();
        assertThat(passwordService.matches("WrongPassword123", hash)).isFalse();
    }
}
