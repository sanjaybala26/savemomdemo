package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.ProviderAvailability;
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
import java.util.Map;
import java.util.Optional;

/**
 * Data access operations for ProviderAvailability entities using JdbcTemplate.
 */
@Repository
public class ProviderAvailabilityRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProviderAvailability> rowMapper = (rs, rowNum) -> new ProviderAvailability(
            rs.getLong("id"),
            rs.getLong("provider_id"),
            rs.getString("working_days"),
            rs.getString("open_time"),
            rs.getString("close_time"),
            rs.getInt("slot_duration_minutes"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
    );

    @Autowired
    public ProviderAvailabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProviderAvailability> findByProviderId(Long providerId) {
        if (providerId == null) return Optional.empty();
        String sql = "SELECT id, provider_id, working_days, open_time, close_time, slot_duration_minutes, created_at, updated_at " +
                "FROM provider_availability WHERE provider_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, providerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public ProviderAvailability save(ProviderAvailability availability) {
        Timestamp now = Timestamp.from(Instant.now());

        if (availability.getId() == null) {
            String sql = "INSERT INTO provider_availability (provider_id, working_days, open_time, close_time, slot_duration_minutes, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, availability.getProviderId());
                ps.setString(2, availability.getWorkingDays());
                ps.setString(3, availability.getOpenTime());
                ps.setString(4, availability.getCloseTime());
                ps.setInt(5, availability.getSlotDurationMinutes());
                ps.setTimestamp(6, now);
                ps.setTimestamp(7, now);
                return ps;
            }, keyHolder);

            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null) {
                Object idVal = keys.get("ID");
                if (idVal == null) idVal = keys.get("id");
                if (idVal == null && !keys.isEmpty()) idVal = keys.values().iterator().next();
                if (idVal instanceof Number) availability.setId(((Number) idVal).longValue());
            } else if (keyHolder.getKey() != null) {
                availability.setId(keyHolder.getKey().longValue());
            }
            availability.setCreatedAt(now.toInstant());
            availability.setUpdatedAt(now.toInstant());
            return availability;
        } else {
            String sql = "UPDATE provider_availability SET working_days = ?, open_time = ?, close_time = ?, slot_duration_minutes = ?, updated_at = ? " +
                    "WHERE id = ?";
            jdbcTemplate.update(
                    sql,
                    availability.getWorkingDays(),
                    availability.getOpenTime(),
                    availability.getCloseTime(),
                    availability.getSlotDurationMinutes(),
                    now,
                    availability.getId()
            );
            availability.setUpdatedAt(now.toInstant());
            return availability;
        }
    }
}
