package com.savemom.appointmentqueue.dto;

/**
 * Request payload for updating an appointment's status (PENDING, CONFIRMED, COMPLETED, CANCELLED).
 */
public class UpdateAppointmentStatusRequest {

    private String status;
    private String reason;

    public UpdateAppointmentStatusRequest() {
    }

    public UpdateAppointmentStatusRequest(String status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
