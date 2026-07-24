package com.mett.hdr.team.repository;

import com.mett.hdr.team.entity.Team;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Team create(
            String globalId,
            Long organizationId,
            String name,
            String description,
            Long actorUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO teams (
                        global_team_id, organization_id, name, description, status,
                        owner_user_id, managed_by_user_id, created_by_user_id
                    ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalId);
            if (organizationId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, organizationId);
            }
            statement.setString(3, name);
            statement.setString(4, description);
            statement.setLong(5, actorUserId);
            statement.setLong(6, actorUserId);
            statement.setLong(7, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<Team> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM teams WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<Team> findByGlobalId(String globalId) {
        return jdbcTemplate.query("SELECT * FROM teams WHERE global_team_id = ?", this::map, globalId)
                .stream().findFirst();
    }

    public List<Team> findActiveForUser(Long userId) {
        return jdbcTemplate.query("""
                SELECT t.* FROM teams t
                JOIN team_members tm ON tm.team_id = t.id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE'
                ORDER BY t.created_at DESC
                """, this::map, userId);
    }

    public Team updateDetails(Long id, String name, String description, String status) {
        jdbcTemplate.update("""
                UPDATE teams SET name = ?, description = ?, status = ? WHERE id = ?
                """, name, description, status, id);
        return findById(id).orElseThrow();
    }

    public Team transferManager(Long id, Long newManagerId) {
        jdbcTemplate.update("UPDATE teams SET managed_by_user_id = ? WHERE id = ?", newManagerId, id);
        return findById(id).orElseThrow();
    }

    public boolean existsManagedOrganizationTeam(Long organizationId, Long userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM teams
                WHERE organization_id = ? AND managed_by_user_id = ? AND status <> 'ARCHIVED'
                """, Long.class, organizationId, userId);
        return count != null && count > 0;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM teams");
    }

    private Team map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long organizationId = rs.getLong("organization_id");
        Long nullableOrganizationId = rs.wasNull() ? null : organizationId;
        return new Team(
                rs.getLong("id"),
                rs.getString("global_team_id"),
                nullableOrganizationId,
                rs.getString("name"),
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
