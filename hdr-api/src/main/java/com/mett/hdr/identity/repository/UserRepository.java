package com.mett.hdr.identity.repository;

import com.mett.hdr.identity.entity.User;
import com.mett.hdr.identity.entity.IdentitySource;
import com.mett.hdr.identity.entity.UserStatus;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User save(User user) {
        if (user.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO users (
                            global_user_id, identity_source, email, phone, username,
                            password_hash, status, last_login_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                statement.setString(1, user.getGlobalUserId());
                statement.setString(2, user.getIdentitySource().name());
                statement.setString(3, user.getEmail());
                statement.setString(4, user.getPhone());
                statement.setString(5, user.getUsername());
                statement.setString(6, user.getPasswordHash());
                statement.setString(7, user.getStatus().name());
                statement.setObject(8, user.getLastLoginAt());
                return statement;
            }, keyHolder);
            user.setId(keyHolder.getKey().longValue());
        } else {
            jdbcTemplate.update("""
                            UPDATE users
                            SET global_user_id = ?, identity_source = ?, email = ?, phone = ?,
                                username = ?, password_hash = ?, status = ?, last_login_at = ?
                            WHERE id = ?
                            """,
                    user.getGlobalUserId(),
                    user.getIdentitySource().name(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getStatus().name(),
                    user.getLastLoginAt(),
                    user.getId());
        }
        return findById(user.getId()).orElseThrow();
    }

    public Optional<User> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM users WHERE id = ?", this::mapUser, id)
                .stream()
                .findFirst();
    }

    public Optional<User> findByGlobalUserId(String globalUserId) {
        return jdbcTemplate.query("SELECT * FROM users WHERE global_user_id = ?", this::mapUser, globalUserId)
                .stream()
                .findFirst();
    }

    public Optional<User> findByAccount(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT * FROM users
                        WHERE LOWER(email) = LOWER(?) OR phone = ? OR LOWER(username) = LOWER(?)
                        LIMIT 1
                        """, this::mapUser, account, account, account)
                .stream()
                .findFirst();
    }

    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", Long.class, email);
        return count != null && count > 0;
    }

    public boolean existsByPhone(String phone) {
        if (phone == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone = ?", Long.class, phone);
        return count != null && count > 0;
    }

    public Collection<User> findAll() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY id", this::mapUser);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM users");
    }

    private User mapUser(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        User user = new User();
        user.setId(resultSet.getLong("id"));
        user.setGlobalUserId(resultSet.getString("global_user_id"));
        user.setIdentitySource(IdentitySource.valueOf(resultSet.getString("identity_source")));
        user.setEmail(resultSet.getString("email"));
        user.setPhone(resultSet.getString("phone"));
        user.setUsername(resultSet.getString("username"));
        user.setPasswordHash(resultSet.getString("password_hash"));
        user.setStatus(UserStatus.valueOf(resultSet.getString("status")));
        user.setLastLoginAt(toLocalDateTime(resultSet.getTimestamp("last_login_at")));
        user.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("created_at")));
        user.setUpdatedAt(toLocalDateTime(resultSet.getTimestamp("updated_at")));
        return user;
    }

    private LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
