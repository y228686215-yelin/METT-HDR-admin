package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentAttempt;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentAttempt create(
            String globalId,
            Long orderId,
            String providerCode,
            String idempotencyKey,
            long amount,
            String currency,
            Long actorUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_attempts (
                        global_payment_attempt_id, payment_order_id, provider_code,
                        idempotency_key, status, requested_amount_minor,
                        requested_currency, created_by_user_id
                    ) VALUES (?, ?, ?, ?, 'CREATED', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalId);
            statement.setLong(2, orderId);
            statement.setString(3, providerCode);
            statement.setString(4, idempotencyKey);
            statement.setLong(5, amount);
            statement.setString(6, currency);
            statement.setLong(7, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentAttempt> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM payment_attempts WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<PaymentAttempt> lockById(Long id) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts WHERE id = ? FOR UPDATE
                """, this::map, id).stream().findFirst();
    }

    public Optional<PaymentAttempt> findByGlobalId(String globalId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts
                WHERE global_payment_attempt_id = ?
                """, this::map, globalId).stream().findFirst();
    }

    public Optional<PaymentAttempt> findByOrderAndKey(Long orderId, String key) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts
                WHERE payment_order_id = ? AND idempotency_key = ?
                """, this::map, orderId, key).stream().findFirst();
    }

    public Optional<PaymentAttempt> lockByProviderAttempt(
            String providerCode,
            String providerAttemptId
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts
                WHERE provider_code = ? AND provider_attempt_id = ?
                FOR UPDATE
                """, this::map, providerCode, providerAttemptId)
                .stream().findFirst();
    }

    public Optional<PaymentAttempt> findByProviderTransaction(
            String providerCode,
            String transactionId
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts
                WHERE provider_code = ? AND provider_transaction_id = ?
                """, this::map, providerCode, transactionId).stream().findFirst();
    }

    public PaymentAttempt updateInitiated(
            Long id,
            String providerAttemptId,
            String status,
            String nextActionType,
            String providerReference
    ) {
        jdbcTemplate.update("""
                UPDATE payment_attempts
                SET provider_attempt_id = ?, status = ?, next_action_type = ?,
                    provider_reference = ?
                WHERE id = ?
                """, providerAttemptId, status, nextActionType, providerReference, id);
        return findById(id).orElseThrow();
    }

    public PaymentAttempt updateStatus(
            Long id,
            String status,
            String providerTransactionId,
            String failureCode,
            String failureMessage,
            LocalDateTime succeededAt,
            LocalDateTime failedAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_attempts
                SET status = ?,
                    provider_transaction_id = COALESCE(?, provider_transaction_id),
                    failure_code = ?, failure_message = ?,
                    succeeded_at = COALESCE(?, succeeded_at),
                    failed_at = COALESCE(?, failed_at)
                WHERE id = ?
                """, status, providerTransactionId, failureCode, failureMessage,
                succeededAt, failedAt, id);
        return findById(id).orElseThrow();
    }

    public List<PaymentAttempt> findByOrder(Long orderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_attempts
                WHERE payment_order_id = ? ORDER BY created_at
                """, this::map, orderId);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_attempts", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM payment_attempts");
    }

    private PaymentAttempt map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PaymentAttempt(
                rs.getLong("id"),
                rs.getString("global_payment_attempt_id"),
                rs.getLong("payment_order_id"),
                rs.getString("provider_code"),
                rs.getString("provider_attempt_id"),
                rs.getString("provider_transaction_id"),
                rs.getString("idempotency_key"),
                rs.getString("status"),
                rs.getLong("requested_amount_minor"),
                rs.getString("requested_currency"),
                rs.getString("next_action_type"),
                rs.getString("provider_reference"),
                rs.getString("failure_code"),
                rs.getString("failure_message"),
                dateTime(rs, "expires_at"),
                dateTime(rs, "succeeded_at"),
                dateTime(rs, "failed_at"),
                rs.getLong("created_by_user_id"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private LocalDateTime dateTime(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
