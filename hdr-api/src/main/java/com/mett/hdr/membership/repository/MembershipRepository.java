package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.Membership;
import com.mett.hdr.membership.model.MembershipSubject;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public MembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Membership create(
            MembershipSubject subject,
            Long planId,
            String status,
            String source,
            LocalDateTime startedAt,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            Long createdByUserId
    ) {
        return create(
                subject, planId, status, source, startedAt,
                periodStartAt, periodEndAt, null, createdByUserId);
    }

    public Membership create(
            MembershipSubject subject,
            Long planId,
            String status,
            String source,
            LocalDateTime startedAt,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt,
            LocalDateTime expiresAt,
            Long createdByUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO memberships (
                        subject_type, user_id, team_id, membership_plan_id,
                        status, source, current_marker, started_at,
                        current_period_start_at, current_period_end_at,
                        expires_at, created_by_user_id
                    ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, subject.subjectType());
            setNullableLong(statement, 2, subject.userId());
            setNullableLong(statement, 3, subject.teamId());
            statement.setLong(4, planId);
            statement.setString(5, status);
            statement.setString(6, source);
            statement.setObject(7, startedAt);
            statement.setObject(8, periodStartAt);
            statement.setObject(9, periodEndAt);
            statement.setObject(10, expiresAt);
            setNullableLong(statement, 11, createdByUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<Membership> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM memberships WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<Membership> lockById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM memberships WHERE id = ? FOR UPDATE", this::map, id)
                .stream().findFirst();
    }

    public Optional<Membership> findCurrent(MembershipSubject subject) {
        if ("USER".equals(subject.subjectType())) {
            return findCurrentForUser(subject.userId());
        }
        return findCurrentForTeam(subject.teamId());
    }

    public Optional<Membership> lockCurrent(MembershipSubject subject) {
        String column = "USER".equals(subject.subjectType()) ? "user_id" : "team_id";
        return jdbcTemplate.query("""
                SELECT * FROM memberships
                WHERE %s = ? AND current_marker = 1
                FOR UPDATE
                """.formatted(column), this::map, subject.subjectId()).stream().findFirst();
    }

    public Optional<Membership> findCurrentForUser(Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM memberships
                WHERE user_id = ? AND current_marker = 1
                """, this::map, userId).stream().findFirst();
    }

    public Optional<Membership> findCurrentForTeam(Long teamId) {
        return jdbcTemplate.query("""
                SELECT * FROM memberships
                WHERE team_id = ? AND current_marker = 1
                """, this::map, teamId).stream().findFirst();
    }

    public List<Membership> findHistory(MembershipSubject subject) {
        String column = "USER".equals(subject.subjectType()) ? "user_id" : "team_id";
        return jdbcTemplate.query("""
                SELECT * FROM memberships WHERE %s = ? ORDER BY created_at
                """.formatted(column), this::map, subject.subjectId());
    }

    public void replace(Long id, LocalDateTime replacedAt) {
        jdbcTemplate.update("""
                UPDATE memberships
                SET status = 'REPLACED', current_marker = NULL, replaced_at = ?
                WHERE id = ? AND current_marker = 1
                """, replacedAt, id);
    }

    public Membership updateStatus(
            Long id,
            String status,
            LocalDateTime suspendedAt,
            LocalDateTime expiresAt
    ) {
        Integer currentMarker = "EXPIRED".equals(status) ? null : 1;
        jdbcTemplate.update("""
                UPDATE memberships
                SET status = ?, suspended_at = ?, expires_at = ?, current_marker = ?
                WHERE id = ?
                """, status, suspendedAt, expiresAt, currentMarker, id);
        return findById(id).orElseThrow();
    }

    public Membership updatePeriod(
            Long id,
            LocalDateTime periodStartAt,
            LocalDateTime periodEndAt
    ) {
        jdbcTemplate.update("""
                UPDATE memberships
                SET current_period_start_at = ?, current_period_end_at = ?
                WHERE id = ?
                """, periodStartAt, periodEndAt, id);
        return findById(id).orElseThrow();
    }

    public long countCurrent(MembershipSubject subject) {
        String column = "USER".equals(subject.subjectType()) ? "user_id" : "team_id";
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM memberships
                WHERE %s = ? AND current_marker = 1
                """.formatted(column), Long.class, subject.subjectId());
        return count == null ? 0 : count;
    }

    public List<Membership> findAll() {
        return jdbcTemplate.query("SELECT * FROM memberships ORDER BY id", this::map);
    }

    public void clearAllMembershipData() {
        jdbcTemplate.update("DELETE FROM usage_snapshots");
        jdbcTemplate.update("DELETE FROM quota_transactions");
        jdbcTemplate.update("DELETE FROM membership_usage");
        jdbcTemplate.update("DELETE FROM memberships");
    }

    private Membership map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Membership(
                rs.getLong("id"),
                rs.getString("subject_type"),
                nullableLong(rs, "user_id"),
                nullableLong(rs, "team_id"),
                rs.getLong("membership_plan_id"),
                rs.getString("status"),
                rs.getString("source"),
                nullableInteger(rs, "current_marker"),
                localDateTime(rs, "started_at"),
                localDateTime(rs, "current_period_start_at"),
                localDateTime(rs, "current_period_end_at"),
                localDateTime(rs, "expires_at"),
                localDateTime(rs, "suspended_at"),
                localDateTime(rs, "cancelled_at"),
                localDateTime(rs, "replaced_at"),
                nullableLong(rs, "created_by_user_id"),
                localDateTime(rs, "created_at"),
                localDateTime(rs, "updated_at")
        );
    }

    private void setNullableLong(
            PreparedStatement statement,
            int index,
            Long value
    ) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime localDateTime(
            java.sql.ResultSet rs,
            String column
    ) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
