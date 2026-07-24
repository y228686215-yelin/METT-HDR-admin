package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.EntitlementDefinition;
import com.mett.hdr.membership.entity.MembershipPlanEntitlement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementDefinitionRepository {

    private final JdbcTemplate jdbcTemplate;

    public EntitlementDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<EntitlementDefinition> findByCode(String code) {
        return jdbcTemplate.query("""
                SELECT * FROM entitlement_definitions WHERE code = ?
                """, this::mapDefinition, code).stream().findFirst();
    }

    public Optional<EntitlementDefinition> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT * FROM entitlement_definitions WHERE id = ?
                """, this::mapDefinition, id).stream().findFirst();
    }

    public List<MembershipPlanEntitlement> findForPlan(Long membershipPlanId) {
        return jdbcTemplate.query("""
                SELECT mpe.*, ed.code AS entitlement_code,
                       ed.entitlement_type, ed.status AS entitlement_status
                FROM membership_plan_entitlements mpe
                JOIN entitlement_definitions ed
                  ON ed.id = mpe.entitlement_definition_id
                WHERE mpe.membership_plan_id = ?
                ORDER BY ed.code
                """, this::mapPlanEntitlement, membershipPlanId);
    }

    public Optional<MembershipPlanEntitlement> findForPlanAndCode(
            Long membershipPlanId,
            String entitlementCode
    ) {
        return jdbcTemplate.query("""
                SELECT mpe.*, ed.code AS entitlement_code,
                       ed.entitlement_type, ed.status AS entitlement_status
                FROM membership_plan_entitlements mpe
                JOIN entitlement_definitions ed
                  ON ed.id = mpe.entitlement_definition_id
                WHERE mpe.membership_plan_id = ? AND ed.code = ?
                """, this::mapPlanEntitlement, membershipPlanId, entitlementCode)
                .stream().findFirst();
    }

    private EntitlementDefinition mapDefinition(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        return new EntitlementDefinition(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("entitlement_type"),
                rs.getString("quota_unit"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private MembershipPlanEntitlement mapPlanEntitlement(
            java.sql.ResultSet rs,
            int row
    ) throws java.sql.SQLException {
        long quotaLimit = rs.getLong("quota_limit");
        Long nullableQuotaLimit = rs.wasNull() ? null : quotaLimit;
        return new MembershipPlanEntitlement(
                rs.getLong("id"),
                rs.getLong("membership_plan_id"),
                rs.getLong("entitlement_definition_id"),
                rs.getBoolean("enabled"),
                nullableQuotaLimit,
                rs.getBoolean("is_unlimited"),
                rs.getString("entitlement_code"),
                rs.getString("entitlement_type"),
                rs.getString("entitlement_status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
