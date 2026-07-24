package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentOrderFulfillment;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderFulfillmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentOrderFulfillmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentOrderFulfillment create(Long orderId, String operationKey) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_order_fulfillments (
                        payment_order_id, fulfillment_type, status, operation_key
                    ) VALUES (?, 'MEMBERSHIP_ACTIVATION', 'PENDING', ?)
                    """, new String[]{"id"});
            statement.setLong(1, orderId);
            statement.setString(2, operationKey);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentOrderFulfillment> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_fulfillments WHERE id = ?
                """, this::map, id).stream().findFirst();
    }

    public Optional<PaymentOrderFulfillment> findByOrderId(Long orderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_fulfillments
                WHERE payment_order_id = ?
                """, this::map, orderId).stream().findFirst();
    }

    public Optional<PaymentOrderFulfillment> lockByOrderId(Long orderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_fulfillments
                WHERE payment_order_id = ? FOR UPDATE
                """, this::map, orderId).stream().findFirst();
    }

    public PaymentOrderFulfillment markProcessing(Long id, LocalDateTime startedAt) {
        jdbcTemplate.update("""
                UPDATE payment_order_fulfillments
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    started_at = ?, completed_at = NULL,
                    last_error_code = NULL, last_error_message = NULL
                WHERE id = ?
                """, startedAt, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrderFulfillment markFulfilled(
            Long id,
            Long membershipId,
            LocalDateTime completedAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_order_fulfillments
                SET status = 'FULFILLED', fulfilled_membership_id = ?,
                    completed_at = ?, last_error_code = NULL,
                    last_error_message = NULL
                WHERE id = ?
                """, membershipId, completedAt, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrderFulfillment markFailed(
            Long id,
            String errorCode,
            String errorMessage,
            LocalDateTime completedAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_order_fulfillments
                SET status = 'FAILED', last_error_code = ?,
                    last_error_message = ?, completed_at = ?
                WHERE id = ?
                """, errorCode, errorMessage, completedAt, id);
        return findById(id).orElseThrow();
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_order_fulfillments", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM payment_order_fulfillments");
    }

    private PaymentOrderFulfillment map(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new PaymentOrderFulfillment(
                rs.getLong("id"),
                rs.getLong("payment_order_id"),
                rs.getString("fulfillment_type"),
                rs.getString("status"),
                rs.getString("operation_key"),
                rs.getInt("attempt_count"),
                nullableLong(rs, "fulfilled_membership_id"),
                rs.getString("last_error_code"),
                rs.getString("last_error_message"),
                dateTime(rs, "started_at"),
                dateTime(rs, "completed_at"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime dateTime(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
