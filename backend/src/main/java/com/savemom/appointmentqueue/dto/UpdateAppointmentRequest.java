package com.savemom.appointmentqueue.dto;

/**
 * Request payload sent to update permitted appointment details (notes and/or rescheduled slot).
 */
public class UpdateAppointmentRequest {

    private Long providerId;
    private String appointmentDate; // YYYY-MM-DD
    private String startTime;       // HH:mm
    private String endTime;         // HH:mm
    private String notes;           // Reason for visit / symptoms / notes

    public UpdateAppointmentRequest() {
    }

    public UpdateAppointmentRequest(Long providerId, String appointmentDate, String startTime, String endTime, String notes) {
        this.providerId = providerId;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
