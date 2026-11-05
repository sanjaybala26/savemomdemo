package com.savemom.appointmentqueue.security;

import org.springframework.stereotype.Component;

/**
 * Service for one-way password hashing and verification using BCrypt.
 * Protects user credentials so plain-text passwords are never stored in the database.
 */
@Component
public class PasswordEncoder {

    /**
     * Hashes a raw password with a newly generated random BCrypt salt (work factor 10).
     */
    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
    }

    /**
     * Verifies if a raw password matches the stored BCrypt hash.
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
