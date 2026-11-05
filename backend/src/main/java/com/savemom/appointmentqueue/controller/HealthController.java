package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/**
 * Controller providing system health check endpoint for monitoring and frontend verification.
 * Beginner-friendly: explicitly written without magic annotations or hidden magic.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    // Explicit constructor injection instead of Lombok or field injection
    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * GET /api/health
     * Returns HTTP 200 OK with application status and database connectivity info.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> checkHealth() {
        String dbStatus = checkDatabaseConnection();

        HealthResponse response = new HealthResponse(
                "UP",
                "CareQueue - Appointment & Queue Management",
                dbStatus,
                Instant.now().toString()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Simple helper method to verify if the MySQL database can be contacted.
     */
    private String checkDatabaseConnection() {
        if (dataSource == null) {
            return "DataSource not configured";
        }

        try (Connection connection = dataSource.getConnection()) {
            // Validates connection with a 2-second timeout
            if (connection.isValid(2)) {
                return "UP (Connected)";
            } else {
                return "DOWN (Invalid connection)";
            }
        } catch (Exception ex) {
            return "DOWN (" + ex.getMessage() + ")";
        }
    }
}
