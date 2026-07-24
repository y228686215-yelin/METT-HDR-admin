package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.PaymentOrderItem;
import java.sql.PreparedStatement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderItemRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentOrderItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentOrderItem create(
            Long orderId,
            Long offerId,
            Long planId,
            String currency,
            long amountMinor,
            String planCode,
            int planVersion,
            String tier,
            String offerCode,
            int offerVersion,
            int durationMonths,
            String snapshot
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_order_items (
                        payment_order_id, item_type, membership_plan_offer_id,
                        membership_plan_id, quantity, currency, unit_amount_minor,
                        total_amount_minor, plan_code_snapshot, plan_version_snapshot,
                        tier_snapshot, offer_code_snapshot, offer_version_snapshot,
                        membership_duration_months, item_snapshot
                    ) VALUES (?, 'MEMBERSHIP_PLAN', ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                              CAST(? AS JSON))
                    """, new String[]{"id"});
            statement.setLong(1, orderId);
            statement.setLong(2, offerId);
            statement.setLong(3, planId);
            statement.setString(4, currency);
            statement.setLong(5, amountMinor);
            statement.setLong(6, amountMinor);
            statement.setString(7, planCode);
            statement.setInt(8, planVersion);
            statement.setString(9, tier);
            statement.setString(10, offerCode);
            statement.setInt(11, offerVersion);
            statement.setInt(12, durationMonths);
            statement.setString(13, snapshot);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<PaymentOrderItem> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_items WHERE id = ?
                """, this::map, id).stream().findFirst();
    }

    public Optional<PaymentOrderItem> findByOrderId(Long orderId) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_order_items WHERE payment_order_id = ?
                """, this::map, orderId).stream().findFirst();
    }

    private PaymentOrderItem map(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new PaymentOrderItem(
                rs.getLong("id"),
                rs.getLong("payment_order_id"),
                rs.getString("item_type"),
                rs.getLong("membership_plan_offer_id"),
                rs.getLong("membership_plan_id"),
                rs.getInt("quantity"),
                rs.getString("currency"),
                rs.getLong("unit_amount_minor"),
                rs.getLong("total_amount_minor"),
                rs.getString("plan_code_snapshot"),
                rs.getInt("plan_version_snapshot"),
                rs.getString("tier_snapshot"),
                rs.getString("offer_code_snapshot"),
                rs.getInt("offer_version_snapshot"),
                rs.getInt("membership_duration_months"),
                rs.getString("item_snapshot"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
