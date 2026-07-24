package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.MembershipUsage;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MembershipUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MembershipUsage create(
            Long membershipId,
            Long entitlementDefinitionId,
            LocalDateTime cycleStartAt,
            LocalDateTime cycleEndAt,
            Long quotaLimit,
            boolean unlimited
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO membership_usage (
                        membership_id, entitlement_definition_id,
                        cycle_start_at, cycle_end_at, quota_limit_snapshot,
                        is_unlimited, used_count, reserved_count, version
                    ) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0)
                    """, new String[]{"id"});
            statement.setLong(1, membershipId);
            statement.setLong(2, entitlementDefinitionId);
            statement.setObject(3, cycleStartAt);
            statement.setObject(4, cycleEndAt);
            if (quotaLimit == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, quotaLimit);
            }
            statement.setBoolean(6, unlimited);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<MembershipUsage> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM membership_usage WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<MembershipUsage> lockById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM membership_usage WHERE id = ? FOR UPDATE", this::map, id)
                .stream().findFirst();
    }

    public Optional<MembershipUsage> findForCycle(
            Long membershipId,
            Long definitionId,
            LocalDateTime cycleStartAt
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_usage
                WHERE membership_id = ?
                  AND entitlement_definition_id = ?
                  AND cycle_start_at = ?
                """, this::map, membershipId, definitionId, cycleStartAt)
                .stream().findFirst();
    }

    public Optional<MembershipUsage> lockForCycle(
            Long membershipId,
            Long definitionId,
            LocalDateTime cycleStartAt
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_usage
                WHERE membership_id = ?
                  AND entitlement_definition_id = ?
                  AND cycle_start_at = ?
                FOR UPDATE
                """, this::map, membershipId, definitionId, cycleStartAt)
                .stream().findFirst();
    }

    public List<MembershipUsage> findForCycle(
            Long membershipId,
            LocalDateTime cycleStartAt
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_usage
                WHERE membership_id = ? AND cycle_start_at = ?
                ORDER BY entitlement_definition_id
                """, this::map, membershipId, cycleStartAt);
    }

    public List<MembershipUsage> findAllForMembership(Long membershipId) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_usage
                WHERE membership_id = ?
                ORDER BY cycle_start_at, entitlement_definition_id
                """, this::map, membershipId);
    }

    public MembershipUsage updateCounts(Long id, long used, long reserved) {
        jdbcTemplate.update("""
                UPDATE membership_usage
                SET used_count = ?, reserved_count = ?, version = version + 1
                WHERE id = ?
                """, used, reserved, id);
        return findById(id).orElseThrow();
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_usage", Long.class);
        return count == null ? 0 : count;
    }

    private MembershipUsage map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long quotaLimit = rs.getLong("quota_limit_snapshot");
        Long nullableQuotaLimit = rs.wasNull() ? null : quotaLimit;
        return new MembershipUsage(
                rs.getLong("id"),
                rs.getLong("membership_id"),
                rs.getLong("entitlement_definition_id"),
                rs.getTimestamp("cycle_start_at").toLocalDateTime(),
                rs.getTimestamp("cycle_end_at").toLocalDateTime(),
                nullableQuotaLimit,
                rs.getBoolean("is_unlimited"),
                rs.getLong("used_count"),
                rs.getLong("reserved_count"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
