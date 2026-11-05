package com.savemom.appointmentqueue.dto;

/**
 * Data transfer object representing a bookable appointment time window.
 */
public class TimeSlotDTO {

    private String startTime;
    private String endTime;
    private String formatted;
    private boolean isBooked;
    private boolean isPast;

    public TimeSlotDTO() {
        this.isBooked = false;
        this.isPast = false;
    }

    public TimeSlotDTO(String startTime, String endTime, String formatted) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.formatted = formatted;
        this.isBooked = false;
        this.isPast = false;
    }

    public TimeSlotDTO(String startTime, String endTime, String formatted, boolean isBooked) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.formatted = formatted;
        this.isBooked = isBooked;
        this.isPast = false;
    }

    public TimeSlotDTO(String startTime, String endTime, String formatted, boolean isBooked, boolean isPast) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.formatted = formatted;
        this.isBooked = isBooked;
        this.isPast = isPast;
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

    public String getFormatted() {
        return formatted;
    }

    public void setFormatted(String formatted) {
        this.formatted = formatted;
    }

    public boolean isBooked() {
        return isBooked;
    }

    public boolean getIsBooked() {
        return isBooked;
    }

    public void setBooked(boolean booked) {
        isBooked = booked;
    }

    public boolean isPast() {
        return isPast;
    }

    public boolean getIsPast() {
        return isPast;
    }

    public void setPast(boolean past) {
        isPast = past;
    }
}
