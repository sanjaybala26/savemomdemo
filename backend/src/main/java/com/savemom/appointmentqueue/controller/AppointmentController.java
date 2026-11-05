package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.AppointmentDTO;
import com.savemom.appointmentqueue.dto.AppointmentHistoryDTO;
import com.savemom.appointmentqueue.dto.BookAppointmentRequest;
import com.savemom.appointmentqueue.dto.CancelAppointmentRequest;
import com.savemom.appointmentqueue.dto.ErrorResponse;
import com.savemom.appointmentqueue.dto.UpdateAppointmentRequest;
import com.savemom.appointmentqueue.dto.UpdateAppointmentStatusRequest;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.service.AppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controller managing customer appointment booking, viewing, and cancellation.
 * Protected by JWT authentication so only authenticated patients/users can manage appointments.
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Autowired
    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * POST /api/appointments
     * Books a new appointment with status PENDING.
     */
    @PostMapping
    public ResponseEntity<?> bookAppointment(HttpServletRequest request,
                                            @RequestBody BookAppointmentRequest bookRequest) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required to book appointments"));
        }

        try {
            AppointmentDTO appointment = appointmentService.bookAppointment(currentUser, bookRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (IllegalStateException e) {
            // Conflict / Double booking
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, "Conflict", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * GET /api/appointments/my
     * Returns all appointments booked by the currently logged-in user.
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyAppointments(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        List<AppointmentDTO> myAppointments = appointmentService.getAppointmentsForCustomer(currentUser);
        return ResponseEntity.ok(myAppointments);
    }

    /**
     * GET /api/appointments/{id}
     * Returns a single appointment by ID if authorized.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAppointmentById(HttpServletRequest request, @PathVariable Long id) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            AppointmentDTO appointment = appointmentService.getAppointmentById(currentUser, id);
            return ResponseEntity.ok(appointment);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/appointments/{id}
     * Updates permitted appointment details (notes and/or rescheduled slot).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointmentDetails(HttpServletRequest request,
                                                      @PathVariable Long id,
                                                      @RequestBody UpdateAppointmentRequest updateRequest) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            AppointmentDTO updated = appointmentService.updateAppointmentDetails(currentUser, id, updateRequest);
            return ResponseEntity.ok(updated);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, "Conflict", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/appointments/{id}/cancel
     * Cancels an eligible appointment and releases the slot for re-booking.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(HttpServletRequest request,
                                              @PathVariable Long id,
                                              @RequestBody(required = false) CancelAppointmentRequest cancelRequest) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            AppointmentDTO cancelled = appointmentService.cancelAppointment(currentUser, id, cancelRequest);
            return ResponseEntity.ok(cancelled);
        } catch (SecurityException e) {
            // Ownership rejection: Cannot cancel another customer's appointment
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/appointments/{id}/confirm
     * Confirms a pending appointment (accessible by assigned provider or admin).
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmAppointment(HttpServletRequest request, @PathVariable Long id) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            AppointmentDTO confirmed = appointmentService.confirmAppointment(currentUser, id);
            return ResponseEntity.ok(confirmed);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/appointments/{id}/complete
     * Marks an appointment as COMPLETED (accessible by assigned provider or admin).
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeAppointment(HttpServletRequest request, @PathVariable Long id) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            AppointmentDTO completed = appointmentService.completeAppointment(currentUser, id);
            return ResponseEntity.ok(completed);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * PUT /api/appointments/{id}/status
     * Updates an appointment's status (PENDING, CONFIRMED, COMPLETED, CANCELLED).
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(HttpServletRequest request,
                                                    @PathVariable Long id,
                                                    @RequestBody UpdateAppointmentStatusRequest updateStatusRequest) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            String status = updateStatusRequest != null ? updateStatusRequest.getStatus() : null;
            String reason = updateStatusRequest != null ? updateStatusRequest.getReason() : null;
            AppointmentDTO updated = appointmentService.updateAppointmentStatus(currentUser, id, status, reason);
            return ResponseEntity.ok(updated);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }

    /**
     * GET /api/appointments/{id}/history
     * Returns the status change history for an appointment.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getAppointmentHistory(HttpServletRequest request, @PathVariable Long id) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        try {
            List<AppointmentHistoryDTO> history = appointmentService.getAppointmentHistory(currentUser, id);
            return ResponseEntity.ok(history);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal Server Error", e.getMessage()));
        }
    }
}
