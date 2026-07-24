package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentOrder;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentOrder create(
            String globalOrderId,
            String orderNumber,
            String subjectType,
            Long userId,
            Long teamId,
            Long purchaserUserId,
            String currency,
            long amountMinor,
            String idempotencyKey,
            LocalDateTime expiresAt
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_orders (
                        global_order_id, order_number, subject_type, user_id, team_id,
                        purchaser_user_id, order_status, payment_status,
                        fulfillment_status, currency, total_amount_minor,
                        request_idempotency_key, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'CREATED', 'UNPAID',
                              'NOT_STARTED', ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalOrderId);
            statement.setString(2, orderNumber);
            statement.setString(3, subjectType);
            setLong(statement, 4, userId);
            setLong(statement, 5, teamId);
            statement.setLong(6, purchaserUserId);
            statement.setString(7, currency);
            statement.setLong(8, amountMinor);
            statement.setString(9, idempotencyKey);
            statement.setObject(10, expiresAt);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentOrder> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM payment_orders WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<PaymentOrder> lockById(Long id) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_orders WHERE id = ? FOR UPDATE
                """, this::map, id).stream().findFirst();
    }

    public Optional<PaymentOrder> findByGlobalId(String globalOrderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_orders WHERE global_order_id = ?
                """, this::map, globalOrderId).stream().findFirst();
    }

    public Optional<PaymentOrder> lockByGlobalId(String globalOrderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_orders
                WHERE global_order_id = ? FOR UPDATE
                """, this::map, globalOrderId).stream().findFirst();
    }

    public Optional<PaymentOrder> findByPurchaserAndKey(
            Long purchaserUserId,
            String key
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_orders
                WHERE purchaser_user_id = ? AND request_idempotency_key = ?
                """, this::map, purchaserUserId, key).stream().findFirst();
    }

    public List<PaymentOrder> findByPurchaser(Long purchaserUserId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_orders
                WHERE purchaser_user_id = ?
                ORDER BY created_at DESC
                """, this::map, purchaserUserId);
    }

    public PaymentOrder updateOrderStatus(
            Long id,
            String status,
            LocalDateTime paidAt,
            LocalDateTime fulfilledAt,
            LocalDateTime cancelledAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_orders
                SET order_status = ?,
                    paid_at = COALESCE(?, paid_at),
                    fulfilled_at = COALESCE(?, fulfilled_at),
                    cancelled_at = COALESCE(?, cancelled_at)
                WHERE id = ?
                """, status, paidAt, fulfilledAt, cancelledAt, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrder updatePaymentStatus(
            Long id,
            String status,
            LocalDateTime paidAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_orders
                SET payment_status = ?, paid_at = COALESCE(?, paid_at)
                WHERE id = ?
                """, status, paidAt, id);
        return findById(id).orElseThrow();
    }

    public PaymentOrder updateFulfillmentStatus(
            Long id,
            String status,
            LocalDateTime fulfilledAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_orders
                SET fulfillment_status = ?,
                    fulfilled_at = COALESCE(?, fulfilled_at)
                WHERE id = ?
                """, status, fulfilledAt, id);
        return findById(id).orElseThrow();
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_orders", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM payment_orders");
    }

    private PaymentOrder map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PaymentOrder(
                rs.getLong("id"),
                rs.getString("global_order_id"),
                rs.getString("order_number"),
                rs.getString("subject_type"),
                nullableLong(rs, "user_id"),
                nullableLong(rs, "team_id"),
                rs.getLong("purchaser_user_id"),
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getString("fulfillment_status"),
                rs.getString("currency"),
                rs.getLong("total_amount_minor"),
                rs.getString("request_idempotency_key"),
                dateTime(rs, "expires_at"),
                dateTime(rs, "paid_at"),
                dateTime(rs, "fulfilled_at"),
                dateTime(rs, "cancelled_at"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private void setLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
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
