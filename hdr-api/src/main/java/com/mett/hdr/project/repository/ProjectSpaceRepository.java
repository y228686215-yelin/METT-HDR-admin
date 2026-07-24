package com.mett.hdr.project.repository;

import com.mett.hdr.project.entity.ProjectSpace;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectSpaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProjectSpace create(
            String globalId,
            Long projectId,
            Long parentId,
            String name,
            String level,
            String usageCode,
            String geometry,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            BigDecimal area,
            BigDecimal volume,
            String orientation,
            int sortOrder,
            Long actorUserId
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO project_spaces (
                        global_space_id, project_id, parent_space_id, name, space_level_type,
                        usage_code, geometry_type, length_m, width_m, height_m, floor_area_m2,
                        volume_m3, orientation_code, status, sort_order, created_by_user_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, globalId);
            statement.setLong(2, projectId);
            setNullableLong(statement, 3, parentId);
            statement.setString(4, name);
            statement.setString(5, level);
            statement.setString(6, usageCode);
            statement.setString(7, geometry);
            statement.setBigDecimal(8, length);
            statement.setBigDecimal(9, width);
            statement.setBigDecimal(10, height);
            statement.setBigDecimal(11, area);
            statement.setBigDecimal(12, volume);
            statement.setString(13, orientation);
            statement.setInt(14, sortOrder);
            statement.setLong(15, actorUserId);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<ProjectSpace> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM project_spaces WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<ProjectSpace> findByGlobalId(String globalId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_spaces WHERE global_space_id = ?
                """, this::map, globalId).stream().findFirst();
    }

    public Optional<ProjectSpace> lockByGlobalId(String globalId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_spaces WHERE global_space_id = ? FOR UPDATE
                """, this::map, globalId).stream().findFirst();
    }

    public List<ProjectSpace> findAll(Long projectId) {
        return jdbcTemplate.query("""
                SELECT * FROM project_spaces WHERE project_id = ? ORDER BY created_at
                """, this::map, projectId);
    }

    public ProjectSpace update(
            Long id,
            Long parentId,
            String name,
            String level,
            String usageCode,
            String geometry,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            BigDecimal area,
            BigDecimal volume,
            String orientation,
            int sortOrder
    ) {
        jdbcTemplate.update("""
                UPDATE project_spaces
                SET parent_space_id = ?, name = ?, space_level_type = ?, usage_code = ?,
                    geometry_type = ?, length_m = ?, width_m = ?, height_m = ?, floor_area_m2 = ?,
                    volume_m3 = ?, orientation_code = ?, sort_order = ?
                WHERE id = ?
                """, parentId, name, level, usageCode, geometry, length, width, height, area, volume,
                orientation, sortOrder, id);
        return findById(id).orElseThrow();
    }

    public ProjectSpace updateStatus(Long id, String status) {
        jdbcTemplate.update("""
                UPDATE project_spaces
                SET status = ?, archived_at = CASE WHEN ? = 'ARCHIVED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END
                WHERE id = ?
                """, status, status, id);
        return findById(id).orElseThrow();
    }

    public long countActiveChildren(Long parentId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_spaces
                WHERE parent_space_id = ? AND status = 'ACTIVE'
                """, Long.class, parentId);
        return count == null ? 0 : count;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM project_spaces");
    }

    private ProjectSpace map(ResultSet rs, int row) throws SQLException {
        return new ProjectSpace(
                rs.getLong("id"),
                rs.getString("global_space_id"),
                rs.getLong("project_id"),
                nullableLong(rs, "parent_space_id"),
                rs.getString("name"),
                rs.getString("space_level_type"),
                rs.getString("usage_code"),
                rs.getString("geometry_type"),
                rs.getBigDecimal("length_m"),
                rs.getBigDecimal("width_m"),
                rs.getBigDecimal("height_m"),
                rs.getBigDecimal("floor_area_m2"),
                rs.getBigDecimal("volume_m3"),
                rs.getString("orientation_code"),
                rs.getString("status"),
                rs.getInt("sort_order"),
                rs.getLong("created_by_user_id"),
                rs.getTimestamp("archived_at") == null
                        ? null : rs.getTimestamp("archived_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
