package com.savemom.appointmentqueue.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO returning the calculated time slots for a provider on a specific date.
 */
public class ProviderAvailabilityResponse {

    private Long providerId;
    private String displayName;
    private String date;
    private String dayOfWeek;
    private boolean isWorkingDay;
    private List<TimeSlotDTO> slots;
    private String message;
    private String currentTime;
    private String timezone;

    public ProviderAvailabilityResponse() {
        this.slots = new ArrayList<>();
    }

    public ProviderAvailabilityResponse(Long providerId, String displayName, String date, String dayOfWeek,
                                      boolean isWorkingDay, List<TimeSlotDTO> slots, String message) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.isWorkingDay = isWorkingDay;
        this.slots = (slots != null) ? slots : new ArrayList<>();
        this.message = message;
    }

    public ProviderAvailabilityResponse(Long providerId, String displayName, String date, String dayOfWeek,
                                      boolean isWorkingDay, List<TimeSlotDTO> slots, String message,
                                      String currentTime, String timezone) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.isWorkingDay = isWorkingDay;
        this.slots = (slots != null) ? slots : new ArrayList<>();
        this.message = message;
        this.currentTime = currentTime;
        this.timezone = timezone;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public boolean isWorkingDay() {
        return isWorkingDay;
    }

    public boolean getIsWorkingDay() {
        return isWorkingDay;
    }

    public void setWorkingDay(boolean workingDay) {
        isWorkingDay = workingDay;
    }

    public List<TimeSlotDTO> getSlots() {
        return slots;
    }

    public void setSlots(List<TimeSlotDTO> slots) {
        this.slots = slots;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(String currentTime) {
        this.currentTime = currentTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
