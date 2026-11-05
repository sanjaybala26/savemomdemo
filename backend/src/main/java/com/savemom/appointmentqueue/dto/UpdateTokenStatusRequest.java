package com.savemom.appointmentqueue.dto;

/**
 * Request payload for providers updating a patient's queue token status.
 */
public class UpdateTokenStatusRequest {

    private String status;
    private String notes;

    public UpdateTokenStatusRequest() {
    }

    public UpdateTokenStatusRequest(String status, String notes) {
        this.status = status;
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
