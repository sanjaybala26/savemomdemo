package com.savemom.appointmentqueue.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain entity maintaining the atomic token sequence counter for a specific provider and date.
 */
public class QueueDailySequence {

    private Long id;
    private Long providerId;
    private LocalDate queueDate;
    private int lastTokenNumber;
    private Instant createdAt;
    private Instant updatedAt;

    public QueueDailySequence() {
        this.lastTokenNumber = 0;
    }

    public QueueDailySequence(Long id, Long providerId, LocalDate queueDate, int lastTokenNumber, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerId = providerId;
        this.queueDate = queueDate;
        this.lastTokenNumber = lastTokenNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
        this.queueDate = queueDate;
    }

    public int getLastTokenNumber() {
        return lastTokenNumber;
    }

    public void setLastTokenNumber(int lastTokenNumber) {
        this.lastTokenNumber = lastTokenNumber;
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
