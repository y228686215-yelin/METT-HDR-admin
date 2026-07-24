package com.mett.hdr.identity.repository;

import com.mett.hdr.identity.entity.UserProfile;
import java.sql.PreparedStatement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserProfile createDefault(Long userId, String displayName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_profiles (user_id, display_name, language)
                    VALUES (?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, userId);
            statement.setString(2, displayName);
            statement.setString(3, "zh-CN");
            return statement;
        }, keyHolder);
        return findByUserId(userId).orElseThrow();
    }

    public Optional<UserProfile> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM user_profiles WHERE user_id = ?", (resultSet, rowNum) ->
                        new UserProfile(
                                resultSet.getLong("id"),
                                resultSet.getLong("user_id"),
                                resultSet.getString("display_name"),
                                resultSet.getString("avatar_file_id"),
                                resultSet.getString("company_name"),
                                resultSet.getString("country"),
                                resultSet.getString("language"),
                                resultSet.getTimestamp("created_at").toLocalDateTime(),
                                resultSet.getTimestamp("updated_at").toLocalDateTime()
                        ), userId)
                .stream()
                .findFirst();
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM user_profiles");
    }
}
