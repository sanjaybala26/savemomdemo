package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.ProviderProfile;
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
import java.util.Map;
import java.util.Optional;

/**
 * Data access operations for ProviderProfile entities using JdbcTemplate.
 */
@Repository
public class ProviderProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProviderProfile> rowMapper = (rs, rowNum) -> new ProviderProfile(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("display_name"),
            rs.getString("service_description"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
    );

    @Autowired
    public ProviderProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProviderProfile> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        String sql = "SELECT id, user_id, display_name, service_description, is_active, created_at, updated_at " +
                "FROM provider_profiles WHERE user_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ProviderProfile> findById(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT id, user_id, display_name, service_description, is_active, created_at, updated_at " +
                "FROM provider_profiles WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ProviderProfile> findAllActive() {
        String sql = "SELECT id, user_id, display_name, service_description, is_active, created_at, updated_at " +
                "FROM provider_profiles WHERE is_active = true ORDER BY display_name ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<ProviderProfile> findAll() {
        String sql = "SELECT id, user_id, display_name, service_description, is_active, created_at, updated_at " +
                "FROM provider_profiles ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM provider_profiles";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public ProviderProfile save(ProviderProfile profile) {
        Timestamp now = Timestamp.from(Instant.now());

        if (profile.getId() == null) {
            String sql = "INSERT INTO provider_profiles (user_id, display_name, service_description, is_active, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, profile.getUserId());
                ps.setString(2, profile.getDisplayName().trim());
                ps.setString(3, profile.getServiceDescription());
                ps.setBoolean(4, profile.isActive());
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
                return ps;
            }, keyHolder);

            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null) {
                Object idVal = keys.get("ID");
                if (idVal == null) idVal = keys.get("id");
                if (idVal == null && !keys.isEmpty()) idVal = keys.values().iterator().next();
                if (idVal instanceof Number) profile.setId(((Number) idVal).longValue());
            } else if (keyHolder.getKey() != null) {
                profile.setId(keyHolder.getKey().longValue());
            }
            profile.setCreatedAt(now.toInstant());
            profile.setUpdatedAt(now.toInstant());
            return profile;
        } else {
            String sql = "UPDATE provider_profiles SET display_name = ?, service_description = ?, is_active = ?, updated_at = ? " +
                    "WHERE id = ?";
            jdbcTemplate.update(sql, profile.getDisplayName().trim(), profile.getServiceDescription(), profile.isActive(), now, profile.getId());
            profile.setUpdatedAt(now.toInstant());
            return profile;
        }
    }
}
