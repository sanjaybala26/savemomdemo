package com.savemom.appointmentqueue.dto;

/**
 * Response DTO providing real-time queue position and estimated wait time for customers.
 * Displays people ahead and dynamic consultation wait estimations.
 */
public class CustomerQueueStatusDTO {

    private Long tokenId;
    private Long appointmentId;
    private Long customerId;
    private String customerName;
    private Long providerId;
    private String providerName;
    private String serviceDescription;
    private String appointmentDate;
    private String startTime;
    private String endTime;
    private int tokenNumber;
    private String tokenDisplay; // e.g. "T-001"
    private String status;       // WAITING, CALLED, IN_PROGRESS, COMPLETED, SKIPPED, CANCELLED
    private int peopleAhead;
    private int queuePosition;
    private int estimatedWaitMinutes;
    private int slotDurationMinutes;
    private String currentlyServingToken;
    private String calledAt;
    private String completedAt;
    private String notes;
    private String lastUpdated;

    public CustomerQueueStatusDTO() {
    }

    public CustomerQueueStatusDTO(Long tokenId, Long appointmentId, Long customerId, String customerName,
                                  Long providerId, String providerName, String serviceDescription,
                                  String appointmentDate, String startTime, String endTime,
                                  int tokenNumber, String tokenDisplay, String status,
                                  int peopleAhead, int queuePosition, int estimatedWaitMinutes, int slotDurationMinutes,
                                  String currentlyServingToken,
                                  String calledAt, String completedAt, String notes, String lastUpdated) {
        this.tokenId = tokenId;
        this.appointmentId = appointmentId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.providerId = providerId;
        this.providerName = providerName;
        this.serviceDescription = serviceDescription;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.tokenNumber = tokenNumber;
        this.tokenDisplay = tokenDisplay;
        this.status = status;
        this.peopleAhead = peopleAhead;
        this.queuePosition = queuePosition;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
        this.slotDurationMinutes = slotDurationMinutes;
        this.currentlyServingToken = currentlyServingToken;
        this.calledAt = calledAt;
        this.completedAt = completedAt;
        this.notes = notes;
        this.lastUpdated = lastUpdated;
    }

    public CustomerQueueStatusDTO(Long tokenId, Long appointmentId, Long customerId, String customerName,
                                  Long providerId, String providerName, String serviceDescription,
                                  String appointmentDate, String startTime, String endTime,
                                  int tokenNumber, String tokenDisplay, String status,
                                  int peopleAhead, int estimatedWaitMinutes, int slotDurationMinutes,
                                  String currentlyServingToken,
                                  String calledAt, String completedAt, String notes, String lastUpdated) {
        this(tokenId, appointmentId, customerId, customerName, providerId, providerName, serviceDescription,
             appointmentDate, startTime, endTime, tokenNumber, tokenDisplay, status,
             peopleAhead, (status != null && status.equals("WAITING")) ? (peopleAhead + 1) : 0,
             estimatedWaitMinutes, slotDurationMinutes, currentlyServingToken, calledAt, completedAt, notes, lastUpdated);
    }

    public Long getTokenId() {
        return tokenId;
    }

    public void setTokenId(Long tokenId) {
        this.tokenId = tokenId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
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

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
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

    public int getPeopleAhead() {
        return peopleAhead;
    }

    public void setPeopleAhead(int peopleAhead) {
        this.peopleAhead = peopleAhead;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    public int getSlotDurationMinutes() {
        return slotDurationMinutes;
    }

    public void setSlotDurationMinutes(int slotDurationMinutes) {
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public String getCurrentlyServingToken() {
        return currentlyServingToken;
    }

    public void setCurrentlyServingToken(String currentlyServingToken) {
        this.currentlyServingToken = currentlyServingToken;
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

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
