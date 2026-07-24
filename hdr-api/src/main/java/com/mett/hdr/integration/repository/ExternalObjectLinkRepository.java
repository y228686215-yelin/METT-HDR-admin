package com.mett.hdr.integration.repository;

import com.mett.hdr.integration.entity.ExternalObjectLink;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalObjectLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExternalObjectLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ExternalObjectLink create(
            String localObjectType,
            Long localObjectId,
            String localGlobalId,
            String externalSystem,
            String externalObjectType,
            String externalObjectId,
            String externalGlobalId,
            String relationType
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO external_object_links (
                        local_object_type, local_object_id, local_global_id, external_system,
                        external_object_type, external_object_id, external_global_id, relation_type, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """, new String[]{"id"});
            statement.setString(1, localObjectType);
            statement.setLong(2, localObjectId);
            statement.setString(3, localGlobalId);
            statement.setString(4, externalSystem);
            statement.setString(5, externalObjectType);
            statement.setString(6, externalObjectId);
            statement.setString(7, externalGlobalId);
            statement.setString(8, relationType);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<ExternalObjectLink> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM external_object_links WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public Optional<ExternalObjectLink> find(
            String localType,
            String localGlobalId,
            String externalSystem,
            String externalType,
            String relationType
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM external_object_links
                WHERE local_object_type = ? AND local_global_id = ? AND external_system = ?
                  AND external_object_type = ? AND relation_type = ?
                """, this::map, localType, localGlobalId, externalSystem, externalType, relationType)
                .stream().findFirst();
    }

    public List<ExternalObjectLink> findByLocalGlobalId(String localGlobalId) {
        return jdbcTemplate.query("""
                SELECT * FROM external_object_links
                WHERE local_global_id = ? ORDER BY created_at
                """, this::map, localGlobalId);
    }

    public List<ExternalObjectLink> findByExternalGlobalId(
            String externalSystem,
            String externalGlobalId
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM external_object_links
                WHERE external_system = ? AND external_global_id = ? ORDER BY created_at
                """, this::map, externalSystem, externalGlobalId);
    }

    public ExternalObjectLink updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE external_object_links SET status = ? WHERE id = ?", status, id);
        return findById(id).orElseThrow();
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM external_object_links");
    }

    private ExternalObjectLink map(ResultSet rs, int row) throws SQLException {
        return new ExternalObjectLink(
                rs.getLong("id"),
                rs.getString("local_object_type"),
                rs.getLong("local_object_id"),
                rs.getString("local_global_id"),
                rs.getString("external_system"),
                rs.getString("external_object_type"),
                rs.getString("external_object_id"),
                rs.getString("external_global_id"),
                rs.getString("relation_type"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
