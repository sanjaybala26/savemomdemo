package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.QueueStatus;
import com.savemom.appointmentqueue.model.QueueToken;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Data access operations for patient queue tokens using JdbcTemplate.
 */
@Repository
public class QueueTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<QueueToken> rowMapper = (rs, rowNum) -> {
        Timestamp calledTs = rs.getTimestamp("called_at");
        Timestamp completedTs = rs.getTimestamp("completed_at");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Date apptDate = rs.getDate("appointment_date");

        return new QueueToken(
                rs.getLong("id"),
                rs.getLong("appointment_id"),
                rs.getLong("provider_id"),
                apptDate != null ? apptDate.toLocalDate() : null,
                rs.getInt("token_number"),
                rs.getString("token_display"),
                QueueStatus.valueOf(rs.getString("status")),
                calledTs != null ? calledTs.toInstant() : null,
                completedTs != null ? completedTs.toInstant() : null,
                rs.getString("notes"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    };

    @Autowired
    public QueueTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueueToken save(QueueToken token) {
        Timestamp now = Timestamp.from(Instant.now());
        String sql = "INSERT INTO queue_tokens (appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, token.getAppointmentId());
            ps.setLong(2, token.getProviderId());
            ps.setDate(3, Date.valueOf(token.getAppointmentDate()));
            ps.setInt(4, token.getTokenNumber());
            ps.setString(5, token.getTokenDisplay());
            ps.setString(6, token.getStatus().name());
            ps.setTimestamp(7, token.getCalledAt() != null ? Timestamp.from(token.getCalledAt()) : null);
            ps.setTimestamp(8, token.getCompletedAt() != null ? Timestamp.from(token.getCompletedAt()) : null);
            ps.setString(9, token.getNotes());
            ps.setTimestamp(10, now);
            ps.setTimestamp(11, now);
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object idVal = keys.get("ID");
            if (idVal == null) idVal = keys.get("id");
            if (idVal == null && !keys.isEmpty()) idVal = keys.values().iterator().next();
            if (idVal instanceof Number) token.setId(((Number) idVal).longValue());
        } else if (keyHolder.getKey() != null) {
            token.setId(keyHolder.getKey().longValue());
        }

        token.setCreatedAt(now.toInstant());
        token.setUpdatedAt(now.toInstant());
        return token;
    }

