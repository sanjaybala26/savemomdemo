-- ==============================================================================
-- Flyway Database Migration V5: Queue Tokens and Queue Management Tables
-- Phase 5: Token Generation, Sequence Row Lock, and Provider Queue Controls
-- ==============================================================================

-- 1. Daily Token Sequence Table
-- Provides atomic, database-backed sequence row locks per provider and date.
-- Prevents duplicate tokens during concurrent bookings.
CREATE TABLE IF NOT EXISTS queue_daily_sequences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    queue_date DATE NOT NULL,
    last_token_number INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seq_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_date_seq UNIQUE (provider_id, queue_date)
);

-- 2. Queue Tokens Table
-- Holds the patient's queue token for an appointment.
-- Strict uniqueness enforced across (provider_id, appointment_date, token_number).
CREATE TABLE IF NOT EXISTS queue_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE,
    provider_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    token_number INT NOT NULL,
    token_display VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    called_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_token_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_date_token UNIQUE (provider_id, appointment_date, token_number)
);

-- 3. Queue Token Status History Table
-- Audit trail tracking status transitions: WAITING -> CALLED -> IN_PROGRESS -> COMPLETED/SKIPPED/CANCELLED.
CREATE TABLE IF NOT EXISTS queue_token_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    reason_or_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_token FOREIGN KEY (token_id) REFERENCES queue_tokens(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_token_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);
