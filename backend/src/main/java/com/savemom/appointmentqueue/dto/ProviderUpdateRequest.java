package com.savemom.appointmentqueue.dto;

import java.util.List;

/**
 * Request payload for a provider updating their profile information and working hours.
 */
public class ProviderUpdateRequest {

    private String displayName;
    private String serviceDescription;
    private Boolean active;
    private List<String> workingDays;
    private String openTime;
    private String closeTime;
    private Integer slotDurationMinutes;

    public ProviderUpdateRequest() {
    }

    public ProviderUpdateRequest(String displayName, String serviceDescription, Boolean active,
                                 List<String> workingDays, String openTime, String closeTime, Integer slotDurationMinutes) {
        this.displayName = displayName;
        this.serviceDescription = serviceDescription;
        this.active = active;
        this.workingDays = workingDays;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotDurationMinutes = slotDurationMinutes;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

    public Integer getSlotDurationMinutes() {
        return slotDurationMinutes;
    }

    public void setSlotDurationMinutes(Integer slotDurationMinutes) {
        this.slotDurationMinutes = slotDurationMinutes;
    }
}