    public Optional<QueueToken> findById(Long id) {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens WHERE id = ?";
        List<QueueToken> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<QueueToken> findByAppointmentId(Long appointmentId) {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens WHERE appointment_id = ?";
        List<QueueToken> list = jdbcTemplate.query(sql, rowMapper, appointmentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<QueueToken> findByProviderIdAndDate(Long providerId, LocalDate date) {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens WHERE provider_id = ? AND appointment_date = ? ORDER BY token_number ASC";
        return jdbcTemplate.query(sql, rowMapper, providerId, Date.valueOf(date));
    }

    public Optional<QueueToken> findNextWaitingToken(Long providerId, LocalDate date) {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens WHERE provider_id = ? AND appointment_date = ? AND status = 'WAITING' ORDER BY token_number ASC LIMIT 1 FOR UPDATE";
        List<QueueToken> list = jdbcTemplate.query(sql, rowMapper, providerId, Date.valueOf(date));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void update(QueueToken token) {
        Timestamp now = Timestamp.from(Instant.now());
        String sql = "UPDATE queue_tokens SET provider_id = ?, appointment_date = ?, status = ?, called_at = ?, completed_at = ?, notes = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(
                sql,
                token.getProviderId(),
                Date.valueOf(token.getAppointmentDate()),
                token.getStatus().name(),
                token.getCalledAt() != null ? Timestamp.from(token.getCalledAt()) : null,
                token.getCompletedAt() != null ? Timestamp.from(token.getCompletedAt()) : null,
                token.getNotes(),
                now,
                token.getId()
        );
        token.setUpdatedAt(now.toInstant());
    }

    /**
     * Counts the total number of queue tokens ever generated.
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM queue_tokens";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Finds the token currently being served (CALLED or IN_PROGRESS) for a provider and date.
     */
    public Optional<QueueToken> findCurrentlyServing(Long providerId, LocalDate date) {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens WHERE provider_id = ? AND appointment_date = ? AND status IN ('IN_PROGRESS', 'CALLED') " +
                "ORDER BY FIELD(status, 'IN_PROGRESS', 'CALLED'), token_number ASC LIMIT 1";
        try {
            List<QueueToken> list = jdbcTemplate.query(sql, rowMapper, providerId, Date.valueOf(date));
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Counts the number of active patients ahead of a given token number on the same date for the same provider.
     * Tokens ahead must have token_number < tokenNumber and an active status (WAITING, CALLED, IN_PROGRESS).
     */
    public int countActiveTokensAhead(Long providerId, LocalDate date, int tokenNumber) {
        String sql = "SELECT COUNT(*) FROM queue_tokens " +
                "WHERE provider_id = ? AND appointment_date = ? AND token_number < ? " +
                "AND status IN ('WAITING', 'CALLED', 'IN_PROGRESS')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, providerId, Date.valueOf(date), tokenNumber);
        return count != null ? count : 0;
    }

    /**
     * Finds all queue tokens booked by a specific customer.
     */
    public List<QueueToken> findByCustomerId(Long customerId) {
        String sql = "SELECT t.id, t.appointment_id, t.provider_id, t.appointment_date, t.token_number, t.token_display, t.status, t.called_at, t.completed_at, t.notes, t.created_at, t.updated_at " +
                "FROM queue_tokens t " +
                "JOIN appointments a ON t.appointment_id = a.id " +
                "WHERE a.customer_id = ? ORDER BY t.id DESC";
        return jdbcTemplate.query(sql, rowMapper, customerId);
    }

    /**
     * Returns total count of tokens with a specific status.
     */
    public long countByStatus(QueueStatus status) {
        String sql = "SELECT COUNT(*) FROM queue_tokens WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, status.name());
        return count != null ? count : 0;
    }

    /**
     * Returns total count of tokens matching any of the specified statuses.
     */
    public long countByStatusIn(String... statuses) {
        if (statuses.length == 0) return 0;
        String inSql = String.join("','", statuses);
        String sql = "SELECT COUNT(*) FROM queue_tokens WHERE status IN ('" + inSql + "')";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Returns all queue tokens (for admin dashboard activity feed).
     */
    public List<QueueToken> findAll() {
        String sql = "SELECT id, appointment_id, provider_id, appointment_date, token_number, token_display, status, called_at, completed_at, notes, created_at, updated_at " +
                "FROM queue_tokens ORDER BY id DESC LIMIT 100";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * Calculates the true average customer wait time (in minutes) based on stored timestamps:
     * TIMESTAMPDIFF(MINUTE, created_at, called_at) for all tokens that have been called.
     * Returns null if no tokens have been called yet.
     */
    public Double getAverageWaitMinutes(LocalDate startDate, LocalDate endDate) {
        String sql;
        if (startDate != null && endDate != null) {
            sql = "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, called_at)) " +
                  "FROM queue_tokens " +
                  "WHERE called_at IS NOT NULL AND created_at IS NOT NULL AND called_at >= created_at " +
                  "AND appointment_date BETWEEN ? AND ?";
            return jdbcTemplate.queryForObject(sql, Double.class, Date.valueOf(startDate), Date.valueOf(endDate));
        } else {
            sql = "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, called_at)) " +
                  "FROM queue_tokens " +
                  "WHERE called_at IS NOT NULL AND created_at IS NOT NULL AND called_at >= created_at";
            return jdbcTemplate.queryForObject(sql, Double.class);
        }
    }

    /**
     * Retrieves daily queue performance trends (total tokens, waiting, served, cancelled, avg wait).
     */
    public List<java.util.Map<String, Object>> getQueueTrends(LocalDate startDate, LocalDate endDate) {
        String sql;
        if (startDate != null && endDate != null) {
            sql = "SELECT appointment_date, " +
                  "COUNT(*) as total_tokens, " +
                  "SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END) as waiting, " +
                  "SUM(CASE WHEN status IN ('CALLED', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END) as served, " +
                  "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled, " +
                  "AVG(CASE WHEN called_at IS NOT NULL AND created_at IS NOT NULL AND called_at >= created_at THEN TIMESTAMPDIFF(MINUTE, created_at, called_at) ELSE NULL END) as avg_wait " +
                  "FROM queue_tokens " +
                  "WHERE appointment_date BETWEEN ? AND ? " +
                  "GROUP BY appointment_date " +
                  "ORDER BY appointment_date ASC";
            return jdbcTemplate.queryForList(sql, Date.valueOf(startDate), Date.valueOf(endDate));
        } else {
            sql = "SELECT appointment_date, " +
                  "COUNT(*) as total_tokens, " +
                  "SUM(CASE WHEN status = 'WAITING' THEN 1 ELSE 0 END) as waiting, " +
                  "SUM(CASE WHEN status IN ('CALLED', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END) as served, " +
                  "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled, " +
                  "AVG(CASE WHEN called_at IS NOT NULL AND created_at IS NOT NULL AND called_at >= created_at THEN TIMESTAMPDIFF(MINUTE, created_at, called_at) ELSE NULL END) as avg_wait " +
                  "FROM queue_tokens " +
                  "GROUP BY appointment_date " +
                  "ORDER BY appointment_date ASC LIMIT 30";
            return jdbcTemplate.queryForList(sql);
        }
    }
}
