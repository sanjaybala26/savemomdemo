package com.savemom.appointmentqueue.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain entity representing an appointment booked by a patient with a healthcare provider.
 * Explicitly written without Lombok to keep code visible and beginner-friendly.
 */
public class Appointment {

    private Long id;
    private Long customerId;
    private Long providerId;
    private LocalDate appointmentDate;
    private String startTime;
    private String endTime;
    private AppointmentStatus status;
    private String notes;
    private String activeSlotLock;
    private Instant createdAt;
    private Instant updatedAt;

    public Appointment() {
        this.status = AppointmentStatus.PENDING;
    }

    public Appointment(Long id, Long customerId, Long providerId, LocalDate appointmentDate,
                       String startTime, String endTime, AppointmentStatus status, String notes,
                       String activeSlotLock, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.providerId = providerId;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status != null ? status : AppointmentStatus.PENDING;
        this.notes = notes;
        this.activeSlotLock = activeSlotLock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
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

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getActiveSlotLock() {
        return activeSlotLock;
    }

    public void setActiveSlotLock(String activeSlotLock) {
        this.activeSlotLock = activeSlotLock;
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
