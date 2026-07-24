package com.mett.hdr.payment.repository;

import com.mett.hdr.payment.entity.MembershipPlanOffer;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPlanOfferRepository {

    private final JdbcTemplate jdbcTemplate;

    public MembershipPlanOfferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MembershipPlanOffer create(
            String globalOfferId,
            String offerCode,
            int version,
            Long planId,
            String status,
            String currency,
            long amountMinor,
            int durationMonths,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO membership_plan_offers (
                        global_offer_id, offer_code, version, membership_plan_id,
                        status, purchase_mode, currency, amount_minor,
                        membership_duration_months, valid_from, valid_until
                    ) VALUES (?, ?, ?, ?, ?, 'ONE_TIME_TERM', ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalOfferId);
            statement.setString(2, offerCode);
            statement.setInt(3, version);
            statement.setLong(4, planId);
            statement.setString(5, status);
            statement.setString(6, currency);
            statement.setLong(7, amountMinor);
            statement.setInt(8, durationMonths);
            statement.setObject(9, validFrom);
            statement.setObject(10, validUntil);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<MembershipPlanOffer> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM membership_plan_offers WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<MembershipPlanOffer> findByGlobalId(String globalOfferId) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_plan_offers WHERE global_offer_id = ?
                """, this::map, globalOfferId).stream().findFirst();
    }

    public List<MembershipPlanOffer> findAvailable(
            LocalDateTime now,
            String audienceType
    ) {
        String audienceFilter = audienceType == null
                ? ""
                : " AND p.audience_type = ?";
        String sql = """
                SELECT o.*
                FROM membership_plan_offers o
                JOIN membership_plans p ON p.id = o.membership_plan_id
                WHERE o.status = 'ACTIVE'
                  AND p.status = 'ACTIVE'
                  AND (o.valid_from IS NULL OR o.valid_from <= ?)
                  AND (o.valid_until IS NULL OR o.valid_until > ?)
                """ + audienceFilter + " ORDER BY o.offer_code, o.version";
        if (audienceType == null) {
            return jdbcTemplate.query(sql, this::map, now, now);
        }
        return jdbcTemplate.query(sql, this::map, now, now, audienceType);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_plan_offers", Long.class);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM membership_plan_offers");
    }

    private MembershipPlanOffer map(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new MembershipPlanOffer(
                rs.getLong("id"),
                rs.getString("global_offer_id"),
                rs.getString("offer_code"),
                rs.getInt("version"),
                rs.getLong("membership_plan_id"),
                rs.getString("status"),
                rs.getString("purchase_mode"),
                rs.getString("currency"),
                rs.getLong("amount_minor"),
                rs.getInt("membership_duration_months"),
                dateTime(rs, "valid_from"),
                dateTime(rs, "valid_until"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private LocalDateTime dateTime(
            java.sql.ResultSet rs,
            String column
    ) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
