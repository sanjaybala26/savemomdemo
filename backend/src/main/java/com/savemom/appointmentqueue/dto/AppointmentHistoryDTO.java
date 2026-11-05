package com.savemom.appointmentqueue.dto;

/**
 * DTO representing an entry in the appointment status audit history.
 */
public class AppointmentHistoryDTO {

    private Long id;
    private Long appointmentId;
    private String fromStatus;
    private String toStatus;
    private Long changedByUserId;
    private String changedByUserName;
    private String changedByUserRole;
    private String reasonOrNotes;
    private String createdAt;

    public AppointmentHistoryDTO() {
    }

    public AppointmentHistoryDTO(Long id, Long appointmentId, String fromStatus, String toStatus,
                                 Long changedByUserId, String reasonOrNotes, String createdAt) {
        this(id, appointmentId, fromStatus, toStatus, changedByUserId, null, null, reasonOrNotes, createdAt);
    }

    public AppointmentHistoryDTO(Long id, Long appointmentId, String fromStatus, String toStatus,
                                 Long changedByUserId, String changedByUserName, String changedByUserRole,
                                 String reasonOrNotes, String createdAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedByUserName = changedByUserName;
        this.changedByUserRole = changedByUserRole;
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

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public String getChangedByUserName() {
        return changedByUserName;
    }

    public void setChangedByUserName(String changedByUserName) {
        this.changedByUserName = changedByUserName;
    }

    public String getChangedByUserRole() {
        return changedByUserRole;
    }

    public void setChangedByUserRole(String changedByUserRole) {
        this.changedByUserRole = changedByUserRole;
    }

    public String getReasonOrNotes() {
        return reasonOrNotes;
    }

    public void setReasonOrNotes(String reasonOrNotes) {
        this.reasonOrNotes = reasonOrNotes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
