package com.savemom.appointmentqueue.model;

import java.time.Instant;

/**
 * Domain entity recording status changes for an appointment audit trail.
 */
public class AppointmentStatusHistory {

    private Long id;
    private Long appointmentId;
    private AppointmentStatus fromStatus;
    private AppointmentStatus toStatus;
    private Long changedByUserId;
    private String reasonOrNotes;
    private Instant createdAt;

    public AppointmentStatusHistory() {
    }

    public AppointmentStatusHistory(Long id, Long appointmentId, AppointmentStatus fromStatus,
                                    AppointmentStatus toStatus, Long changedByUserId,
                                    String reasonOrNotes, Instant createdAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.reasonOrNotes = reasonOrNotes;
        this.createdAt = createdAt;
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

    public AppointmentStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(AppointmentStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public AppointmentStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(AppointmentStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public String getReasonOrNotes() {
        return reasonOrNotes;
    }

    public void setReasonOrNotes(String reasonOrNotes) {
        this.reasonOrNotes = reasonOrNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
