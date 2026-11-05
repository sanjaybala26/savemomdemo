package com.savemom.appointmentqueue.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain entity representing a patient's position in a healthcare provider's daily consultation queue.
 * Explicitly written without Lombok to keep code transparent and beginner-friendly.
 */
public class QueueToken {

    private Long id;
    private Long appointmentId;
    private Long providerId;
    private LocalDate appointmentDate;
    private int tokenNumber;
    private String tokenDisplay; // e.g. "T-001"
    private QueueStatus status;
    private Instant calledAt;
    private Instant completedAt;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public QueueToken() {
        this.status = QueueStatus.WAITING;
    }

    public QueueToken(Long id, Long appointmentId, Long providerId, LocalDate appointmentDate,
                      int tokenNumber, String tokenDisplay, QueueStatus status,
                      Instant calledAt, Instant completedAt, String notes,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.providerId = providerId;
        this.appointmentDate = appointmentDate;
        this.tokenNumber = tokenNumber;
        this.tokenDisplay = tokenDisplay;
        this.status = (status != null) ? status : QueueStatus.WAITING;
        this.calledAt = calledAt;
        this.completedAt = completedAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public int getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(int tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public String getTokenDisplay() {
        return tokenDisplay;
    }

    public void setTokenDisplay(String tokenDisplay) {
        this.tokenDisplay = tokenDisplay;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
