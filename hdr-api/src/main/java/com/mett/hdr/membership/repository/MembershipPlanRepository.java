package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.MembershipPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public MembershipPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MembershipPlan> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM membership_plans WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<MembershipPlan> findActive(String planCode, int version) {
        return jdbcTemplate.query("""
                SELECT * FROM membership_plans
                WHERE plan_code = ? AND version = ? AND status = 'ACTIVE'
                """, this::map, planCode, version).stream().findFirst();
    }

    public Optional<MembershipPlan> findDefaultPersonal() {
        return jdbcTemplate.query("""
                SELECT * FROM membership_plans
                WHERE audience_type = 'PERSONAL'
                  AND is_default = TRUE
                  AND status = 'ACTIVE'
                ORDER BY version DESC
                LIMIT 1
                """, this::map).stream().findFirst();
    }

    public List<MembershipPlan> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM membership_plans ORDER BY plan_code, version", this::map);
    }

    private MembershipPlan map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MembershipPlan(
                rs.getLong("id"),
                rs.getString("plan_code"),
                rs.getInt("version"),
                rs.getString("name"),
                rs.getString("audience_type"),
                rs.getString("tier"),
                rs.getString("usage_cycle"),
                rs.getString("status"),
                rs.getBoolean("is_default"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
