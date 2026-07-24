package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentOrderStateTransition;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderStateTransitionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentOrderStateTransitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(
            Long orderId,
            String stateType,
            String fromState,
            String toState,
            String reason,
            Long actorUserId,
            Long eventId
    ) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_order_state_transitions (
                        payment_order_id, state_type, from_state, to_state,
                        reason, actor_user_id, payment_event_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setLong(1, orderId);
            statement.setString(2, stateType);
            statement.setString(3, fromState);
            statement.setString(4, toState);
            statement.setString(5, reason);
            setLong(statement, 6, actorUserId);
            setLong(statement, 7, eventId);
            return statement;
        });
    }

    public List<PaymentOrderStateTransition> findByOrder(Long orderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_state_transitions
                WHERE payment_order_id = ? ORDER BY id
                """, this::map, orderId);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_order_state_transitions", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM payment_order_state_transitions");
    }

    private PaymentOrderStateTransition map(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new PaymentOrderStateTransition(
                rs.getLong("id"),
                rs.getLong("payment_order_id"),
                rs.getString("state_type"),
                rs.getString("from_state"),
                rs.getString("to_state"),
                rs.getString("reason"),
                nullableLong(rs, "actor_user_id"),
                nullableLong(rs, "payment_event_id"),
                rs.getTimestamp("created_at").toLocalDateTime()
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
}
