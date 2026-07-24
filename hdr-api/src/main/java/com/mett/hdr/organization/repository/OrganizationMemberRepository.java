package com.mett.hdr.organization.repository;

import com.mett.hdr.organization.entity.OrganizationMember;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrganizationMember create(Long organizationId, Long userId, String role, Long actorUserId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organization_members (
                        organization_id, user_id, member_role, status, created_by_user_id
                    ) VALUES (?, ?, ?, 'ACTIVE', ?)
                    """, new String[]{"id"});
            statement.setLong(1, organizationId);
            statement.setLong(2, userId);
            statement.setString(3, role);
            statement.setLong(4, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<OrganizationMember> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM organization_members WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<OrganizationMember> find(Long organizationId, Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM organization_members WHERE organization_id = ? AND user_id = ?
                """, this::map, organizationId, userId).stream().findFirst();
    }

    public List<OrganizationMember> findAll(Long organizationId) {
        return jdbcTemplate.query("""
                SELECT * FROM organization_members WHERE organization_id = ? ORDER BY created_at
                """, this::map, organizationId);
    }

    public OrganizationMember update(Long id, String role, String status) {
        LocalDateTime leftAt = "LEFT".equals(status) ? LocalDateTime.now() : null;
        jdbcTemplate.update("""
                UPDATE organization_members
                SET member_role = ?, status = ?, left_at = ?,
                    joined_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP(3) ELSE joined_at END
                WHERE id = ?
                """, role, status, leftAt, status, id);
        return findById(id).orElseThrow();
    }

    public void transferOwnerRoles(Long organizationId, Long oldOwnerId, Long newOwnerId) {
        jdbcTemplate.update("""
                UPDATE organization_members
                SET member_role = CASE
                    WHEN user_id = ? THEN 'ADMIN'
                    WHEN user_id = ? THEN 'OWNER'
                    ELSE member_role
                END
                WHERE organization_id = ? AND user_id IN (?, ?)
                """, oldOwnerId, newOwnerId, organizationId, oldOwnerId, newOwnerId);
    }

    public long countActiveOwners(Long organizationId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM organization_members
                WHERE organization_id = ? AND member_role = 'OWNER' AND status = 'ACTIVE'
                """, Long.class, organizationId);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM organization_members");
    }

    private OrganizationMember map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp leftAt = rs.getTimestamp("left_at");
        return new OrganizationMember(
                rs.getLong("id"),
                rs.getLong("organization_id"),
                rs.getLong("user_id"),
                rs.getString("member_role"),
                rs.getString("status"),
                rs.getTimestamp("joined_at").toLocalDateTime(),
                leftAt == null ? null : leftAt.toLocalDateTime(),
                rs.getLong("created_by_user_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
