-- Phase 2: Create users table for authentication and role-based access control
-- Roles supported: CUSTOMER, PROVIDER, ADMIN

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index on email to optimize authentication queries
CREATE INDEX idx_users_email ON users(email);
