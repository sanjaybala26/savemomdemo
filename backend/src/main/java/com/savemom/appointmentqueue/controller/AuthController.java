package com.savemom.appointmentqueue.controller;

import com.savemom.appointmentqueue.dto.*;
import com.savemom.appointmentqueue.model.Role;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.repository.ProviderAvailabilityRepository;
import com.savemom.appointmentqueue.repository.ProviderProfileRepository;
import com.savemom.appointmentqueue.repository.UserRepository;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.security.JwtService;
import com.savemom.appointmentqueue.security.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Controller handling user registration, login, and current profile retrieval.
 * Implements strict input validation, BCrypt password hashing, and stateless JWT issuance.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderAvailabilityRepository providerAvailabilityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @org.springframework.beans.factory.annotation.Value("${app.auth.allow-admin-registration:true}")
    private boolean allowAdminRegistration = true;

    @Autowired
    public AuthController(UserRepository userRepository,
                          ProviderProfileRepository providerProfileRepository,
                          ProviderAvailabilityRepository providerAvailabilityRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerAvailabilityRepository = providerAvailabilityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * POST /api/auth/register
     * Registration endpoint. Supports CUSTOMER, PROVIDER, or ADMIN roles.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // 1. Validate name
        if (request.getName() == null || request.getName().trim().length() < 2) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Full name must be at least 2 characters long"));
        }

        // 2. Validate email format
        if (request.getEmail() == null || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Please provide a valid email address"));
        }

        // 3. Validate password strength
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Password must be at least 6 characters long"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // 4. Check for duplicate email
        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(409, "Conflict", "Email is already registered. Please log in."));
        }

        // 5. Determine requested role (defaults to CUSTOMER if omitted)
        Role assignedRole = Role.CUSTOMER;
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            try {
                assignedRole = Role.valueOf(request.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Invalid role specified: " + request.getRole()));
            }
        }

        // Restrict ADMIN self-registration unless running in local development mode
        if (assignedRole == Role.ADMIN && !allowAdminRegistration) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    new ErrorResponse(403, "Forbidden", "Public administrator registration is restricted in production. Administrators must be provisioned by an existing administrator.")
            );
        }

        // 6. Hash password with BCrypt (never store plain-text)
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = new User();
        newUser.setName(request.getName().trim());
        newUser.setEmail(normalizedEmail);
        newUser.setPasswordHash(hashedPassword);
        newUser.setRole(assignedRole);

        User savedUser = userRepository.save(newUser);

        // 7. If registering as PROVIDER (Doctor), create initial profile & availability so account is immediately functional
        if (assignedRole == Role.PROVIDER) {
            providerProfileRepository.findByUserId(savedUser.getId()).orElseGet(() -> {
                com.savemom.appointmentqueue.model.ProviderProfile profile = new com.savemom.appointmentqueue.model.ProviderProfile();
                profile.setUserId(savedUser.getId());
                profile.setDisplayName(savedUser.getName().startsWith("Dr.") ? savedUser.getName() : "Dr. " + savedUser.getName());
                profile.setServiceDescription("General Consultation & Clinical Care");
                profile.setActive(true);
                com.savemom.appointmentqueue.model.ProviderProfile savedProfile = providerProfileRepository.save(profile);

                com.savemom.appointmentqueue.model.ProviderAvailability availability = new com.savemom.appointmentqueue.model.ProviderAvailability();
                availability.setProviderId(savedProfile.getId());
                availability.setWorkingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
                availability.setOpenTime("09:00");
                availability.setCloseTime("17:00");
                availability.setSlotDurationMinutes(30);
                providerAvailabilityRepository.save(availability);

                return savedProfile;
            });
        }

        // 8. Issue JWT token
        String token = jwtService.generateToken(savedUser);
        UserProfileResponse profile = UserProfileResponse.fromUser(savedUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, profile));
    }

    /**
     * POST /api/auth/login
     * Authenticates user credentials and returns a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Email is required"));
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", "Password is required"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // Check if user exists
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(401, "Unauthorized", "Invalid email or password"));
        }

        User user = userOpt.get();

        // Verify password against BCrypt hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(401, "Unauthorized", "Invalid email or password"));
        }

        // Issue JWT token
        String token = jwtService.generateToken(user);
        UserProfileResponse profile = UserProfileResponse.fromUser(user);

        return ResponseEntity.ok(new AuthResponse(token, profile));
    }

    /**
     * GET /api/auth/me
     * Protected endpoint returning the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(401, "Unauthorized", "Authentication required"));
        }

        return ResponseEntity.ok(UserProfileResponse.fromUser(currentUser));
    }
}
