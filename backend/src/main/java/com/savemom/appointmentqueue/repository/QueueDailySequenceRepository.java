package com.savemom.appointmentqueue.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Data access for daily queue sequence counters.
 * Enforces atomic sequence increments using database row locks (SELECT ... FOR UPDATE).
 */
@Repository
public class QueueDailySequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public QueueDailySequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically increments and returns the next daily token number for a given provider and appointment date.
     * Pessimistic row locking ensures no two concurrent bookings receive the same token number.
     */
    public int getNextTokenNumberWithLock(Long providerId, LocalDate date) {
        Date sqlDate = Date.valueOf(date);
        String selectForUpdateSql = "SELECT id, last_token_number FROM queue_daily_sequences WHERE provider_id = ? AND queue_date = ? FOR UPDATE";

        List<SequenceRecord> records = jdbcTemplate.query(
                selectForUpdateSql,
                (rs, rowNum) -> new SequenceRecord(rs.getLong("id"), rs.getInt("last_token_number")),
                providerId, sqlDate
        );

        if (!records.isEmpty()) {
            SequenceRecord record = records.get(0);
            int nextToken = record.lastTokenNumber + 1;
            jdbcTemplate.update(
                    "UPDATE queue_daily_sequences SET last_token_number = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    nextToken, record.id
            );
            return nextToken;
        }

        // If no sequence row exists yet for today, attempt to insert starting at 1
        try {
            jdbcTemplate.update(
                    "INSERT INTO queue_daily_sequences (provider_id, queue_date, last_token_number, updated_at) VALUES (?, ?, 1, CURRENT_TIMESTAMP)",
                    providerId, sqlDate
            );
            return 1;
        } catch (DuplicateKeyException e) {
            // A concurrent transaction inserted the initial row first; retry with SELECT ... FOR UPDATE
            List<SequenceRecord> retryRecords = jdbcTemplate.query(
                    selectForUpdateSql,
                    (rs, rowNum) -> new SequenceRecord(rs.getLong("id"), rs.getInt("last_token_number")),
                    providerId, sqlDate
            );
            if (!retryRecords.isEmpty()) {
                SequenceRecord record = retryRecords.get(0);
                int nextToken = record.lastTokenNumber + 1;
                jdbcTemplate.update(
                        "UPDATE queue_daily_sequences SET last_token_number = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        nextToken, record.id
                );
                return nextToken;
            }
            throw e;
        }
    }

    private static class SequenceRecord {
        final Long id;
        final int lastTokenNumber;

        SequenceRecord(Long id, int lastTokenNumber) {
            this.id = id;
            this.lastTokenNumber = lastTokenNumber;
        }
    }
}
