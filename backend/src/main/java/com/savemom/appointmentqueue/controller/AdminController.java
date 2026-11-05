package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.*;
import com.savemom.appointmentqueue.model.*;
import com.savemom.appointmentqueue.repository.AppointmentRepository;
import com.savemom.appointmentqueue.repository.ProviderProfileRepository;
import com.savemom.appointmentqueue.repository.QueueTokenRepository;
import com.savemom.appointmentqueue.repository.UserRepository;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.service.AppointmentService;
import com.savemom.appointmentqueue.service.ProviderService;
import com.savemom.appointmentqueue.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller providing system-wide overview and operational metrics for clinic administrators.
 * Strictly protected for users with the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final ProviderService providerService;
    private final AppointmentService appointmentService;
    private final QueueService queueService;

    @Autowired
    public AdminController(UserRepository userRepository,
                           ProviderProfileRepository providerProfileRepository,
                           AppointmentRepository appointmentRepository,
                           QueueTokenRepository queueTokenRepository,
                           ProviderService providerService,
                           AppointmentService appointmentService,
                           QueueService queueService) {
        this.userRepository = userRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.providerService = providerService;
        this.appointmentService = appointmentService;
        this.queueService = queueService;
    }

    private User verifyAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);
        if (currentUser == null) {
            throw new IllegalStateException("Authentication required");
        }
        if (currentUser.getRole() != Role.ADMIN) {
            throw new SecurityException("Access denied: Administrator privileges required");
        }
        return currentUser;
    }

    /**
     * GET /api/admin/stats
     * Returns high-level operational metrics across users, doctors, appointments, and queue tokens.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats(HttpServletRequest request) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        long waiting = queueTokenRepository.countByStatus(QueueStatus.WAITING);
        long inProgress = queueTokenRepository.countByStatusIn("CALLED", "IN_PROGRESS");
        Double avgWait = queueTokenRepository.getAverageWaitMinutes(null, null);

        String avgWaitDisplay;
        if (avgWait != null) {
            double rounded = Math.round(avgWait * 10.0) / 10.0;
            avgWaitDisplay = rounded + " mins";
        } else {
            avgWaitDisplay = "Not available (no tokens called yet)";
        }

        AdminStatsDTO stats = new AdminStatsDTO(
                userRepository.count(),
                providerProfileRepository.count(),
                appointmentRepository.count(),
                queueTokenRepository.count(),
                waiting,
                inProgress,
                appointmentRepository.countByStatus(AppointmentStatus.PENDING),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                avgWait != null ? Math.round(avgWait * 10.0) / 10.0 : null,
                avgWaitDisplay,
                "Calculated as AVG(called_at - created_at) from stored timestamps of called patient tokens."
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/admin/users
     * Returns a list of all registered accounts (omits sensitive password hashes).
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(HttpServletRequest request) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        List<UserProfileResponse> users = userRepository.findAll().stream()
                .map(UserProfileResponse::fromUser)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/admin/providers
     * Returns all registered healthcare provider profiles and their availability.
     */
    @GetMapping("/providers")
    public ResponseEntity<?> getAllProviders(HttpServletRequest request) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        List<ProviderProfileDTO> providers = providerService.getAllProviders();
        return ResponseEntity.ok(providers);
    }

    /**
     * GET /api/admin/appointments
     * Returns all appointments across the system.
     */
    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments(HttpServletRequest request) {
        User adminUser;
        try {
            adminUser = verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        List<AppointmentDTO> appointments = appointmentService.getAppointmentsForCustomer(adminUser);
        return ResponseEntity.ok(appointments);
    }

    /**
     * GET /api/admin/queue
     * Returns recent clinic queue activity.
     */
    @GetMapping("/queue")
    public ResponseEntity<?> getAllQueueTokens(HttpServletRequest request) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        List<QueueTokenDTO> tokens = queueService.getAllTokens();
        return ResponseEntity.ok(tokens);
    }

    /**
     * GET /api/admin/analytics
     * Returns daily and monthly booking trends, queue volume, and average wait time.
     */
    @GetMapping("/analytics")
    public ResponseEntity<?> getAdminAnalytics(HttpServletRequest request,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        LocalDate start = null;
        LocalDate end = null;
        try {
            if (startDate != null && !startDate.trim().isEmpty()) {
                start = LocalDate.parse(startDate.trim());
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                end = LocalDate.parse(endDate.trim());
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", "Invalid date format. Expected YYYY-MM-DD"));
        }

        List<Map<String, Object>> daily = appointmentRepository.getDailyBookingStats(start, end);
        List<Map<String, Object>> monthly = appointmentRepository.getMonthlyBookingStats();
        List<Map<String, Object>> queueTrends = queueTokenRepository.getQueueTrends(start, end);
        Double avgWait = queueTokenRepository.getAverageWaitMinutes(start, end);

        String avgWaitDisplay;
        if (avgWait != null) {
            double rounded = Math.round(avgWait * 10.0) / 10.0;
            avgWaitDisplay = rounded + " mins";
        } else {
            avgWaitDisplay = "Not available (no tokens called yet)";
        }

        AdminAnalyticsDTO analytics = new AdminAnalyticsDTO(
                daily,
                monthly,
                queueTrends,
                avgWait != null ? Math.round(avgWait * 10.0) / 10.0 : null,
                avgWaitDisplay,
                "Calculated as AVG(called_at - created_at) from stored timestamps of called patient tokens.",
                startDate,
                endDate
        );

        return ResponseEntity.ok(analytics);
    }

    /**
     * PUT /api/admin/users/{id}/role
     * Allows administrators to update a user's assigned system role.
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(HttpServletRequest request,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        String roleStr = body != null ? body.get("role") : null;
        if (roleStr == null || roleStr.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", "Role is required (ADMIN, PROVIDER, CUSTOMER)"));
        }

        Role newRole;
        try {
            newRole = Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, "Bad Request", "Invalid role: " + roleStr + ". Allowed: ADMIN, PROVIDER, CUSTOMER"));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", "User not found with ID: " + id));
        }

        userRepository.updateUserRole(id, newRole);
        user.setRole(newRole);

        return ResponseEntity.ok(UserProfileResponse.fromUser(user));
    }

    /**
     * PUT /api/admin/providers/{id}/toggle-active
     * Allows administrators to activate or deactivate a provider without deleting records.
     */
    @PutMapping("/providers/{id}/toggle-active")
    public ResponseEntity<?> toggleProviderActive(HttpServletRequest request, @PathVariable Long id) {
        try {
            verifyAdmin(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "Forbidden", e.getMessage()));
        }

        ProviderProfile provider = providerProfileRepository.findById(id).orElse(null);
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "Not Found", "Provider not found with ID: " + id));
        }

        provider.setActive(!provider.isActive());
        ProviderProfile updated = providerProfileRepository.save(provider);

        return ResponseEntity.ok(providerService.getProviderById(updated.getId()));
    }
}
