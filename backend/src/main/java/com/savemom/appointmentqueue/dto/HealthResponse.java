package com.savemom.appointmentqueue.dto;

/**
 * Data Transfer Object (DTO) returned by the GET /api/health endpoint.
 * No Lombok is used; fields, constructors, getters, and setters are explicitly written.
 */
public class HealthResponse {

    private String status;
    private String appName;
    private String database;
    private String timestamp;

    public HealthResponse() {
    }

    public HealthResponse(String status, String appName, String database, String timestamp) {
        this.status = status;
        this.appName = appName;
        this.database = database;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
