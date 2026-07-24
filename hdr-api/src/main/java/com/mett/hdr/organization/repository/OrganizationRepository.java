package com.mett.hdr.organization.repository;

import com.mett.hdr.organization.entity.Organization;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Organization create(
            String globalId,
            String name,
            String type,
            String description,
            Long actorUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organizations (
                        global_organization_id, name, organization_type, description, status,
                        owner_user_id, managed_by_user_id, created_by_user_id
                    ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalId);
            statement.setString(2, name);
            statement.setString(3, type);
            statement.setString(4, description);
            statement.setLong(5, actorUserId);
            statement.setLong(6, actorUserId);
            statement.setLong(7, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<Organization> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM organizations WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<Organization> findByGlobalId(String globalId) {
        return jdbcTemplate.query(
                        "SELECT * FROM organizations WHERE global_organization_id = ?", this::map, globalId)
                .stream().findFirst();
    }

    public List<Organization> findActiveForUser(Long userId) {
        return jdbcTemplate.query("""
                SELECT o.* FROM organizations o
                JOIN organization_members om ON om.organization_id = o.id
                WHERE om.user_id = ? AND om.status = 'ACTIVE'
                ORDER BY o.created_at DESC
                """, this::map, userId);
    }

    public Organization updateDetails(
            Long id,
            String name,
            String description,
            String status,
            Long managedByUserId
    ) {
        jdbcTemplate.update("""
                UPDATE organizations
                SET name = ?, description = ?, status = ?, managed_by_user_id = ?
                WHERE id = ?
                """, name, description, status, managedByUserId, id);
        return findById(id).orElseThrow();
    }

    public Organization transferOwnership(Long id, Long oldOwnerId, Long newOwnerId) {
        jdbcTemplate.update("""
                UPDATE organizations
                SET owner_user_id = ?,
                    managed_by_user_id = CASE WHEN managed_by_user_id = ? THEN ? ELSE managed_by_user_id END
                WHERE id = ?
                """, newOwnerId, oldOwnerId, newOwnerId, id);
        return findById(id).orElseThrow();
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM organizations");
    }

    private Organization map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Organization(
                rs.getLong("id"),
                rs.getString("global_organization_id"),
                rs.getString("global_company_id"),
                rs.getString("name"),
                rs.getString("organization_type"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getLong("owner_user_id"),
                rs.getLong("managed_by_user_id"),
                rs.getLong("created_by_user_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
