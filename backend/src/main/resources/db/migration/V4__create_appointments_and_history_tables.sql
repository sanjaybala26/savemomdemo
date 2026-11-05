-- ==============================================================================
-- Flyway Database Migration V4: Appointments and Status History Tables
-- Phase 4: Customer Booking, Status Management, and Double-Booking Prevention
-- ==============================================================================

-- 1. Appointments Table
-- Holds bookable appointments between patients (customer_id) and doctors (provider_id).
-- active_slot_lock: holds "providerId_date_startTime" while status != CANCELLED.
-- Setting active_slot_lock to NULL on cancellation allows the slot to be booked again,
-- while the UNIQUE constraint strictly blocks simultaneous double-booking.
CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time VARCHAR(10) NOT NULL,
    end_time VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    active_slot_lock VARCHAR(80) NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE
);

-- 2. Appointment Status History Table
-- Audit trail recording each status transition (e.g. null -> PENDING, PENDING -> CANCELLED).
CREATE TABLE IF NOT EXISTS appointment_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    reason_or_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);
