package com.savemom.appointmentqueue.dto;

import java.util.List;
import java.util.Map;

/**
 * Public response DTO containing clinic analytics, trends, and booking statistics.
 */
public class AdminAnalyticsDTO {

    private List<Map<String, Object>> dailyBookings;
    private List<Map<String, Object>> monthlyBookings;
    private List<Map<String, Object>> queueTrends;
    private Double averageWaitMinutes;
    private String averageWaitDisplay;
    private String averageWaitExplanation;
    private String startDate;
    private String endDate;

    public AdminAnalyticsDTO() {
    }

    public AdminAnalyticsDTO(List<Map<String, Object>> dailyBookings,
                             List<Map<String, Object>> monthlyBookings,
                             List<Map<String, Object>> queueTrends,
                             Double averageWaitMinutes,
                             String averageWaitDisplay,
                             String averageWaitExplanation,
                             String startDate,
                             String endDate) {
        this.dailyBookings = dailyBookings;
        this.monthlyBookings = monthlyBookings;
        this.queueTrends = queueTrends;
        this.averageWaitMinutes = averageWaitMinutes;
        this.averageWaitDisplay = averageWaitDisplay;
        this.averageWaitExplanation = averageWaitExplanation;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public List<Map<String, Object>> getDailyBookings() {
        return dailyBookings;
    }

    public void setDailyBookings(List<Map<String, Object>> dailyBookings) {
        this.dailyBookings = dailyBookings;
    }

    public List<Map<String, Object>> getMonthlyBookings() {
        return monthlyBookings;
    }

    public void setMonthlyBookings(List<Map<String, Object>> monthlyBookings) {
        this.monthlyBookings = monthlyBookings;
    }

    public List<Map<String, Object>> getQueueTrends() {
        return queueTrends;
    }

    public void setQueueTrends(List<Map<String, Object>> queueTrends) {
        this.queueTrends = queueTrends;
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

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
