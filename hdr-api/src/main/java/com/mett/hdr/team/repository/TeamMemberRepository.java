package com.mett.hdr.team.repository;

import com.mett.hdr.team.entity.TeamMember;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TeamMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TeamMember create(Long teamId, Long userId, String role, Long actorUserId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO team_members (
                        team_id, user_id, member_role, status, created_by_user_id
                    ) VALUES (?, ?, ?, 'ACTIVE', ?)
                    """, new String[]{"id"});
            statement.setLong(1, teamId);
            statement.setLong(2, userId);
            statement.setString(3, role);
            statement.setLong(4, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<TeamMember> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM team_members WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<TeamMember> find(Long teamId, Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM team_members WHERE team_id = ? AND user_id = ?
                """, this::map, teamId, userId).stream().findFirst();
    }

    public List<TeamMember> findAll(Long teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM team_members WHERE team_id = ? ORDER BY created_at", this::map, teamId);
    }

    public TeamMember update(Long id, String role, String status) {
        LocalDateTime leftAt = "LEFT".equals(status) ? LocalDateTime.now() : null;
        jdbcTemplate.update("""
                UPDATE team_members
                SET member_role = ?, status = ?, left_at = ?,
                    joined_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP(3) ELSE joined_at END
                WHERE id = ?
                """, role, status, leftAt, status, id);
        return findById(id).orElseThrow();
    }

    public void transitionForOrganizationMembership(
            Long organizationId,
            Long userId,
            String membershipStatus
    ) {
        String teamStatus = "LEFT".equals(membershipStatus) ? "LEFT" : "SUSPENDED";
        LocalDateTime leftAt = "LEFT".equals(teamStatus) ? LocalDateTime.now() : null;
        jdbcTemplate.update("""
                UPDATE team_members
                SET status = ?, left_at = ?
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                  AND team_id IN (
                      SELECT t.id FROM teams t WHERE t.organization_id = ?
                  )
                """, teamStatus, leftAt, userId, organizationId);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM team_members");
    }

    private TeamMember map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp leftAt = rs.getTimestamp("left_at");
        return new TeamMember(
                rs.getLong("id"),
                rs.getLong("team_id"),
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
