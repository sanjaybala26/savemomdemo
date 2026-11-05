package com.savemom.appointmentqueue.model;

/**
 * Status of a patient in a provider's daily consultation queue.
 */
public enum QueueStatus {
    WAITING,
    CALLED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    CANCELLED
}
