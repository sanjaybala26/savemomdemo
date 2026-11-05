package com.savemom.appointmentqueue.model;

import java.time.Instant;

/**
 * Domain entity representing a healthcare service provider's public profile.
 * Linked 1-to-1 with a User possessing the PROVIDER role.
 * Explicitly written without Lombok to keep code visible and beginner-friendly.
 */
public class ProviderProfile {

    private Long id;
    private Long userId;
    private String displayName;
    private String serviceDescription;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public ProviderProfile() {
        this.isActive = true;
    }

    public ProviderProfile(Long id, Long userId, String displayName, String serviceDescription, boolean isActive) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.serviceDescription = serviceDescription;
        this.isActive = isActive;
    }

    public ProviderProfile(Long id, Long userId, String displayName, String serviceDescription, boolean isActive, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.serviceDescription = serviceDescription;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
}
