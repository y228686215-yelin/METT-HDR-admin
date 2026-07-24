package com.mett.hdr.membership.repository;

import com.mett.hdr.membership.entity.UsageSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UsageSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public UsageSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UsageSnapshot create(
            Long membershipId,
            LocalDateTime cycleStartAt,
            LocalDateTime cycleEndAt,
            String snapshotData
    ) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO usage_snapshots (
                        membership_id, cycle_start_at, cycle_end_at, snapshot_data
                    ) VALUES (?, ?, ?, CAST(? AS JSON))
                    """, new String[]{"id"});
            statement.setLong(1, membershipId);
            statement.setObject(2, cycleStartAt);
            statement.setObject(3, cycleEndAt);
            statement.setString(4, snapshotData);
            return statement;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    public Optional<UsageSnapshot> findCycle(
            Long membershipId,
            LocalDateTime cycleStartAt,
            LocalDateTime cycleEndAt
    ) {
        return jdbcTemplate.query("""
                SELECT * FROM usage_snapshots
                WHERE membership_id = ?
                  AND cycle_start_at = ?
                  AND cycle_end_at = ?
                """, this::map, membershipId, cycleStartAt, cycleEndAt)
                .stream().findFirst();
    }

    public List<UsageSnapshot> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM usage_snapshots ORDER BY id", this::map);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usage_snapshots", Long.class);
        return count == null ? 0 : count;
    }

    private Optional<UsageSnapshot> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM usage_snapshots WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    private UsageSnapshot map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new UsageSnapshot(
                rs.getLong("id"),
                rs.getLong("membership_id"),
                rs.getTimestamp("cycle_start_at").toLocalDateTime(),
                rs.getTimestamp("cycle_end_at").toLocalDateTime(),
                rs.getString("snapshot_data"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
