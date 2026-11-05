package com.savemom.appointmentqueue.model;

import java.time.Instant;

/**
 * Domain entity recording status transitions for a queue token audit trail.
 * Tracks transitions like WAITING -> CALLED -> IN_PROGRESS -> COMPLETED/SKIPPED/CANCELLED.
 */
public class QueueTokenHistory {

    private Long id;
    private Long tokenId;
    private QueueStatus fromStatus;
    private QueueStatus toStatus;
    private Long changedByUserId;
    private String reasonOrNotes;
    private Instant createdAt;

    public QueueTokenHistory() {
    }

    public QueueTokenHistory(Long id, Long tokenId, QueueStatus fromStatus,
                             QueueStatus toStatus, Long changedByUserId,
                             String reasonOrNotes, Instant createdAt) {
        this.id = id;
        this.tokenId = tokenId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.reasonOrNotes = reasonOrNotes;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTokenId() {
        return tokenId;
    }

    public void setTokenId(Long tokenId) {
        this.tokenId = tokenId;
    }

    public QueueStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(QueueStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public QueueStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(QueueStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public String getReasonOrNotes() {
        return reasonOrNotes;
    }

    public void setReasonOrNotes(String reasonOrNotes) {
        this.reasonOrNotes = reasonOrNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
