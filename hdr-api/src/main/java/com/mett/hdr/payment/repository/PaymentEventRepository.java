package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentEvent;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentEvent create(
            String providerCode,
            String providerEventId,
            String eventType,
            String payloadDigest,
            String processingStatus,
            Long orderId,
            Long attemptId,
            String transactionId,
            String sanitizedData,
            String errorCode,
            String errorMessage,
            LocalDateTime receivedAt,
            LocalDateTime processedAt
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_events (
                        provider_code, provider_event_id, event_type, payload_digest,
                        processing_status, payment_order_id, payment_attempt_id,
                        provider_transaction_id, sanitized_event_data,
                        error_code, error_message, received_at, processed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, providerCode);
            statement.setString(2, providerEventId);
            statement.setString(3, eventType);
            statement.setString(4, payloadDigest);
            statement.setString(5, processingStatus);
            setLong(statement, 6, orderId);
            setLong(statement, 7, attemptId);
            statement.setString(8, transactionId);
            statement.setString(9, sanitizedData);
            statement.setString(10, errorCode);
            statement.setString(11, errorMessage);
            statement.setObject(12, receivedAt);
            statement.setObject(13, processedAt);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentEvent> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM payment_events WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<PaymentEvent> findByProviderEvent(
            String providerCode,
            String providerEventId
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_events
                WHERE provider_code = ? AND provider_event_id = ?
                """, this::map, providerCode, providerEventId).stream().findFirst();
    }

    public PaymentEvent updateProcessing(
            Long id,
            String status,
            String errorCode,
            String errorMessage,
            LocalDateTime processedAt
    ) {
        jdbcTemplate.update("""
                UPDATE payment_events
                SET processing_status = ?, error_code = ?, error_message = ?,
                    processed_at = ?
                WHERE id = ?
                """, status, errorCode, errorMessage, processedAt, id);
        return findById(id).orElseThrow();
    }

    public List<PaymentEvent> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM payment_events ORDER BY id", this::map);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_events", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM payment_events");
    }

    private PaymentEvent map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PaymentEvent(
                rs.getLong("id"),
                rs.getString("provider_code"),
                rs.getString("provider_event_id"),
                rs.getString("event_type"),
                rs.getString("payload_digest"),
                rs.getString("processing_status"),
                nullableLong(rs, "payment_order_id"),
                nullableLong(rs, "payment_attempt_id"),
                rs.getString("provider_transaction_id"),
                rs.getString("sanitized_event_data"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                dateTime(rs, "received_at"),
                dateTime(rs, "processed_at"),
                dateTime(rs, "created_at")
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
