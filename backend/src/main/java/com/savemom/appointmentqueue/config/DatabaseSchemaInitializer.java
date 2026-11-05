package com.savemom.appointmentqueue.config;

import com.savemom.appointmentqueue.security.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fallback database initializer that guarantees all required CareQueue tables
 * and starter demo accounts exist even if Flyway migrations are skipped or fail.
 */
@Component
@Order(1)
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Checking database schema tables...");
            initTables();
            initDemoData();
            log.info("Database schema check and initialization completed successfully.");
        } catch (Exception e) {
            log.error("Database initialization warning: {}", e.getMessage(), e);
        }
    }

    private void initTables() {
        // 1. system_metadata
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS system_metadata (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                property_key VARCHAR(100) NOT NULL UNIQUE,
                property_value VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 2. users
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(150) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 3. provider_profiles
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS provider_profiles (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL UNIQUE,
                display_name VARCHAR(150) NOT NULL,
                service_description TEXT,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """);

        // 4. provider_availability
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS provider_availability (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                provider_id BIGINT NOT NULL UNIQUE,
                working_days VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
                open_time VARCHAR(10) NOT NULL DEFAULT '09:00',
                close_time VARCHAR(10) NOT NULL DEFAULT '17:00',
                slot_duration_minutes INT NOT NULL DEFAULT 30,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT fk_availability_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE
            )
        """);

        // 5. appointments
        jdbcTemplate.execute("""
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
            )
        """);

        // 6. appointment_status_history
        jdbcTemplate.execute("""
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
            )
        """);

        // 7. queue_daily_sequences
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS queue_daily_sequences (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                provider_id BIGINT NOT NULL,
                queue_date DATE NOT NULL,
                last_token_number INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT fk_seq_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE,
                CONSTRAINT uq_provider_date_seq UNIQUE (provider_id, queue_date)
            )
        """);

        // 8. queue_tokens
        jdbcTemplate.execute("""
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
            )
        """);

        // 9. queue_token_history
        jdbcTemplate.execute("""
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
            )
        """);
    }

    private void initDemoData() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (count != null && count == 0) {
                log.info("Database is empty. Seeding initial admin and provider accounts...");

                // Seed admin: admin@carequeue.com / admin123
                String adminHash = passwordEncoder.encode("admin123");
                jdbcTemplate.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'ADMIN')",
                        "Clinic Administrator", "admin@carequeue.com", adminHash);

                // Seed doctor 1: doctor.sarah@carequeue.com / doctor123
                String doctorHash = passwordEncoder.encode("doctor123");
                jdbcTemplate.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'PROVIDER')",
                        "Dr. Sarah Jenkins", "doctor.sarah@carequeue.com", doctorHash);
                Long doc1UserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'doctor.sarah@carequeue.com'", Long.class);

                jdbcTemplate.update("INSERT INTO provider_profiles (user_id, display_name, service_description, is_active) VALUES (?, ?, ?, true)",
                        doc1UserId, "Dr. Sarah Jenkins", "Senior General Physician & Family Medicine Specialist");
                Long doc1ProfId = jdbcTemplate.queryForObject("SELECT id FROM provider_profiles WHERE user_id = ?", Long.class, doc1UserId);

                jdbcTemplate.update("INSERT INTO provider_availability (provider_id, working_days, open_time, close_time, slot_duration_minutes) VALUES (?, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', '09:00', '17:00', 30)",
                        doc1ProfId);

                // Seed doctor 2: doctor.james@carequeue.com / doctor123
                jdbcTemplate.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'PROVIDER')",
                        "Dr. James Wilson", "doctor.james@carequeue.com", doctorHash);
                Long doc2UserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'doctor.james@carequeue.com'", Long.class);

                jdbcTemplate.update("INSERT INTO provider_profiles (user_id, display_name, service_description, is_active) VALUES (?, ?, ?, true)",
                        doc2UserId, "Dr. James Wilson", "Consultant Pediatrician & Child Health Specialist");
                Long doc2ProfId = jdbcTemplate.queryForObject("SELECT id FROM provider_profiles WHERE user_id = ?", Long.class, doc2UserId);

                jdbcTemplate.update("INSERT INTO provider_availability (provider_id, working_days, open_time, close_time, slot_duration_minutes) VALUES (?, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', '10:00', '18:00', 30)",
                        doc2ProfId);

                log.info("Initial demo accounts and providers successfully seeded.");
            }
        } catch (Exception ex) {
            log.warn("Demo data seeding skipped or encountered warning: {}", ex.getMessage());
        }
    }
}
