package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.ErrorResponse;
import com.savemom.appointmentqueue.dto.ProviderProfileDTO;
import com.savemom.appointmentqueue.dto.ProviderUpdateRequest;
import com.savemom.appointmentqueue.model.Role;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.service.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for provider management actions.
 * Protected by JWT authentication and restricted to PROVIDER and ADMIN roles.
 * Regular CUSTOMER users receive 403 Forbidden.
 */
@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private final ProviderService providerService;

    @Autowired
    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * GET /api/provider/me
     * Returns the authenticated provider's profile and current working schedule.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        // Enforce role restriction: Customers cannot access provider management endpoints
        if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", "Access denied. Only healthcare providers and administrators can manage provider settings."));
        }

        try {
            ProviderProfileDTO profile = providerService.getOrCreateProfileForUser(currentUser);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/provider/me
     * Updates the authenticated provider's profile and working schedule.
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(HttpServletRequest request,
                                            @RequestBody ProviderUpdateRequest updateRequest) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        // Enforce role restriction
        if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", "Access denied. Only healthcare providers and administrators can manage provider settings."));
        }

        try {
            ProviderProfileDTO updated = providerService.updateProfile(currentUser, updateRequest);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }
}
