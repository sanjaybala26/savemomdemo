package com.savemom.appointmentqueue.dto;

/**
 * Public response DTO for queue tokens.
 * Provides complete consultation position information for patients and providers.
 */
public class QueueTokenDTO {

    private Long id;
    private Long appointmentId;
    private Long providerId;
    private String providerName;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String appointmentDate;
    private String startTime;
    private String endTime;
    private int tokenNumber;
    private String tokenDisplay; // e.g. "T-001"
    private String status;       // e.g. "WAITING", "CALLED", "IN_PROGRESS", "COMPLETED", "SKIPPED", "CANCELLED"
    private String calledAt;
    private String completedAt;
    private String notes;
    private String createdAt;
    private String updatedAt;

    public QueueTokenDTO() {
    }

    public QueueTokenDTO(Long id, Long appointmentId, Long providerId, String providerName,
                         Long customerId, String customerName, String customerEmail,
                         String appointmentDate, String startTime, String endTime,
                         int tokenNumber, String tokenDisplay, String status,
                         String calledAt, String completedAt, String notes,
                         String createdAt, String updatedAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.providerId = providerId;
        this.providerName = providerName;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.tokenNumber = tokenNumber;
        this.tokenDisplay = tokenDisplay;
        this.status = status;
        this.calledAt = calledAt;
        this.completedAt = completedAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
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

    public int getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(int tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public String getTokenDisplay() {
        return tokenDisplay;
    }

    public void setTokenDisplay(String tokenDisplay) {
        this.tokenDisplay = tokenDisplay;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(String calledAt) {
        this.calledAt = calledAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
