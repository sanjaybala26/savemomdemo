package com.savemom.appointmentqueue.dto;

/**
 * Public response DTO summarizing clinic-wide operational metrics for administrators.
 */
public class AdminStatsDTO {

    private long totalUsers;
    private long totalProviders;
    private long totalAppointments;
    private long totalTokens;
    private long waitingTokens;
    private long inProgressTokens;
    private long pendingAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private Double averageWaitMinutes;
    private String averageWaitDisplay;
    private String averageWaitExplanation;

    public AdminStatsDTO() {
    }

    public AdminStatsDTO(long totalUsers, long totalProviders, long totalAppointments,
                         long totalTokens, long waitingTokens, long inProgressTokens,
                         long pendingAppointments, long completedAppointments,
                         long cancelledAppointments, Double averageWaitMinutes,
                         String averageWaitDisplay, String averageWaitExplanation) {
        this.totalUsers = totalUsers;
        this.totalProviders = totalProviders;
        this.totalAppointments = totalAppointments;
        this.totalTokens = totalTokens;
        this.waitingTokens = waitingTokens;
        this.inProgressTokens = inProgressTokens;
        this.pendingAppointments = pendingAppointments;
        this.completedAppointments = completedAppointments;
        this.cancelledAppointments = cancelledAppointments;
        this.averageWaitMinutes = averageWaitMinutes;
        this.averageWaitDisplay = averageWaitDisplay;
        this.averageWaitExplanation = averageWaitExplanation;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalProviders() {
        return totalProviders;
    }

    public void setTotalProviders(long totalProviders) {
        this.totalProviders = totalProviders;
    }

    public long getTotalAppointments() {
        return totalAppointments;
    }

    public void setTotalAppointments(long totalAppointments) {
        this.totalAppointments = totalAppointments;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getWaitingTokens() {
        return waitingTokens;
    }

    public void setWaitingTokens(long waitingTokens) {
        this.waitingTokens = waitingTokens;
    }

    public long getInProgressTokens() {
        return inProgressTokens;
    }

    public void setInProgressTokens(long inProgressTokens) {
        this.inProgressTokens = inProgressTokens;
    }

    public long getPendingAppointments() {
        return pendingAppointments;
    }

    public void setPendingAppointments(long pendingAppointments) {
        this.pendingAppointments = pendingAppointments;
    }

    public long getCompletedAppointments() {
        return completedAppointments;
    }

    public void setCompletedAppointments(long completedAppointments) {
        this.completedAppointments = completedAppointments;
    }

    public long getCancelledAppointments() {
        return cancelledAppointments;
    }

    public void setCancelledAppointments(long cancelledAppointments) {
        this.cancelledAppointments = cancelledAppointments;
    }

    public Double getAverageWaitMinutes() {
        return averageWaitMinutes;
    }

    public void setAverageWaitMinutes(Double averageWaitMinutes) {
        this.averageWaitMinutes = averageWaitMinutes;
    }

    public String getAverageWaitDisplay() {
        return averageWaitDisplay;
    }

    public void setAverageWaitDisplay(String averageWaitDisplay) {
        this.averageWaitDisplay = averageWaitDisplay;
    }

    public String getAverageWaitExplanation() {
        return averageWaitExplanation;
    }

    public void setAverageWaitExplanation(String averageWaitExplanation) {
        this.averageWaitExplanation = averageWaitExplanation;
    }
}
