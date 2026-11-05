package com.savemom.appointmentqueue.dto;

import com.savemom.appointmentqueue.model.User;

/**
 * Public profile data returned to the client.
 * Strictly omits sensitive information such as password hashes.
 */
public class UserProfileResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String createdAt;

    public UserProfileResponse() {
    }

    public UserProfileResponse(Long id, String name, String email, String role, String createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static UserProfileResponse fromUser(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
