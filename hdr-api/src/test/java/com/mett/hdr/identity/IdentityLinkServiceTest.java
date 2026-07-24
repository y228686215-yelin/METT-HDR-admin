package com.mett.hdr.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mett.hdr.audit.repository.AuditLogRepository;
import com.mett.hdr.audit.service.AuditService;
import com.mett.hdr.common.exception.ConflictException;
import com.mett.hdr.identity.entity.UserIdentityLink;
import com.mett.hdr.identity.repository.UserIdentityLinkRepository;
import com.mett.hdr.identity.service.IdentityLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class IdentityLinkServiceTest {

    @Test
    void createsIdentityLinkAndAuditLog() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test.sql")
                .build());
        UserIdentityLinkRepository repository = new UserIdentityLinkRepository(jdbcTemplate);
        AuditLogRepository auditRepository = new AuditLogRepository(jdbcTemplate);
        IdentityLinkService service = new IdentityLinkService(repository, new AuditService(auditRepository));

        UserIdentityLink link = service.link(1L, "usr_123", "METT_WEBSITE", "mett_user_1", "ext_usr_1", "METT", null);

        assertThat(link.getId()).isNotNull();
        assertThat(link.getGlobalUserId()).isEqualTo("usr_123");
        assertThat(repository.findAll()).hasSize(1);
        assertThat(auditRepository.countByAction("IDENTITY_BIND")).isEqualTo(1);
        assertThatThrownBy(() -> service.link(1L, "usr_123", "METT_WEBSITE", "mett_user_1", "ext_usr_1", "METT", null))
                .isInstanceOf(ConflictException.class);
    }
}
