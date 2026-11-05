package com.savemom.appointmentqueue.dto;

/**
 * Request payload when cancelling an existing appointment.
 */
public class CancelAppointmentRequest {

    private String reason;

    public CancelAppointmentRequest() {
    }

    public CancelAppointmentRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
