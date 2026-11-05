-- Phase 1 Initial Database Migration
-- Purpose: Verify that Flyway is properly configured and can apply schema changes to MySQL.

CREATE TABLE IF NOT EXISTS system_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_key VARCHAR(100) NOT NULL UNIQUE,
    property_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial record to confirm migration execution
INSERT INTO system_metadata (property_key, property_value)
VALUES ('phase', '1_initial_setup')
ON DUPLICATE KEY UPDATE property_value = '1_initial_setup';
