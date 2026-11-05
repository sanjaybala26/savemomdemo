package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.Appointment;
import com.savemom.appointmentqueue.model.AppointmentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access operations for Appointment entities using JdbcTemplate.
 */
@Repository
public class AppointmentRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Appointment> rowMapper = (rs, rowNum) -> {
        Date sqlDate = rs.getDate("appointment_date");
        LocalDate localDate = (sqlDate != null) ? sqlDate.toLocalDate() : null;

        String statusStr = rs.getString("status");
        AppointmentStatus status = null;
        if (statusStr != null) {
            try {
                status = AppointmentStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                status = AppointmentStatus.PENDING;
            }
        }

        return new Appointment(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getLong("provider_id"),
                localDate,
                rs.getString("start_time"),
                rs.getString("end_time"),
                status,
                rs.getString("notes"),
                rs.getString("active_slot_lock"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
        );
    };

    @Autowired
    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Appointment> findById(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Appointment> findByCustomerId(Long customerId) {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "WHERE customer_id = ? ORDER BY appointment_date DESC, start_time DESC";
        return jdbcTemplate.query(sql, rowMapper, customerId);
    }

    public List<Appointment> findByProviderId(Long providerId) {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "WHERE provider_id = ? ORDER BY appointment_date DESC, start_time DESC";
        return jdbcTemplate.query(sql, rowMapper, providerId);
    }

    public List<Appointment> findByProviderIdOrCustomerId(Long providerId, Long customerId) {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "WHERE provider_id = ? OR customer_id = ? ORDER BY appointment_date DESC, start_time DESC";
        return jdbcTemplate.query(sql, rowMapper, providerId, customerId);
    }

    public List<Appointment> findAll() {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "ORDER BY appointment_date DESC, start_time DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Appointment> findActiveByProviderAndDate(Long providerId, LocalDate date) {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "WHERE provider_id = ? AND appointment_date = ? AND status != 'CANCELLED' " +
                "ORDER BY start_time ASC";
        return jdbcTemplate.query(sql, rowMapper, providerId, Date.valueOf(date));
    }

    public Optional<Appointment> findActiveByProviderAndSlot(Long providerId, LocalDate date, String startTime) {
        String sql = "SELECT id, customer_id, provider_id, appointment_date, start_time, end_time, status, " +
                "notes, active_slot_lock, created_at, updated_at FROM appointments " +
                "WHERE provider_id = ? AND appointment_date = ? AND start_time = ? AND status != 'CANCELLED'";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, providerId, Date.valueOf(date), startTime));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Appointment save(Appointment appt) {
        Timestamp now = Timestamp.from(Instant.now());

        if (appt.getId() == null) {
            String sql = "INSERT INTO appointments (customer_id, provider_id, appointment_date, start_time, " +
                    "end_time, status, notes, active_slot_lock, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, appt.getCustomerId());
                ps.setLong(2, appt.getProviderId());
                ps.setDate(3, Date.valueOf(appt.getAppointmentDate()));
                ps.setString(4, appt.getStartTime());
                ps.setString(5, appt.getEndTime());
                ps.setString(6, appt.getStatus().name());
                ps.setString(7, appt.getNotes());
                ps.setString(8, appt.getActiveSlotLock());
                ps.setTimestamp(9, now);
                ps.setTimestamp(10, now);
                return ps;
            }, keyHolder);

            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null) {
                Object idVal = keys.get("ID");
                if (idVal == null) idVal = keys.get("id");
                if (idVal == null && !keys.isEmpty()) idVal = keys.values().iterator().next();
                if (idVal instanceof Number) appt.setId(((Number) idVal).longValue());
            } else if (keyHolder.getKey() != null) {
                appt.setId(keyHolder.getKey().longValue());
            }
            appt.setCreatedAt(now.toInstant());
            appt.setUpdatedAt(now.toInstant());
            return appt;
        } else {
            String sql = "UPDATE appointments SET customer_id = ?, provider_id = ?, appointment_date = ?, " +
                    "start_time = ?, end_time = ?, status = ?, notes = ?, active_slot_lock = ?, updated_at = ? " +
                    "WHERE id = ?";
            jdbcTemplate.update(
                    sql,
                    appt.getCustomerId(),
                    appt.getProviderId(),
                    Date.valueOf(appt.getAppointmentDate()),
                    appt.getStartTime(),
                    appt.getEndTime(),
                    appt.getStatus().name(),
                    appt.getNotes(),
                    appt.getActiveSlotLock(),
                    now,
                    appt.getId()
            );
            appt.setUpdatedAt(now.toInstant());
            return appt;
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM appointments";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public long countByStatus(AppointmentStatus status) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, status.name());
        return count != null ? count : 0;
    }

    /**
     * Retrieves daily booking statistics grouped by appointment_date.
     */
    public List<java.util.Map<String, Object>> getDailyBookingStats(LocalDate startDate, LocalDate endDate) {
        String sql;
        if (startDate != null && endDate != null) {
            sql = "SELECT appointment_date, " +
                  "COUNT(*) as total, " +
                  "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
                  "SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed, " +
                  "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                  "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled " +
                  "FROM appointments " +
                  "WHERE appointment_date BETWEEN ? AND ? " +
                  "GROUP BY appointment_date " +
                  "ORDER BY appointment_date ASC";
            return jdbcTemplate.queryForList(sql, Date.valueOf(startDate), Date.valueOf(endDate));
        } else {
            sql = "SELECT appointment_date, " +
                  "COUNT(*) as total, " +
                  "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
                  "SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed, " +
                  "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                  "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled " +
                  "FROM appointments " +
                  "GROUP BY appointment_date " +
                  "ORDER BY appointment_date ASC LIMIT 30";
            return jdbcTemplate.queryForList(sql);
        }
    }

    /**
     * Retrieves monthly booking statistics grouped by YYYY-MM.
     */
    public List<java.util.Map<String, Object>> getMonthlyBookingStats() {
        String sql = "SELECT DATE_FORMAT(appointment_date, '%Y-%m') as month_str, " +
                     "COUNT(*) as total, " +
                     "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
                     "SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed, " +
                     "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                     "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled " +
                     "FROM appointments " +
                     "GROUP BY DATE_FORMAT(appointment_date, '%Y-%m') " +
                     "ORDER BY month_str ASC LIMIT 12";
        return jdbcTemplate.queryForList(sql);
    }
}
