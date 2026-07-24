package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.QuotaTransaction;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class QuotaTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public QuotaTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<QuotaTransaction> findByOperationKey(String operationKey) {
        return jdbcTemplate.query("""
                SELECT * FROM quota_transactions WHERE operation_key = ?
                """, this::map, operationKey).stream().findFirst();
    }

    public QuotaTransaction create(
            Long membershipId,
            Long usageId,
            Long definitionId,
            String operationKey,
            String changeType,
            long amount,
            long usedBefore,
            long usedAfter,
            long reservedBefore,
            long reservedAfter,
            Long quotaLimit,
            String reservationKey,
            String relatedObjectType,
            String relatedObjectId,
            String reason,
            Long actorUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO quota_transactions (
                        membership_id, membership_usage_id,
                        entitlement_definition_id, operation_key, change_type,
                        amount, used_before, used_after, reserved_before,
                        reserved_after, quota_limit_snapshot, reservation_key,
                        related_object_type, related_object_id, reason, actor_user_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, membershipId);
            statement.setLong(2, usageId);
            statement.setLong(3, definitionId);
            statement.setString(4, operationKey);
            statement.setString(5, changeType);
            statement.setLong(6, amount);
            statement.setLong(7, usedBefore);
            statement.setLong(8, usedAfter);
            statement.setLong(9, reservedBefore);
            statement.setLong(10, reservedAfter);
            setNullableLong(statement, 11, quotaLimit);
            statement.setString(12, reservationKey);
            statement.setString(13, relatedObjectType);
            statement.setString(14, relatedObjectId);
            statement.setString(15, reason);
            setNullableLong(statement, 16, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public boolean reservationExists(
            Long membershipId,
            Long definitionId,
            String reservationKey
    ) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM quota_transactions
                WHERE membership_id = ?
                  AND entitlement_definition_id = ?
                  AND reservation_key = ?
                  AND change_type = 'RESERVE'
                """, Long.class, membershipId, definitionId, reservationKey);
        return count != null && count > 0;
    }

    public long outstandingReservation(
            Long membershipId,
            Long definitionId,
            String reservationKey
    ) {
        Long amount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE
                        WHEN change_type = 'RESERVE' THEN amount
                        WHEN change_type IN ('COMMIT', 'RELEASE') THEN -amount
                        ELSE 0
                    END
                ), 0)
                FROM quota_transactions
                WHERE membership_id = ?
                  AND entitlement_definition_id = ?
                  AND reservation_key = ?
                """, Long.class, membershipId, definitionId, reservationKey);
        return amount == null ? 0 : amount;
    }

    public List<QuotaTransaction> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM quota_transactions ORDER BY id", this::map);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quota_transactions", Long.class);
        return count == null ? 0 : count;
    }

    private Optional<QuotaTransaction> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM quota_transactions WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    private QuotaTransaction map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long quotaLimit = rs.getLong("quota_limit_snapshot");
        Long nullableQuotaLimit = rs.wasNull() ? null : quotaLimit;
        return new QuotaTransaction(
                rs.getLong("id"),
                rs.getLong("membership_id"),
                rs.getLong("membership_usage_id"),
                rs.getLong("entitlement_definition_id"),
                rs.getString("operation_key"),
                rs.getString("change_type"),
                rs.getLong("amount"),
                rs.getLong("used_before"),
                rs.getLong("used_after"),
                rs.getLong("reserved_before"),
                rs.getLong("reserved_after"),
                nullableQuotaLimit,
                rs.getString("reservation_key"),
                rs.getString("related_object_type"),
                rs.getString("related_object_id"),
                rs.getString("reason"),
                nullableLong(rs, "actor_user_id"),
                rs.getTimestamp("created_at").toLocalDateTime()
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
}
