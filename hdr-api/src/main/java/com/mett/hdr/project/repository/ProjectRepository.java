package com.mett.hdr.project.repository;

import com.mett.hdr.project.entity.Project;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Project create(
            String globalId,
            String projectNumber,
            String name,
            String type,
            String description,
            String city,
            String timezone,
            Long ownerUserId,
            Long teamId,
            Long organizationId,
            Long actorUserId,
            Long managerUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO projects (
                        global_project_id, project_number, name, project_type, description,
                        status, origin_system, city, timezone, owner_user_id, team_id,
                        organization_id, created_by_user_id, managed_by_user_id
                    ) VALUES (?, ?, ?, ?, ?, 'DRAFT', 'HDR', ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalId);
            statement.setString(2, projectNumber);
            statement.setString(3, name);
            statement.setString(4, type);
            statement.setString(5, description);
            statement.setString(6, city);
            statement.setString(7, timezone);
            statement.setLong(8, ownerUserId);
            setNullableLong(statement, 9, teamId);
            setNullableLong(statement, 10, organizationId);
            statement.setLong(11, actorUserId);
            statement.setLong(12, managerUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<Project> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM projects WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<Project> findByGlobalId(String globalId) {
        return jdbcTemplate.query("SELECT * FROM projects WHERE global_project_id = ?", this::map, globalId)
                .stream().findFirst();
    }

    public Optional<Project> lockByGlobalId(String globalId) {
        return jdbcTemplate.query(
                "SELECT * FROM projects WHERE global_project_id = ? FOR UPDATE", this::map, globalId)
                .stream().findFirst();
    }

    public List<Project> findAll() {
        return jdbcTemplate.query("SELECT * FROM projects ORDER BY created_at DESC", this::map);
    }

    public Project updateDetails(
            Long id,
            String name,
            String type,
            String description,
            String city,
            String timezone
    ) {
        jdbcTemplate.update("""
                UPDATE projects SET name = ?, project_type = ?, description = ?, city = ?, timezone = ?
                WHERE id = ?
                """, name, type, description, city, timezone, id);
        return findById(id).orElseThrow();
    }

    public Project updateStatus(
            Long id,
            String status,
            LocalDateTime activatedAt,
            LocalDateTime archivedAt
    ) {
        jdbcTemplate.update("""
                UPDATE projects SET status = ?, activated_at = ?, archived_at = ? WHERE id = ?
                """, status, activatedAt, archivedAt, id);
        return findById(id).orElseThrow();
    }

    public Project transferManager(Long id, Long managerUserId) {
        jdbcTemplate.update("UPDATE projects SET managed_by_user_id = ? WHERE id = ?", managerUserId, id);
        return findById(id).orElseThrow();
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM projects");
    }

    private Project map(ResultSet rs, int row) throws SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("global_project_id"),
                rs.getString("project_number"),
                rs.getString("name"),
                rs.getString("project_type"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("origin_system"),
                rs.getString("city"),
                rs.getString("timezone"),
                rs.getLong("owner_user_id"),
                nullableLong(rs, "team_id"),
                nullableLong(rs, "organization_id"),
                rs.getLong("created_by_user_id"),
                rs.getLong("managed_by_user_id"),
                timestamp(rs, "activated_at"),
                timestamp(rs, "archived_at"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
