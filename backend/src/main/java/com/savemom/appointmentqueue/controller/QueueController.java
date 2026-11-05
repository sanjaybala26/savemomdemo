package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.CustomerQueueStatusDTO;
import com.savemom.appointmentqueue.dto.ErrorResponse;
import com.savemom.appointmentqueue.dto.QueueTokenDTO;
import com.savemom.appointmentqueue.dto.UpdateTokenStatusRequest;
import com.savemom.appointmentqueue.model.QueueTokenHistory;
import com.savemom.appointmentqueue.model.Role;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Controller managing clinic queue flow for healthcare providers.
 * Provides endpoints for viewing daily queue, calling the next waiting patient,
 * and transitioning token statuses.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    @Autowired
    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * GET /api/queue/my-token?appointmentId=...
     * Allows an authenticated customer to track their active queue position,
     * including people ahead and estimated waiting time.
     */
    @GetMapping("/my-token")
    public ResponseEntity<?> getMyActiveToken(
            HttpServletRequest request,
            @RequestParam(value = "appointmentId", required = false) Long appointmentId) {

        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required to track queue position"));
        }

        try {
            Optional<CustomerQueueStatusDTO> tokenOpt = queueService.getActiveCustomerToken(currentUser, appointmentId);
            if (tokenOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(404, "Not Found", "No queue token found for current account"));
            }
            return ResponseEntity.ok(tokenOpt.get());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", "Failed to retrieve queue position: " + e.getMessage()));
        }
    }

    /**
     * GET /api/queue/my-tokens
     * Returns all queue tokens belonging to the authenticated customer.
     */
    @GetMapping("/my-tokens")
    public ResponseEntity<?> getMyTokens(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        List<CustomerQueueStatusDTO> tokens = queueService.getCustomerQueueTokens(currentUser);
        return ResponseEntity.ok(tokens);
    }

    /**
     * GET /api/queue/provider?date=YYYY-MM-DD
     * Retrieves the provider's patient queue for the specified date (defaults to today).
     */
    @GetMapping("/provider")
    public ResponseEntity<?> getProviderQueue(
            HttpServletRequest request,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", "Only healthcare providers can view clinic queues"));
        }

        try {
            List<QueueTokenDTO> queue = queueService.getProviderQueue(currentUser, date);
            return ResponseEntity.ok(queue);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", "Failed to retrieve clinic queue: " + e.getMessage()));
        }
    }

    /**
     * POST /api/queue/call-next?date=YYYY-MM-DD
     * Calls the next patient in WAITING status, transitioning them to CALLED.
     */
    @PostMapping("/call-next")
    public ResponseEntity<?> callNextPatient(
            HttpServletRequest request,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", "Only healthcare providers can call patients"));
        }

        try {
            QueueTokenDTO calledToken = queueService.callNextPatient(currentUser, date);
            return ResponseEntity.ok(calledToken);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", "Failed to call next patient: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/queue/tokens/{id}/status
     * Transitions a patient's token to WAITING, CALLED, IN_PROGRESS, COMPLETED, SKIPPED, or CANCELLED.
     */
    @PutMapping("/tokens/{id}/status")
    public ResponseEntity<?> updateTokenStatus(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody UpdateTokenStatusRequest updateRequest) {

        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", "Only healthcare providers can manage queue statuses"));
        }

        try {
            QueueTokenDTO updated = queueService.updateTokenStatus(currentUser, id, updateRequest);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", "Failed to update token: " + e.getMessage()));
        }
    }

    /**
     * GET /api/queue/tokens/{id}/history
     * Returns the status transition audit history for a queue token.
     */
    @GetMapping("/tokens/{id}/history")
    public ResponseEntity<?> getTokenHistory(HttpServletRequest request, @PathVariable("id") Long id) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            List<QueueTokenHistory> history = queueService.getTokenHistory(currentUser, id);
            return ResponseEntity.ok(history);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", "Failed to retrieve queue history: " + e.getMessage()));
        }
    }
}
