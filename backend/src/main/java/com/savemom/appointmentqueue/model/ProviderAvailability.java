package com.savemom.appointmentqueue.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain entity representing working hours and slot durations for a provider.
 * Times are stored in 24-hour "HH:mm" format (e.g. "09:00", "17:00") representing
 * the local operational hours of the clinic.
 */
public class ProviderAvailability {

    private Long id;
    private Long providerId;
    private String workingDays; // Comma-separated: "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
    private String openTime;    // "09:00"
    private String closeTime;   // "17:00"
    private int slotDurationMinutes; // e.g. 30
    private Instant createdAt;
    private Instant updatedAt;

    public ProviderAvailability() {
        this.workingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
        this.openTime = "09:00";
        this.closeTime = "17:00";
        this.slotDurationMinutes = 30;
    }

    public ProviderAvailability(Long id, Long providerId, String workingDays, String openTime, String closeTime, int slotDurationMinutes) {
        this.id = id;
        this.providerId = providerId;
        this.workingDays = workingDays;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public ProviderAvailability(Long id, Long providerId, String workingDays, String openTime, String closeTime, int slotDurationMinutes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerId = providerId;
        this.workingDays = workingDays;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotDurationMinutes = slotDurationMinutes;
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

    public String getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(String workingDays) {
        this.workingDays = workingDays;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public int getSlotDurationMinutes() {
        return slotDurationMinutes;
    }

    public void setSlotDurationMinutes(int slotDurationMinutes) {
        this.slotDurationMinutes = slotDurationMinutes;
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

    public List<String> getWorkingDaysAsList() {
        if (workingDays == null || workingDays.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(workingDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
