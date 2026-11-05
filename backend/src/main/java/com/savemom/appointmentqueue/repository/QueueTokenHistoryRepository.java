package com.savemom.appointmentqueue.repository;

import com.savemom.appointmentqueue.model.QueueStatus;
import com.savemom.appointmentqueue.model.QueueTokenHistory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Data access operations for QueueTokenHistory audit trail records using JdbcTemplate.
 */
@Repository
public class QueueTokenHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<QueueTokenHistory> rowMapper = (rs, rowNum) -> {
        String fromStr = rs.getString("from_status");
        String toStr = rs.getString("to_status");

        QueueStatus from = (fromStr != null) ? QueueStatus.valueOf(fromStr) : null;
        QueueStatus to = QueueStatus.valueOf(toStr);

        return new QueueTokenHistory(
                rs.getLong("id"),
                rs.getLong("token_id"),
                from,
                to,
                rs.getLong("changed_by_user_id"),
                rs.getString("reason_or_notes"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
        );
    };

    @Autowired
    public QueueTokenHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<QueueTokenHistory> findByTokenId(Long tokenId) {
        String sql = "SELECT id, token_id, from_status, to_status, changed_by_user_id, reason_or_notes, created_at " +
                "FROM queue_token_history WHERE token_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, rowMapper, tokenId);
    }

    public QueueTokenHistory save(QueueTokenHistory history) {
        Timestamp now = Timestamp.from(Instant.now());
        String sql = "INSERT INTO queue_token_history (token_id, from_status, to_status, changed_by_user_id, reason_or_notes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, history.getTokenId());
            ps.setString(2, history.getFromStatus() != null ? history.getFromStatus().name() : null);
            ps.setString(3, history.getToStatus().name());
            ps.setLong(4, history.getChangedByUserId());
            ps.setString(5, history.getReasonOrNotes());
            ps.setTimestamp(6, now);
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object idVal = keys.get("ID");
            if (idVal == null) idVal = keys.get("id");
            if (idVal == null && !keys.isEmpty()) idVal = keys.values().iterator().next();
            if (idVal instanceof Number) history.setId(((Number) idVal).longValue());
        } else if (keyHolder.getKey() != null) {
            history.setId(keyHolder.getKey().longValue());
        }
        history.setCreatedAt(now.toInstant());
        return history;
    }
}
