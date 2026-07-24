package com.mett.hdr.identity.repository;

import com.mett.hdr.identity.entity.UserIdentityLink;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserIdentityLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserIdentityLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserIdentityLink save(UserIdentityLink link) {
        if (link.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_identity_links (
                            global_user_id, source_system, external_user_id,
                            external_global_user_id, identity_type, status
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                statement.setString(1, link.getGlobalUserId());
                statement.setString(2, link.getSourceSystem());
                statement.setString(3, link.getExternalUserId());
                statement.setString(4, link.getExternalGlobalUserId());
                statement.setString(5, link.getIdentityType());
                statement.setString(6, link.getStatus());
                return statement;
            }, keyHolder);
            link.setId(keyHolder.getKey().longValue());
        } else {
            jdbcTemplate.update("""
                            UPDATE user_identity_links
                            SET global_user_id = ?, source_system = ?, external_user_id = ?,
                                external_global_user_id = ?, identity_type = ?, status = ?
                            WHERE id = ?
                            """,
                    link.getGlobalUserId(),
                    link.getSourceSystem(),
                    link.getExternalUserId(),
                    link.getExternalGlobalUserId(),
                    link.getIdentityType(),
                    link.getStatus(),
                    link.getId());
        }
        return findById(link.getId()).orElseThrow();
    }

    public Optional<UserIdentityLink> findBySourceAndExternalUserId(String sourceSystem, String externalUserId) {
        return jdbcTemplate.query("""
                        SELECT * FROM user_identity_links
                        WHERE source_system = ? AND external_user_id = ?
                        """, this::mapLink, sourceSystem, externalUserId)
                .stream()
                .findFirst();
    }

    public Collection<UserIdentityLink> findAll() {
        return jdbcTemplate.query("SELECT * FROM user_identity_links ORDER BY id", this::mapLink);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM user_identity_links");
    }

    private Optional<UserIdentityLink> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM user_identity_links WHERE id = ?", this::mapLink, id)
                .stream()
                .findFirst();
    }

    private UserIdentityLink mapLink(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        UserIdentityLink link = new UserIdentityLink();
        link.setId(resultSet.getLong("id"));
        link.setGlobalUserId(resultSet.getString("global_user_id"));
        link.setSourceSystem(resultSet.getString("source_system"));
        link.setExternalUserId(resultSet.getString("external_user_id"));
        link.setExternalGlobalUserId(resultSet.getString("external_global_user_id"));
        link.setIdentityType(resultSet.getString("identity_type"));
        link.setStatus(resultSet.getString("status"));
        link.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        link.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
        return link;
    }
}
