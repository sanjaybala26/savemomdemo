package com.savemom.appointmentqueue.model;

/**
 * Roles supported by the CareQueue application.
 * - CUSTOMER: Patient who books appointments and waits in queue
 * - PROVIDER: Doctor, nurse, or healthcare specialist managing consultations
 * - ADMIN: Clinic administrator managing providers and overall operations
 */
public enum Role {
    CUSTOMER,
    PROVIDER,
    ADMIN;

    /**
     * Helper to safely check if a string represents a valid Role.
     */
    public static boolean isValid(String roleStr) {
        if (roleStr == null) {
            return false;
        }
        try {
            Role.valueOf(roleStr.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
