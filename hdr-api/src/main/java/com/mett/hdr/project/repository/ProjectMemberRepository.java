package com.mett.hdr.project.repository;

import com.mett.hdr.project.entity.ProjectMember;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProjectMember create(Long projectId, Long userId, String role, Long actorUserId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO project_members (
                        project_id, user_id, member_role, status, joined_at, created_by_user_id
                    ) VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, projectId);
            statement.setLong(2, userId);
            statement.setString(3, role);
            statement.setObject(4, now);
            statement.setLong(5, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<ProjectMember> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM project_members WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<ProjectMember> find(Long projectId, Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_members WHERE project_id = ? AND user_id = ?
                """, this::map, projectId, userId).stream().findFirst();
    }

    public Optional<ProjectMember> lock(Long projectId, Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_members WHERE project_id = ? AND user_id = ? FOR UPDATE
                """, this::map, projectId, userId).stream().findFirst();
    }

    public List<ProjectMember> findAll(Long projectId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_members WHERE project_id = ? ORDER BY created_at
                """, this::map, projectId);
    }

    public ProjectMember update(Long id, String role, String status) {
        LocalDateTime leftAt = "LEFT".equals(status) ? LocalDateTime.now() : null;
        jdbcTemplate.update("""
                UPDATE project_members
                SET member_role = ?, status = ?, left_at = ?,
                    joined_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP(3) ELSE joined_at END
                WHERE id = ?
                """, role, status, leftAt, status, id);
        return findById(id).orElseThrow();
    }

    public void transferManagerRoles(Long projectId, Long oldManagerId, Long newManagerId) {
        int updated = jdbcTemplate.update("""
                UPDATE project_members
                SET member_role = CASE
                    WHEN user_id = ? THEN 'EDITOR'
                    WHEN user_id = ? THEN 'MANAGER'
                    ELSE member_role
                END
                WHERE project_id = ? AND user_id IN (?, ?) AND status = 'ACTIVE'
                """, oldManagerId, newManagerId, projectId, oldManagerId, newManagerId);
        if (updated != 2) {
            throw new IllegalStateException("Both active manager memberships must be updated.");
        }
    }

    public long countActiveManagers(Long projectId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_members
                WHERE project_id = ? AND member_role = 'MANAGER' AND status = 'ACTIVE'
                """, Long.class, projectId);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM project_members");
    }

    private ProjectMember map(ResultSet rs, int row) throws SQLException {
        var leftAt = rs.getTimestamp("left_at");
        return new ProjectMember(
                rs.getLong("id"),
                rs.getLong("project_id"),
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
