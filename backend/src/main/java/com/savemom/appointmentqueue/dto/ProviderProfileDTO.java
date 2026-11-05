package com.savemom.appointmentqueue.dto;

import java.util.List;

/**
 * Public and provider-facing DTO representing a healthcare provider's profile and schedule.
 */
public class ProviderProfileDTO {

    private Long id;
    private Long userId;
    private String displayName;
    private String serviceDescription;
    private boolean isActive;
    private List<String> workingDays;
    private String openTime;
    private String closeTime;
    private int slotDurationMinutes;

    public ProviderProfileDTO() {
    }

    public ProviderProfileDTO(Long id, Long userId, String displayName, String serviceDescription, boolean isActive,
                              List<String> workingDays, String openTime, String closeTime, int slotDurationMinutes) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.serviceDescription = serviceDescription;
        this.isActive = isActive;
        this.workingDays = workingDays;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<String> getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(List<String> workingDays) {
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
}
