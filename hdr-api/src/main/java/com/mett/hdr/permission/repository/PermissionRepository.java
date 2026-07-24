package com.mett.hdr.permission.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PermissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void assignRole(Long userId, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, r.id
                FROM roles r
                WHERE r.code = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM user_roles ur
                      WHERE ur.user_id = ? AND ur.role_id = r.id
                  )
                """, userId, roleCode, userId);
    }

    public List<String> findRoleCodesByUserId(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.code
                FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.code
                """, String.class, userId);
    }

    public void clearAssignments() {
        jdbcTemplate.update("DELETE FROM user_roles");
    }
}
