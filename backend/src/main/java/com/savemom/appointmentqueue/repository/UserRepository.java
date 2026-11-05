package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.Role;
import com.savemom.appointmentqueue.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing User records in MySQL using Spring's JdbcTemplate.
 * Straightforward SQL queries without ORM magic or hidden proxies.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    // Maps a database result row to a User object
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getTimestamp("created_at").toInstant()
    );

    @Autowired
    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Looks up a user by email address (case-insensitive).
     */
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, email, password_hash, role, created_at FROM users WHERE LOWER(email) = LOWER(?)";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email.trim());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Looks up a user by unique numeric ID.
     */
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, email, password_hash, role, created_at FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if an email is already registered in the system.
     */
    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email.trim());
        return count != null && count > 0;
    }

    /**
     * Inserts a new user or updates an existing one.
     */
    public User save(User user) {
        if (user.getId() == null) {
            String sql = "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            Timestamp now = Timestamp.from(Instant.now());

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, user.getName().trim());
                ps.setString(2, user.getEmail().trim().toLowerCase());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getRole().name());
                ps.setTimestamp(5, now);
                return ps;
            }, keyHolder);

            java.util.Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null) {
                Object idVal = keys.get("ID");
                if (idVal == null) {
                    idVal = keys.get("id");
                }
                if (idVal == null && !keys.isEmpty()) {
                    idVal = keys.values().iterator().next();
                }
                if (idVal instanceof Number) {
                    user.setId(((Number) idVal).longValue());
                }
            } else if (keyHolder.getKey() != null) {
                user.setId(keyHolder.getKey().longValue());
            }
            user.setCreatedAt(now.toInstant());
            return user;
        } else {
            String sql = "UPDATE users SET name = ?, role = ? WHERE id = ?";
            jdbcTemplate.update(sql, user.getName().trim(), user.getRole().name(), user.getId());
            return user;
        }
    }

    /**
     * Utility method for developers/admins to assign a role to a user.
     */
    public int updateUserRole(Long userId, Role newRole) {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        return jdbcTemplate.update(sql, newRole.name(), userId);
    }

    /**
     * Returns total count of registered users.
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Lists all registered users (for admin dashboard).
     */
    public List<User> findAll() {
        String sql = "SELECT id, name, email, password_hash, role, created_at FROM users ORDER BY id DESC";
        return jdbcTemplate.query(sql, userRowMapper);
    }
}
