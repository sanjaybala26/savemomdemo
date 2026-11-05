package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.ErrorResponse;
import com.savemom.appointmentqueue.dto.ProviderAvailabilityResponse;
import com.savemom.appointmentqueue.dto.ProviderProfileDTO;
import com.savemom.appointmentqueue.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Public controller for browsing active healthcare providers and checking their available appointment slots.
 * Accessible without authentication so patients can browse services.
 */
@RestController
@RequestMapping("/api/providers")
public class PublicProviderController {

    private final ProviderService providerService;

    @Autowired
    public PublicProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * GET /api/providers
     * Lists all active healthcare providers with their working schedules.
     */
    @GetMapping
    public ResponseEntity<List<ProviderProfileDTO>> getActiveProviders() {
        List<ProviderProfileDTO> providers = providerService.getActiveProviders();
        return ResponseEntity.ok(providers);
    }

    /**
     * GET /api/providers/{id}
     * Returns details for a specific provider.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProviderById(@PathVariable Long id) {
        return providerService.getProviderById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(404, "Not Found", "Provider not found with ID: " + id)));
    }

    /**
     * GET /api/providers/{id}/slots?date=YYYY-MM-DD
     * Returns the calculated available time slots for a given calendar date.
     */
    @GetMapping("/{id}/slots")
    public ResponseEntity<?> getAvailableSlots(@PathVariable Long id,
                                               @RequestParam(name = "date") String date) {
        try {
            ProviderAvailabilityResponse response = providerService.getSlotsForDate(id, date);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }
}
