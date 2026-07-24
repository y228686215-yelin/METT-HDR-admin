package com.mett.hdr.audit.repository;

import com.mett.hdr.audit.entity.AuditLog;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditLog append(Long actorUserId, String action, String resourceType, String resourceId, String sourceSystem, String ipAddress, String userAgent) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO audit_logs (
                        actor_user_id, action, resource_type, resource_id,
                        source_system, ip_address, user_agent
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            if (actorUserId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, actorUserId);
            }
            statement.setString(2, action);
            statement.setString(3, resourceType);
            statement.setString(4, resourceId);
            statement.setString(5, sourceSystem);
            statement.setString(6, ipAddress);
            statement.setString(7, userAgent);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    public List<AuditLog> findAll() {
        return jdbcTemplate.query("SELECT * FROM audit_logs ORDER BY id", this::mapAuditLog);
    }

    public long countByAction(String action) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ?", Long.class, action);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM audit_logs");
    }

    private AuditLog findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM audit_logs WHERE id = ?", this::mapAuditLog, id)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private AuditLog mapAuditLog(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        long actorUserId = resultSet.getLong("actor_user_id");
        return new AuditLog(
                resultSet.getLong("id"),
                resultSet.wasNull() ? null : actorUserId,
                resultSet.getString("action"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("source_system"),
                resultSet.getString("ip_address"),
                resultSet.getString("user_agent"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
