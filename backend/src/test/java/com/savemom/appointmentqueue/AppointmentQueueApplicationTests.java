package com.savemom.appointmentqueue;

import com.savemom.appointmentqueue.controller.AdminController;
import com.savemom.appointmentqueue.controller.AppointmentController;
import com.savemom.appointmentqueue.controller.AuthController;
import com.savemom.appointmentqueue.controller.HealthController;
import com.savemom.appointmentqueue.controller.ProviderController;
import com.savemom.appointmentqueue.controller.PublicProviderController;
import com.savemom.appointmentqueue.controller.QueueController;
import com.savemom.appointmentqueue.dto.*;
import com.savemom.appointmentqueue.service.ProviderService;
import com.savemom.appointmentqueue.model.QueueStatus;
import com.savemom.appointmentqueue.model.QueueToken;
import com.savemom.appointmentqueue.model.QueueTokenHistory;
import com.savemom.appointmentqueue.model.Role;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.repository.QueueTokenHistoryRepository;
import com.savemom.appointmentqueue.repository.QueueTokenRepository;
import com.savemom.appointmentqueue.repository.UserRepository;
import com.savemom.appointmentqueue.security.JwtAuthenticationInterceptor;
import com.savemom.appointmentqueue.security.JwtService;
import com.savemom.appointmentqueue.security.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppointmentQueueApplicationTests {

    @Autowired
    private HealthController healthController;

    @Autowired
    private AuthController authController;

    @Autowired
    private ProviderController providerController;

    @Autowired
    private PublicProviderController publicProviderController;

    @Autowired
    private AppointmentController appointmentController;

    @Autowired
    private QueueController queueController;

    @Autowired
    private AdminController adminController;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueTokenHistoryRepository queueTokenHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtAuthenticationInterceptor jwtInterceptor;

    @Test
    void contextLoads() {
        assertNotNull(healthController, "HealthController should be in context");
        assertNotNull(authController, "AuthController should be in context");
        assertNotNull(providerController, "ProviderController should be in context");
        assertNotNull(publicProviderController, "PublicProviderController should be in context");
        assertNotNull(userRepository, "UserRepository should be in context");
    }

    @Test
    void healthEndpointReturnsOk() {
        ResponseEntity<HealthResponse> response = healthController.checkHealth();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().getStatus());
    }

    @Test
    void testRegistrationFlowAlwaysCreatesCustomer() {
        String testEmail = "patient_" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest("Test Patient", testEmail, "secret123");

        ResponseEntity<?> response = authController.register(registerReq);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        assertTrue(response.getBody() instanceof AuthResponse);
        AuthResponse authResp = (AuthResponse) response.getBody();
        assertNotNull(authResp.getToken());
        assertEquals("Test Patient", authResp.getUser().getName());
        assertEquals(testEmail, authResp.getUser().getEmail());
        assertEquals(Role.CUSTOMER.name(), authResp.getUser().getRole(), "New user must strictly be CUSTOMER");

        // Verify password is encrypted in database
        User dbUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertNotEquals("secret123", dbUser.getPasswordHash(), "Plain password must never be stored");
        assertTrue(passwordEncoder.matches("secret123", dbUser.getPasswordHash()), "BCrypt hash must verify against password");
    }

    @Test
    void testDuplicateRegistrationFails() {
        String email = "dup_" + System.currentTimeMillis() + "@example.com";
        RegisterRequest req = new RegisterRequest("First User", email, "secret123");
        ResponseEntity<?> resp1 = authController.register(req);
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());

        ResponseEntity<?> resp2 = authController.register(req);
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }

    @Test
    void testLoginWithValidAndInvalidCredentials() {
        String email = "login_" + System.currentTimeMillis() + "@example.com";
        authController.register(new RegisterRequest("Login User", email, "correctPass123"));

        // Valid login
        ResponseEntity<?> goodLogin = authController.login(new LoginRequest(email, "correctPass123"));
        assertEquals(HttpStatus.OK, goodLogin.getStatusCode());
        assertTrue(goodLogin.getBody() instanceof AuthResponse);

        // Invalid login (wrong password)
        ResponseEntity<?> badPass = authController.login(new LoginRequest(email, "wrongPassword"));
        assertEquals(HttpStatus.UNAUTHORIZED, badPass.getStatusCode());

        // Invalid login (non-existent email)
        ResponseEntity<?> unknownUser = authController.login(new LoginRequest("noone@example.com", "anyPass"));
        assertEquals(HttpStatus.UNAUTHORIZED, unknownUser.getStatusCode());
    }

    @Test
    void testProtectedMeEndpointWithJwt() throws Exception {
        String email = "profile_" + System.currentTimeMillis() + "@example.com";
        ResponseEntity<?> reg = authController.register(new RegisterRequest("Profile User", email, "pass123456"));
        AuthResponse auth = (AuthResponse) reg.getBody();
        String token = auth.getToken();

        // 1. Test interceptor with valid token
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = jwtInterceptor.preHandle(request, response, new Object());
        assertTrue(allowed, "Valid token should be allowed by interceptor");

        // 2. Call controller with request containing user attribute
        ResponseEntity<?> meResp = authController.getCurrentUser(request);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertTrue(meResp.getBody() instanceof UserProfileResponse);
        UserProfileResponse profile = (UserProfileResponse) meResp.getBody();
        assertEquals("Profile User", profile.getName());
        assertEquals(email, profile.getEmail());

        // 3. Test interceptor with invalid token
        MockHttpServletRequest badRequest = new MockHttpServletRequest();
        badRequest.addHeader("Authorization", "Bearer invalid.fake.token");
        MockHttpServletResponse badResponse = new MockHttpServletResponse();

        boolean rejected = jwtInterceptor.preHandle(badRequest, badResponse, new Object());
        assertFalse(rejected, "Invalid token must be rejected");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), badResponse.getStatus());
    }

    @Test
    void testRoleSpecificRegistrationAndValidation() {
        // 1. Role-specific registration as PROVIDER
        String docEmail = "doc_role_" + System.currentTimeMillis() + "@example.com";
        RegisterRequest docReq = new RegisterRequest("Dr. Role Tester", docEmail, "secret123", "PROVIDER");
        ResponseEntity<?> docResp = authController.register(docReq);
        assertEquals(HttpStatus.CREATED, docResp.getStatusCode());
        AuthResponse docAuth = (AuthResponse) docResp.getBody();
        assertEquals(Role.PROVIDER.name(), docAuth.getUser().getRole());

        // 2. Role-specific registration as ADMIN
        String adminEmail = "admin_role_" + System.currentTimeMillis() + "@example.com";
        RegisterRequest adminReq = new RegisterRequest("Admin Role Tester", adminEmail, "secret123", "ADMIN");
        ResponseEntity<?> adminResp = authController.register(adminReq);
        assertEquals(HttpStatus.CREATED, adminResp.getStatusCode());
        AuthResponse adminAuth = (AuthResponse) adminResp.getBody();
        assertEquals(Role.ADMIN.name(), adminAuth.getUser().getRole());

        // 3. Invalid role rejection
        RegisterRequest badRoleReq = new RegisterRequest("Bad Role User", "badrole@example.com", "secret123", "SUPERUSER");
        ResponseEntity<?> badRoleResp = authController.register(badRoleReq);
        assertEquals(HttpStatus.BAD_REQUEST, badRoleResp.getStatusCode());

        // 4. Input validation: short name, invalid email, short password
        RegisterRequest shortName = new RegisterRequest("A", "valid@example.com", "secret123");
        assertEquals(HttpStatus.BAD_REQUEST, authController.register(shortName).getStatusCode());

        RegisterRequest badEmail = new RegisterRequest("Valid Name", "invalid-email-format", "secret123");
        assertEquals(HttpStatus.BAD_REQUEST, authController.register(badEmail).getStatusCode());

        RegisterRequest shortPass = new RegisterRequest("Valid Name", "valid2@example.com", "123");
        assertEquals(HttpStatus.BAD_REQUEST, authController.register(shortPass).getStatusCode());
    }

    @Test
    void testCustomerCannotAccessProviderManagementEndpoints() {
        // Create customer user
        User customer = new User();
        customer.setName("Regular Patient");
        customer.setEmail("cust_" + System.currentTimeMillis() + "@example.com");
        customer.setPasswordHash(passwordEncoder.encode("secret123"));
        customer.setRole(Role.CUSTOMER);
        customer = userRepository.save(customer);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer);

        // Try GET /api/provider/me
        ResponseEntity<?> getResp = providerController.getMyProfile(request);
        assertEquals(HttpStatus.FORBIDDEN, getResp.getStatusCode(), "Customer must receive 403 Forbidden for provider GET");

        // Try PUT /api/provider/me
        ProviderUpdateRequest updateReq = new ProviderUpdateRequest("Dr. Malicious", "Hacked", true,
                Arrays.asList("MONDAY"), "09:00", "17:00", 30);
        ResponseEntity<?> putResp = providerController.updateMyProfile(request, updateReq);
        assertEquals(HttpStatus.FORBIDDEN, putResp.getStatusCode(), "Customer must receive 403 Forbidden for provider PUT");
    }

    @Test
    void testProviderProfileManagementAndSlotDerivation() {
        // 1. Create a provider account directly
        User providerUser = new User();
        providerUser.setName("Dr. Sarah Jenkins");
        providerUser.setEmail("dr_sarah_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("providerPass123"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        // 2. GET /api/provider/me auto-initializes default schedule
        ResponseEntity<?> initialResp = providerController.getMyProfile(request);
        assertEquals(HttpStatus.OK, initialResp.getStatusCode());
        assertTrue(initialResp.getBody() instanceof ProviderProfileDTO);
        ProviderProfileDTO initialProfile = (ProviderProfileDTO) initialResp.getBody();
        assertEquals("Dr. Sarah Jenkins", initialProfile.getDisplayName());
        assertEquals(5, initialProfile.getWorkingDays().size());
        assertEquals("09:00", initialProfile.getOpenTime());
        assertEquals("17:00", initialProfile.getCloseTime());
        assertEquals(30, initialProfile.getSlotDurationMinutes());

        // 3. Update profile and custom schedule (Monday, Wednesday, Friday, 10:00 to 12:00, 30 min slots)
        ProviderUpdateRequest updateReq = new ProviderUpdateRequest(
                "Dr. Sarah Jenkins MD",
                "Obstetrics and Gynecological Care",
                true,
                Arrays.asList("MONDAY", "WEDNESDAY", "FRIDAY"),
                "10:00",
                "12:00",
                30
        );

        ResponseEntity<?> updateResp = providerController.updateMyProfile(request, updateReq);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        ProviderProfileDTO updatedProfile = (ProviderProfileDTO) updateResp.getBody();
        assertEquals("Dr. Sarah Jenkins MD", updatedProfile.getDisplayName());
        assertEquals("Obstetrics and Gynecological Care", updatedProfile.getServiceDescription());
        assertEquals(3, updatedProfile.getWorkingDays().size());
        assertEquals("10:00", updatedProfile.getOpenTime());
        assertEquals("12:00", updatedProfile.getCloseTime());

        Long providerId = updatedProfile.getId();

        // 4. Test public listing includes active provider
        ResponseEntity<List<ProviderProfileDTO>> activeListResp = publicProviderController.getActiveProviders();
        assertEquals(HttpStatus.OK, activeListResp.getStatusCode());
        boolean foundInList = activeListResp.getBody().stream()
                .anyMatch(p -> p.getId().equals(providerId));
        assertTrue(foundInList, "Updated provider should appear in active providers list");

        // 5. Test public slot generation on a working day (next Wednesday)
        LocalDate nextWednesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        ResponseEntity<?> workingDaySlotsResp = publicProviderController.getAvailableSlots(providerId, nextWednesday.toString());
        assertEquals(HttpStatus.OK, workingDaySlotsResp.getStatusCode());
        assertTrue(workingDaySlotsResp.getBody() instanceof ProviderAvailabilityResponse);
        ProviderAvailabilityResponse availResp = (ProviderAvailabilityResponse) workingDaySlotsResp.getBody();

        assertTrue(availResp.isWorkingDay(), "Wednesday should be recognized as a working day");
        // 10:00 to 12:00 with 30-min duration should yield 4 slots: 10:00-10:30, 10:30-11:00, 11:00-11:30, 11:30-12:00
        assertEquals(4, availResp.getSlots().size(), "There should be exactly 4 slots between 10:00 and 12:00 (30 min each)");
        assertEquals("10:00", availResp.getSlots().get(0).getStartTime());
        assertEquals("10:30", availResp.getSlots().get(0).getEndTime());
        assertEquals("11:30", availResp.getSlots().get(3).getStartTime());
        assertEquals("12:00", availResp.getSlots().get(3).getEndTime());

        // 6. Test slot generation on a non-working day (next Tuesday)
        LocalDate nextTuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        ResponseEntity<?> closedDaySlotsResp = publicProviderController.getAvailableSlots(providerId, nextTuesday.toString());
        assertEquals(HttpStatus.OK, closedDaySlotsResp.getStatusCode());
        ProviderAvailabilityResponse closedResp = (ProviderAvailabilityResponse) closedDaySlotsResp.getBody();
        assertFalse(closedResp.isWorkingDay(), "Tuesday is not configured as a working day");
        assertTrue(closedResp.getSlots().isEmpty(), "No slots should be returned for closed days");
    }

    @Test
    void testProviderScheduleValidationRejectsInvalidHours() {
        User providerUser = new User();
        providerUser.setName("Dr. Validation Tester");
        providerUser.setEmail("dr_val_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        // Inverted hours: open at 18:00, close at 09:00
        ProviderUpdateRequest invalidHoursReq = new ProviderUpdateRequest(
                "Dr. Tester",
                "Description",
                true,
                Arrays.asList("MONDAY"),
                "18:00",
                "09:00",
                30
        );

        ResponseEntity<?> invalidHoursResp = providerController.updateMyProfile(request, invalidHoursReq);
        assertEquals(HttpStatus.BAD_REQUEST, invalidHoursResp.getStatusCode());

        // Duration too long for operating window
        ProviderUpdateRequest invalidDurationReq = new ProviderUpdateRequest(
                "Dr. Tester",
                "Description",
                true,
                Arrays.asList("MONDAY"),
                "09:00",
                "09:30",
                60 // 60 minutes cannot fit in 30 min window
        );

        ResponseEntity<?> invalidDurationResp = providerController.updateMyProfile(request, invalidDurationReq);
        assertEquals(HttpStatus.BAD_REQUEST, invalidDurationResp.getStatusCode());
    }

    @Test
    void testAppointmentBookingAndDoubleBookingPrevention() {
        // 1. Create a provider
        User providerUser = new User();
        providerUser.setName("Dr. Booking Doctor");
        providerUser.setEmail("dr_booking_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest providerReq = new MockHttpServletRequest();
        providerReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        // Schedule: Monday, Wednesday, 09:00 - 11:00, 30 mins
        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Booking Doctor", "Maternal Care", true,
                Arrays.asList("MONDAY", "WEDNESDAY"), "09:00", "11:00", 30
        );
        ResponseEntity<?> scheduleResp = providerController.updateMyProfile(providerReq, scheduleReq);
        ProviderProfileDTO profile = (ProviderProfileDTO) scheduleResp.getBody();
        Long providerId = profile.getId();

        // 2. Create customer 1
        User customer1 = new User();
        customer1.setName("Patient One");
        customer1.setEmail("patient1_" + System.currentTimeMillis() + "@example.com");
        customer1.setPasswordHash(passwordEncoder.encode("pass"));
        customer1.setRole(Role.CUSTOMER);
        customer1 = userRepository.save(customer1);

        MockHttpServletRequest cust1Req = new MockHttpServletRequest();
        cust1Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer1);

        LocalDate nextWed = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

        // 3. Customer 1 books 09:00 slot
        BookAppointmentRequest bookReq1 = new BookAppointmentRequest(
                providerId, nextWed.toString(), "09:00", "09:30", "First prenatal consultation"
        );
        ResponseEntity<?> bookResp1 = appointmentController.bookAppointment(cust1Req, bookReq1);
        assertEquals(HttpStatus.CREATED, bookResp1.getStatusCode());
        assertTrue(bookResp1.getBody() instanceof AppointmentDTO);
        AppointmentDTO appt1 = (AppointmentDTO) bookResp1.getBody();
        assertEquals("PENDING", appt1.getStatus(), "New appointment must be created with PENDING status");
        assertEquals("09:00", appt1.getStartTime());
        assertEquals("09:30", appt1.getEndTime());

        // 4. Verify slot is marked as booked in public availability endpoint
        ResponseEntity<?> slotsResp = publicProviderController.getAvailableSlots(providerId, nextWed.toString());
        ProviderAvailabilityResponse availResp = (ProviderAvailabilityResponse) slotsResp.getBody();
        TimeSlotDTO slot0900 = availResp.getSlots().stream()
                .filter(s -> s.getStartTime().equals("09:00"))
                .findFirst().orElseThrow();
        assertTrue(slot0900.isBooked(), "09:00 slot must now be marked as booked");

        TimeSlotDTO slot0930 = availResp.getSlots().stream()
                .filter(s -> s.getStartTime().equals("09:30"))
                .findFirst().orElseThrow();
        assertFalse(slot0930.isBooked(), "09:30 slot must still be available");

        // 5. Customer 2 attempts to book the SAME 09:00 slot (Double booking attempt)
        User customer2 = new User();
        customer2.setName("Patient Two");
        customer2.setEmail("patient2_" + System.currentTimeMillis() + "@example.com");
        customer2.setPasswordHash(passwordEncoder.encode("pass"));
        customer2.setRole(Role.CUSTOMER);
        customer2 = userRepository.save(customer2);

        MockHttpServletRequest cust2Req = new MockHttpServletRequest();
        cust2Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer2);

        BookAppointmentRequest doubleBookReq = new BookAppointmentRequest(
                providerId, nextWed.toString(), "09:00", "09:30", "Attempting double booking"
        );
        ResponseEntity<?> doubleBookResp = appointmentController.bookAppointment(cust2Req, doubleBookReq);
        assertEquals(HttpStatus.CONFLICT, doubleBookResp.getStatusCode(), "Double booking must return HTTP 409 CONFLICT");

        // 6. Customer 1 views their appointments
        ResponseEntity<?> myApptsResp = appointmentController.getMyAppointments(cust1Req);
        assertEquals(HttpStatus.OK, myApptsResp.getStatusCode());
        List<AppointmentDTO> myAppts = (List<AppointmentDTO>) myApptsResp.getBody();
        assertFalse(myAppts.isEmpty());
        assertEquals(appt1.getId(), myAppts.get(0).getId());
    }

    @Test
    void testDoctorCannotBookWithSelfButCanBookWithAnotherDoctor() {
        // 1. Create Doctor A
        User docAUser = new User();
        docAUser.setName("Dr. Self Booking A");
        docAUser.setEmail("doc_a_" + System.currentTimeMillis() + "@example.com");
        docAUser.setPasswordHash(passwordEncoder.encode("pass"));
        docAUser.setRole(Role.PROVIDER);
        docAUser = userRepository.save(docAUser);

        MockHttpServletRequest docAReq = new MockHttpServletRequest();
        docAReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, docAUser);

        ProviderUpdateRequest docASched = new ProviderUpdateRequest(
                "Dr. Self Booking A", "Cardiology", true,
                Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), "09:00", "12:00", 30
        );
        ProviderProfileDTO docAProfile = (ProviderProfileDTO) providerController.updateMyProfile(docAReq, docASched).getBody();

        // 2. Create Doctor B
        User docBUser = new User();
        docBUser.setName("Dr. Colleague B");
        docBUser.setEmail("doc_b_" + System.currentTimeMillis() + "@example.com");
        docBUser.setPasswordHash(passwordEncoder.encode("pass"));
        docBUser.setRole(Role.PROVIDER);
        docBUser = userRepository.save(docBUser);

        MockHttpServletRequest docBReq = new MockHttpServletRequest();
        docBReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, docBUser);

        ProviderUpdateRequest docBSched = new ProviderUpdateRequest(
                "Dr. Colleague B", "Neurology", true,
                Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), "09:00", "12:00", 30
        );
        ProviderProfileDTO docBProfile = (ProviderProfileDTO) providerController.updateMyProfile(docBReq, docBSched).getBody();

        LocalDate nextWed = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

        // 3. Doctor A attempts to book an appointment with THEMSELVES (Doctor A profile) -> must be rejected
        BookAppointmentRequest selfBookReq = new BookAppointmentRequest(
                docAProfile.getId(), nextWed.toString(), "09:00", "09:30", "Attempting self-booking"
        );
        ResponseEntity<?> selfBookResp = appointmentController.bookAppointment(docAReq, selfBookReq);
        assertEquals(HttpStatus.BAD_REQUEST, selfBookResp.getStatusCode(), "Self booking must be rejected with 400 Bad Request");
        ErrorResponse err = (ErrorResponse) selfBookResp.getBody();
        assertTrue(err.getMessage().contains("themselves"), "Error message should mention self-booking prevention");

        // 4. Doctor A books an appointment with DOCTOR B -> must succeed
        BookAppointmentRequest peerBookReq = new BookAppointmentRequest(
                docBProfile.getId(), nextWed.toString(), "09:00", "09:30", "Consultation with peer specialist"
        );
        ResponseEntity<?> peerBookResp = appointmentController.bookAppointment(docAReq, peerBookReq);
        assertEquals(HttpStatus.CREATED, peerBookResp.getStatusCode(), "Doctor booking with another doctor must succeed");
        AppointmentDTO appt = (AppointmentDTO) peerBookResp.getBody();
        assertEquals(docAUser.getId(), appt.getCustomerId(), "Doctor A should be recorded as the customer/patient");
        assertEquals(docBProfile.getId(), appt.getProviderId(), "Doctor B should be the provider");

        // 5. Admin books an appointment with DOCTOR B for themselves -> must succeed
        User adminUser = new User();
        adminUser.setName("Clinic Admin");
        adminUser.setEmail("admin_patient_" + System.currentTimeMillis() + "@example.com");
        adminUser.setPasswordHash(passwordEncoder.encode("pass"));
        adminUser.setRole(Role.ADMIN);
        adminUser = userRepository.save(adminUser);

        MockHttpServletRequest adminReq = new MockHttpServletRequest();
        adminReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, adminUser);

        BookAppointmentRequest adminBookReq = new BookAppointmentRequest(
                docBProfile.getId(), nextWed.toString(), "09:30", "10:00", "Admin routine checkup"
        );
        ResponseEntity<?> adminBookResp = appointmentController.bookAppointment(adminReq, adminBookReq);
        assertEquals(HttpStatus.CREATED, adminBookResp.getStatusCode(), "Admin booking with doctor must succeed");
        AppointmentDTO adminAppt = (AppointmentDTO) adminBookResp.getBody();
        assertEquals(adminUser.getId(), adminAppt.getCustomerId(), "Admin must be the patient");
        assertEquals(docBProfile.getId(), adminAppt.getProviderId(), "Doctor B must be the provider");
    }

    @Test
    void testCustomerCannotCancelOtherCustomerAppointment() {
        // Create Provider
        User providerUser = new User();
        providerUser.setName("Dr. Guard Doctor");
        providerUser.setEmail("dr_guard_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest providerReq = new MockHttpServletRequest();
        providerReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Guard Doctor", "General", true,
                Arrays.asList("FRIDAY"), "14:00", "16:00", 30
        );
        ResponseEntity<?> scheduleResp = providerController.updateMyProfile(providerReq, scheduleReq);
        Long providerId = ((ProviderProfileDTO) scheduleResp.getBody()).getId();

        // Customer A books appointment
        User customerA = new User();
        customerA.setName("Customer Alice");
        customerA.setEmail("alice_" + System.currentTimeMillis() + "@example.com");
        customerA.setPasswordHash(passwordEncoder.encode("pass"));
        customerA.setRole(Role.CUSTOMER);
        customerA = userRepository.save(customerA);

        MockHttpServletRequest reqA = new MockHttpServletRequest();
        reqA.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customerA);

        LocalDate nextFri = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        BookAppointmentRequest bookReq = new BookAppointmentRequest(providerId, nextFri.toString(), "14:00", "14:30", "Alice appt");
        AppointmentDTO appt = (AppointmentDTO) appointmentController.bookAppointment(reqA, bookReq).getBody();

        // Customer B tries to cancel Customer A's appointment
        User customerB = new User();
        customerB.setName("Customer Bob");
        customerB.setEmail("bob_" + System.currentTimeMillis() + "@example.com");
        customerB.setPasswordHash(passwordEncoder.encode("pass"));
        customerB.setRole(Role.CUSTOMER);
        customerB = userRepository.save(customerB);

        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customerB);

        ResponseEntity<?> cancelResp = appointmentController.cancelAppointment(reqB, appt.getId(), new CancelAppointmentRequest("Malicious cancel"));
        assertEquals(HttpStatus.FORBIDDEN, cancelResp.getStatusCode(), "Customer cannot cancel another customer's appointment (403 Forbidden)");
    }

    @Test
    void testAppointmentCancellationReleasesSlotForRebooking() {
        // Setup Provider
        User providerUser = new User();
        providerUser.setName("Dr. Rebooking Doctor");
        providerUser.setEmail("dr_rebook_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest providerReq = new MockHttpServletRequest();
        providerReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Rebooking Doctor", "Cardiology", true,
                Arrays.asList("MONDAY"), "10:00", "12:00", 30
        );
        ResponseEntity<?> scheduleResp = providerController.updateMyProfile(providerReq, scheduleReq);
        Long providerId = ((ProviderProfileDTO) scheduleResp.getBody()).getId();

        // Customer 1 books slot 10:00
        User customer1 = new User();
        customer1.setName("Patient One");
        customer1.setEmail("p1_" + System.currentTimeMillis() + "@example.com");
        customer1.setPasswordHash(passwordEncoder.encode("pass"));
        customer1.setRole(Role.CUSTOMER);
        customer1 = userRepository.save(customer1);

        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer1);

        LocalDate nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        BookAppointmentRequest bookReq = new BookAppointmentRequest(providerId, nextMon.toString(), "10:00", "10:30", "Initial checkup");
        AppointmentDTO appt = (AppointmentDTO) appointmentController.bookAppointment(req1, bookReq).getBody();
        assertEquals("PENDING", appt.getStatus());

        // Customer 1 cancels their appointment
        ResponseEntity<?> cancelResp = appointmentController.cancelAppointment(req1, appt.getId(), new CancelAppointmentRequest("Schedule conflict"));
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        AppointmentDTO cancelledAppt = (AppointmentDTO) cancelResp.getBody();
        assertEquals("CANCELLED", cancelledAppt.getStatus());

        // Verify history recorded status transition
        ResponseEntity<?> histResp = appointmentController.getAppointmentHistory(req1, appt.getId());
        List<AppointmentHistoryDTO> history = (List<AppointmentHistoryDTO>) histResp.getBody();
        assertEquals(2, history.size(), "Should have 2 history records: initial booking and cancellation");
        assertEquals("CANCELLED", history.get(1).getToStatus());

        // Customer 2 can now book the same 10:00 slot without conflict!
        User customer2 = new User();
        customer2.setName("Patient Two");
        customer2.setEmail("p2_" + System.currentTimeMillis() + "@example.com");
        customer2.setPasswordHash(passwordEncoder.encode("pass"));
        customer2.setRole(Role.CUSTOMER);
        customer2 = userRepository.save(customer2);

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer2);

        BookAppointmentRequest bookReq2 = new BookAppointmentRequest(providerId, nextMon.toString(), "10:00", "10:30", "New booking in freed slot");
        ResponseEntity<?> bookResp2 = appointmentController.bookAppointment(req2, bookReq2);
        assertEquals(HttpStatus.CREATED, bookResp2.getStatusCode(), "Freed slot should be bookable again without conflict");
        AppointmentDTO appt2 = (AppointmentDTO) bookResp2.getBody();
        assertEquals("PENDING", appt2.getStatus());
    }

    @Test
    void testPhase5QueueTokenGenerationAndDailySequencing() {
        // Setup doctor
        User providerUser = new User();
        providerUser.setName("Dr. Queue Sequencer");
        providerUser.setEmail("queue_doc_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Queue Sequencer", "Pediatrics", true,
                Arrays.asList("WEDNESDAY", "THURSDAY"), "09:00", "12:00", 30
        );
        ResponseEntity<?> schedResp = providerController.updateMyProfile(provReq, scheduleReq);
        Long providerId = ((ProviderProfileDTO) schedResp.getBody()).getId();

        LocalDate nextWed = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        LocalDate nextThu = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY));

        // Patient 1 books Wednesday 09:00 slot
        User patient1 = new User();
        patient1.setName("Patient One");
        patient1.setEmail("q_p1_" + System.currentTimeMillis() + "@example.com");
        patient1.setPasswordHash(passwordEncoder.encode("pass"));
        patient1.setRole(Role.CUSTOMER);
        patient1 = userRepository.save(patient1);

        MockHttpServletRequest p1Req = new MockHttpServletRequest();
        p1Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient1);

        BookAppointmentRequest b1 = new BookAppointmentRequest(providerId, nextWed.toString(), "09:00", "09:30", "Fever check");
        AppointmentDTO appt1 = (AppointmentDTO) appointmentController.bookAppointment(p1Req, b1).getBody();
        assertNotNull(appt1.getTokenNumber(), "Token number must be generated on booking");
        assertEquals(1, appt1.getTokenNumber(), "First patient on Wednesday must receive token #1");
        assertEquals("T-001", appt1.getTokenDisplay());
        assertEquals("WAITING", appt1.getTokenStatus());

        // Patient 2 books Wednesday 09:30 slot -> gets token #2
        User patient2 = new User();
        patient2.setName("Patient Two");
        patient2.setEmail("q_p2_" + System.currentTimeMillis() + "@example.com");
        patient2.setPasswordHash(passwordEncoder.encode("pass"));
        patient2.setRole(Role.CUSTOMER);
        patient2 = userRepository.save(patient2);

        MockHttpServletRequest p2Req = new MockHttpServletRequest();
        p2Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient2);

        BookAppointmentRequest b2 = new BookAppointmentRequest(providerId, nextWed.toString(), "09:30", "10:00", "Vaccination");
        AppointmentDTO appt2 = (AppointmentDTO) appointmentController.bookAppointment(p2Req, b2).getBody();
        assertEquals(2, appt2.getTokenNumber(), "Second patient on Wednesday must receive token #2");
        assertEquals("T-002", appt2.getTokenDisplay());
        assertEquals("WAITING", appt2.getTokenStatus());

        // Patient 3 books Thursday slot -> sequence resets to 1 for the new date!
        User patient3 = new User();
        patient3.setName("Patient Three");
        patient3.setEmail("q_p3_" + System.currentTimeMillis() + "@example.com");
        patient3.setPasswordHash(passwordEncoder.encode("pass"));
        patient3.setRole(Role.CUSTOMER);
        patient3 = userRepository.save(patient3);

        MockHttpServletRequest p3Req = new MockHttpServletRequest();
        p3Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient3);

        BookAppointmentRequest b3 = new BookAppointmentRequest(providerId, nextThu.toString(), "09:00", "09:30", "Consultation");
        AppointmentDTO appt3 = (AppointmentDTO) appointmentController.bookAppointment(p3Req, b3).getBody();
        assertEquals(1, appt3.getTokenNumber(), "First patient on Thursday must receive token #1 (daily sequence isolation)");
        assertEquals("T-001", appt3.getTokenDisplay());
    }

    @Test
    void testPhase5ProviderQueueCallNextAndStatusTransitions() {
        // Setup doctor
        User providerUser = new User();
        providerUser.setName("Dr. Queue Manager");
        providerUser.setEmail("mgr_doc_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Queue Manager", "ENT", true,
                Arrays.asList("FRIDAY"), "14:00", "16:00", 30
        );
        ResponseEntity<?> schedResp = providerController.updateMyProfile(provReq, scheduleReq);
        Long providerId = ((ProviderProfileDTO) schedResp.getBody()).getId();

        LocalDate nextFri = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));

        // Patient books
        User patient = new User();
        patient.setName("Queue Patient");
        patient.setEmail("queue_patient_" + System.currentTimeMillis() + "@example.com");
        patient.setPasswordHash(passwordEncoder.encode("pass"));
        patient.setRole(Role.CUSTOMER);
        patient = userRepository.save(patient);

        MockHttpServletRequest pReq = new MockHttpServletRequest();
        pReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient);

        BookAppointmentRequest bReq = new BookAppointmentRequest(providerId, nextFri.toString(), "14:00", "14:30", "Ear pain");
        AppointmentDTO booked = (AppointmentDTO) appointmentController.bookAppointment(pReq, bReq).getBody();

        // 1. Provider views daily queue for Friday
        ResponseEntity<?> queueResp = queueController.getProviderQueue(provReq, nextFri);
        assertEquals(HttpStatus.OK, queueResp.getStatusCode());
        List<QueueTokenDTO> queue = (List<QueueTokenDTO>) queueResp.getBody();
        assertEquals(1, queue.size());
        assertEquals("WAITING", queue.get(0).getStatus());
        assertEquals("T-001", queue.get(0).getTokenDisplay());
        Long tokenId = queue.get(0).getId();

        // 2. Provider calls next patient
        ResponseEntity<?> callResp = queueController.callNextPatient(provReq, nextFri);
        assertEquals(HttpStatus.OK, callResp.getStatusCode());
        QueueTokenDTO calledToken = (QueueTokenDTO) callResp.getBody();
        assertEquals("CALLED", calledToken.getStatus());
        assertNotNull(calledToken.getCalledAt(), "calledAt timestamp must be set");

        // 3. Provider transitions status to IN_PROGRESS
        ResponseEntity<?> progResp = queueController.updateTokenStatus(
                provReq, tokenId, new UpdateTokenStatusRequest("IN_PROGRESS", "Patient entered consultation room")
        );
        assertEquals(HttpStatus.OK, progResp.getStatusCode());
        QueueTokenDTO inProgToken = (QueueTokenDTO) progResp.getBody();
        assertEquals("IN_PROGRESS", inProgToken.getStatus());

        // 4. Provider transitions status to COMPLETED
        ResponseEntity<?> compResp = queueController.updateTokenStatus(
                provReq, tokenId, new UpdateTokenStatusRequest("COMPLETED", "Consultation finished, prescribed medication")
        );
        assertEquals(HttpStatus.OK, compResp.getStatusCode());
        QueueTokenDTO compToken = (QueueTokenDTO) compResp.getBody();
        assertEquals("COMPLETED", compToken.getStatus());
        assertNotNull(compToken.getCompletedAt(), "completedAt timestamp must be set");

        // Verify history contains all transitions
        List<QueueTokenHistory> history = queueTokenHistoryRepository.findByTokenId(tokenId);
        assertEquals(4, history.size(), "Should have 4 history records: WAITING -> CALLED -> IN_PROGRESS -> COMPLETED");
    }

    @Test
    void testPhase5CustomerForbiddenFromQueueManagement() {
        User customer = new User();
        customer.setName("Unauthorized Patient");
        customer.setEmail("unauth_" + System.currentTimeMillis() + "@example.com");
        customer.setPasswordHash(passwordEncoder.encode("pass"));
        customer.setRole(Role.CUSTOMER);
        customer = userRepository.save(customer);

        MockHttpServletRequest custReq = new MockHttpServletRequest();
        custReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer);

        // Customer trying to view provider queue
        ResponseEntity<?> queueResp = queueController.getProviderQueue(custReq, LocalDate.now());
        assertEquals(HttpStatus.FORBIDDEN, queueResp.getStatusCode());

        // Customer trying to call next patient
        ResponseEntity<?> callResp = queueController.callNextPatient(custReq, LocalDate.now());
        assertEquals(HttpStatus.FORBIDDEN, callResp.getStatusCode());

        // Customer trying to update a token status
        ResponseEntity<?> updateResp = queueController.updateTokenStatus(
                custReq, 1L, new UpdateTokenStatusRequest("COMPLETED", "Unauthorized update")
        );
        assertEquals(HttpStatus.FORBIDDEN, updateResp.getStatusCode());
    }

    @Test
    void testPhase5CancellationUpdatesQueueTokenToCancelled() {
        // Setup doctor
        User providerUser = new User();
        providerUser.setName("Dr. Cancel Queue");
        providerUser.setEmail("cq_doc_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Cancel Queue", "General", true,
                Arrays.asList("MONDAY"), "08:00", "10:00", 30
        );
        providerController.updateMyProfile(provReq, scheduleReq);
        Long providerId = ((ProviderProfileDTO) providerController.getMyProfile(provReq).getBody()).getId();

        LocalDate nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Patient books
        User patient = new User();
        patient.setName("Patient Cancel");
        patient.setEmail("pc_" + System.currentTimeMillis() + "@example.com");
        patient.setPasswordHash(passwordEncoder.encode("pass"));
        patient.setRole(Role.CUSTOMER);
        patient = userRepository.save(patient);

        MockHttpServletRequest pReq = new MockHttpServletRequest();
        pReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient);

        BookAppointmentRequest bReq = new BookAppointmentRequest(providerId, nextMon.toString(), "08:00", "08:30", "Routine checkup");
        AppointmentDTO booked = (AppointmentDTO) appointmentController.bookAppointment(pReq, bReq).getBody();
        assertEquals("WAITING", booked.getTokenStatus());

        // Cancel appointment
        ResponseEntity<?> cancelResp = appointmentController.cancelAppointment(
                pReq, booked.getId(), new CancelAppointmentRequest("No longer needed")
        );
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        AppointmentDTO cancelledAppt = (AppointmentDTO) cancelResp.getBody();
        assertEquals("CANCELLED", cancelledAppt.getStatus());
        assertEquals("CANCELLED", cancelledAppt.getTokenStatus());

        // Check token directly in repository
        QueueToken token = queueTokenRepository.findByAppointmentId(booked.getId()).orElseThrow();
        assertEquals(QueueStatus.CANCELLED, token.getStatus());

        // Verify queue token audit history has CANCELLED
        List<QueueTokenHistory> tokenHistory = queueTokenHistoryRepository.findByTokenId(token.getId());
        assertEquals(2, tokenHistory.size(), "Should have 2 history entries: issuance and cancellation");
        assertEquals(QueueStatus.CANCELLED, tokenHistory.get(1).getToStatus());
        assertEquals("No longer needed", tokenHistory.get(1).getReasonOrNotes());
    }

    @Test
    void testPhase6CustomerQueueTrackingWithPeopleAheadAndWaitTime() {
        // Setup doctor with 30-min slot duration
        User doctor = new User();
        doctor.setName("Dr. Queue Tracker");
        doctor.setEmail("track_doc_" + System.currentTimeMillis() + "@example.com");
        doctor.setPasswordHash(passwordEncoder.encode("pass"));
        doctor.setRole(Role.PROVIDER);
        doctor = userRepository.save(doctor);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, doctor);

        ProviderUpdateRequest schedReq = new ProviderUpdateRequest(
                "Dr. Queue Tracker", "General", true,
                Arrays.asList("MONDAY"), "09:00", "12:00", 30
        );
        providerController.updateMyProfile(provReq, schedReq);
        Long providerId = ((ProviderProfileDTO) providerController.getMyProfile(provReq).getBody()).getId();

        LocalDate nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Patient 1 books 09:00 slot
        User p1 = new User();
        p1.setName("Patient First");
        p1.setEmail("p1_track_" + System.currentTimeMillis() + "@example.com");
        p1.setPasswordHash(passwordEncoder.encode("pass"));
        p1.setRole(Role.CUSTOMER);
        p1 = userRepository.save(p1);

        MockHttpServletRequest p1Req = new MockHttpServletRequest();
        p1Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, p1);

        appointmentController.bookAppointment(p1Req, new BookAppointmentRequest(providerId, nextMon.toString(), "09:00", "09:30", "Visit 1"));

        // Patient 2 books 09:30 slot
        User p2 = new User();
        p2.setName("Patient Second");
        p2.setEmail("p2_track_" + System.currentTimeMillis() + "@example.com");
        p2.setPasswordHash(passwordEncoder.encode("pass"));
        p2.setRole(Role.CUSTOMER);
        p2 = userRepository.save(p2);

        MockHttpServletRequest p2Req = new MockHttpServletRequest();
        p2Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, p2);

        appointmentController.bookAppointment(p2Req, new BookAppointmentRequest(providerId, nextMon.toString(), "09:30", "10:00", "Visit 2"));

        // Patient 1 checks their queue token -> 0 people ahead, 0 min wait
        ResponseEntity<?> p1TrackResp = queueController.getMyActiveToken(p1Req, null);
        assertEquals(HttpStatus.OK, p1TrackResp.getStatusCode());
        CustomerQueueStatusDTO p1Status = (CustomerQueueStatusDTO) p1TrackResp.getBody();
        assertEquals("T-001", p1Status.getTokenDisplay());
        assertEquals("WAITING", p1Status.getStatus());
        assertEquals(0, p1Status.getPeopleAhead(), "First patient should have 0 people ahead");
        assertEquals(0, p1Status.getEstimatedWaitMinutes(), "First patient should have 0 min estimated wait");

        // Patient 2 checks their queue token -> 1 person ahead, 30 min wait
        ResponseEntity<?> p2TrackResp = queueController.getMyActiveToken(p2Req, null);
        assertEquals(HttpStatus.OK, p2TrackResp.getStatusCode());
        CustomerQueueStatusDTO p2Status = (CustomerQueueStatusDTO) p2TrackResp.getBody();
        assertEquals("T-002", p2Status.getTokenDisplay());
        assertEquals("WAITING", p2Status.getStatus());
        assertEquals(1, p2Status.getPeopleAhead(), "Second patient should have 1 person ahead (Patient 1)");
        assertEquals(30, p2Status.getEstimatedWaitMinutes(), "Estimated wait should be 1 * 30 = 30 minutes");
    }

    @Test
    void testPhase6CustomerForbiddenFromViewingOthersTokens() {
        // Setup dedicated doctor
        User docUser = new User();
        docUser.setName("Dr. Ownership Check");
        docUser.setEmail("own_doc_" + System.currentTimeMillis() + "@example.com");
        docUser.setPasswordHash(passwordEncoder.encode("pass"));
        docUser.setRole(Role.PROVIDER);
        docUser = userRepository.save(docUser);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, docUser);

        ProviderUpdateRequest schedReq = new ProviderUpdateRequest(
                "Dr. Ownership Check", "General", true,
                Arrays.asList("MONDAY"), "09:00", "12:00", 30
        );
        providerController.updateMyProfile(provReq, schedReq);
        Long providerId = ((ProviderProfileDTO) providerController.getMyProfile(provReq).getBody()).getId();

        LocalDate nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Customer 1 books
        User c1 = new User();
        c1.setName("Customer One");
        c1.setEmail("c1_" + System.currentTimeMillis() + "@example.com");
        c1.setPasswordHash(passwordEncoder.encode("pass"));
        c1.setRole(Role.CUSTOMER);
        c1 = userRepository.save(c1);

        MockHttpServletRequest c1Req = new MockHttpServletRequest();
        c1Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, c1);

        AppointmentDTO appt1 = (AppointmentDTO) appointmentController.bookAppointment(
                c1Req, new BookAppointmentRequest(providerId, nextMon.toString(), "09:00", "09:30", "Checkup")
        ).getBody();

        // Customer 2 attempts to query Customer 1's token
        User c2 = new User();
        c2.setName("Customer Two");
        c2.setEmail("c2_" + System.currentTimeMillis() + "@example.com");
        c2.setPasswordHash(passwordEncoder.encode("pass"));
        c2.setRole(Role.CUSTOMER);
        c2 = userRepository.save(c2);

        MockHttpServletRequest c2Req = new MockHttpServletRequest();
        c2Req.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, c2);

        ResponseEntity<?> resp = queueController.getMyActiveToken(c2Req, appt1.getId());
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), "Customer 2 must not be able to view Customer 1's token");
    }

    @Test
    void testPhase6AdminDashboardMetricsAndLists() {
        User admin = new User();
        admin.setName("Clinic Administrator");
        admin.setEmail("admin_" + System.currentTimeMillis() + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode("pass"));
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        MockHttpServletRequest adminReq = new MockHttpServletRequest();
        adminReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, admin);

        // 1. Check stats endpoint
        ResponseEntity<?> statsResp = adminController.getAdminStats(adminReq);
        assertEquals(HttpStatus.OK, statsResp.getStatusCode());
        AdminStatsDTO stats = (AdminStatsDTO) statsResp.getBody();
        assertTrue(stats.getTotalUsers() > 0, "Admin stats should report users");
        assertTrue(stats.getTotalProviders() > 0, "Admin stats should report providers");
        assertTrue(stats.getTotalAppointments() >= 0);

        // 2. Check lists endpoints
        ResponseEntity<?> usersResp = adminController.getAllUsers(adminReq);
        assertEquals(HttpStatus.OK, usersResp.getStatusCode());
        assertTrue(((List<?>) usersResp.getBody()).size() > 0);

        ResponseEntity<?> provResp = adminController.getAllProviders(adminReq);
        assertEquals(HttpStatus.OK, provResp.getStatusCode());
        assertTrue(((List<?>) provResp.getBody()).size() > 0);

        ResponseEntity<?> apptResp = adminController.getAllAppointments(adminReq);
        assertEquals(HttpStatus.OK, apptResp.getStatusCode());

        ResponseEntity<?> qResp = adminController.getAllQueueTokens(adminReq);
        assertEquals(HttpStatus.OK, qResp.getStatusCode());
    }

    @Test
    void testPhase6NonAdminForbiddenFromAdminEndpoints() {
        User customer = new User();
        customer.setName("Regular Patient");
        customer.setEmail("reg_patient_" + System.currentTimeMillis() + "@example.com");
        customer.setPasswordHash(passwordEncoder.encode("pass"));
        customer.setRole(Role.CUSTOMER);
        customer = userRepository.save(customer);

        MockHttpServletRequest custReq = new MockHttpServletRequest();
        custReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, customer);

        assertEquals(HttpStatus.FORBIDDEN, adminController.getAdminStats(custReq).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.getAllUsers(custReq).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.getAllProviders(custReq).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.getAllAppointments(custReq).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.getAllQueueTokens(custReq).getStatusCode());
    }

    @Test
    void testPastSlotValidationAndLiveTimeHandling() {
        // 1. Create a provider working 7 days a week, 00:00 to 23:30, 30 min duration
        User providerUser = new User();
        providerUser.setName("Dr. Time Tester");
        providerUser.setEmail("dr_timetest_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest providerReq = new MockHttpServletRequest();
        providerReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest scheduleReq = new ProviderUpdateRequest(
                "Dr. Time Tester", "Emergency & Live Care", true,
                Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                "00:00", "23:30", 30
        );
        ResponseEntity<?> scheduleResp = providerController.updateMyProfile(providerReq, scheduleReq);
        ProviderProfileDTO profile = (ProviderProfileDTO) scheduleResp.getBody();
        Long providerId = profile.getId();

        // 2. Fetch slots for today and verify past slots are marked isPast = true
        LocalDate today = LocalDate.now(ProviderService.APP_TIMEZONE);
        LocalTime nowTime = LocalTime.now(ProviderService.APP_TIMEZONE);

        ResponseEntity<?> slotsResp = publicProviderController.getAvailableSlots(providerId, today.toString());
        assertEquals(HttpStatus.OK, slotsResp.getStatusCode());
        ProviderAvailabilityResponse availResp = (ProviderAvailabilityResponse) slotsResp.getBody();
        assertNotNull(availResp);
        assertNotNull(availResp.getTimezone());
        assertEquals("Asia/Kolkata", availResp.getTimezone());

        // Slots before current time should be marked isPast = true
        for (TimeSlotDTO slot : availResp.getSlots()) {
            LocalTime slotStart = LocalTime.parse(slot.getStartTime());
            if (!slotStart.isAfter(nowTime)) {
                assertTrue(slot.isPast(), "Slot at " + slot.getStartTime() + " should be marked as past since now is " + nowTime);
            }
        }

        // 3. Create a patient to test past slot booking rejection
        User patient = new User();
        patient.setName("Patient Live Test");
        patient.setEmail("patient_live_" + System.currentTimeMillis() + "@example.com");
        patient.setPasswordHash(passwordEncoder.encode("pass"));
        patient.setRole(Role.CUSTOMER);
        patient = userRepository.save(patient);

        MockHttpServletRequest patientReq = new MockHttpServletRequest();
        patientReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient);

        // Attempt booking a past slot: 00:00 - 00:30 on today (since nowTime is afternoon/evening, 00:00 is definitely past)
        if (nowTime.isAfter(LocalTime.of(0, 30))) {
            BookAppointmentRequest pastReq = new BookAppointmentRequest(
                    providerId, today.toString(), "00:00", "00:30", "Testing past slot rejection"
            );
            ResponseEntity<?> pastBookingResp = appointmentController.bookAppointment(patientReq, pastReq);
            assertEquals(HttpStatus.BAD_REQUEST, pastBookingResp.getStatusCode());
            ErrorResponse error = (ErrorResponse) pastBookingResp.getBody();
            assertNotNull(error);
            assertTrue(error.getMessage().contains("Cannot book a past appointment slot") || error.getMessage().contains("passed"));
        }

        // Attempt booking a past date: yesterday
        BookAppointmentRequest yesterdayReq = new BookAppointmentRequest(
                providerId, today.minusDays(1).toString(), "10:00", "10:30", "Testing past date rejection"
        );
        ResponseEntity<?> yesterdayResp = appointmentController.bookAppointment(patientReq, yesterdayReq);
        assertEquals(HttpStatus.BAD_REQUEST, yesterdayResp.getStatusCode());
    }

    @Test
    void testSlotDurationAndIntervalValidation() {
        // Setup Provider with 30-min slots starting at 09:00 on Fridays
        User providerUser = new User();
        providerUser.setName("Dr. Interval Doctor");
        providerUser.setEmail("dr_interval_" + System.currentTimeMillis() + "@example.com");
        providerUser.setPasswordHash(passwordEncoder.encode("pass"));
        providerUser.setRole(Role.PROVIDER);
        providerUser = userRepository.save(providerUser);

        MockHttpServletRequest providerReq = new MockHttpServletRequest();
        providerReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, providerUser);

        ProviderUpdateRequest schedReq = new ProviderUpdateRequest(
                "Dr. Interval Doctor", "Pediatrics", true,
                Arrays.asList("FRIDAY"), "09:00", "12:00", 30
        );
        providerController.updateMyProfile(providerReq, schedReq);
        Long providerId = ((ProviderProfileDTO) providerController.getMyProfile(providerReq).getBody()).getId();

        LocalDate nextFri = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));

        User patient = new User();
        patient.setName("Patient Interval Test");
        patient.setEmail("patient_interval_" + System.currentTimeMillis() + "@example.com");
        patient.setPasswordHash(passwordEncoder.encode("pass"));
        patient.setRole(Role.CUSTOMER);
        patient = userRepository.save(patient);

        MockHttpServletRequest patientReq = new MockHttpServletRequest();
        patientReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient);

        // 1. Invalid duration: 15 minutes instead of 30
        BookAppointmentRequest invalidDurationReq = new BookAppointmentRequest(
                providerId, nextFri.toString(), "09:00", "09:15", "Wrong duration"
        );
        ResponseEntity<?> durResp = appointmentController.bookAppointment(patientReq, invalidDurationReq);
        assertEquals(HttpStatus.BAD_REQUEST, durResp.getStatusCode());
        ErrorResponse durError = (ErrorResponse) durResp.getBody();
        assertTrue(durError.getMessage().contains("duration"));

        // 2. Misaligned start time: 09:17 instead of 09:00 or 09:30
        BookAppointmentRequest misalignedReq = new BookAppointmentRequest(
                providerId, nextFri.toString(), "09:17", "09:47", "Misaligned slot"
        );
        ResponseEntity<?> misalignedResp = appointmentController.bookAppointment(patientReq, misalignedReq);
        assertEquals(HttpStatus.BAD_REQUEST, misalignedResp.getStatusCode());
        ErrorResponse misalignedError = (ErrorResponse) misalignedResp.getBody();
        assertTrue(misalignedError.getMessage().contains("align"));

        // 3. Valid slot: 09:00 - 09:30 on Friday -> succeeds
        BookAppointmentRequest validReq = new BookAppointmentRequest(
                providerId, nextFri.toString(), "09:00", "09:30", "Valid slot booking"
        );
        ResponseEntity<?> validResp = appointmentController.bookAppointment(patientReq, validReq);
        assertEquals(HttpStatus.CREATED, validResp.getStatusCode());
        AppointmentDTO validAppt = (AppointmentDTO) validResp.getBody();
        assertNotNull(validAppt.getTokenNumber());
        assertEquals("T-001", validAppt.getTokenDisplay());
    }

    @Test
    void testProviderAppointmentStatusTransitionsAndCrossDoctorAccessPrevention() {
        // 1. Setup Doctor A
        User docA = new User();
        docA.setName("Dr. Alice Specialist");
        docA.setEmail("dr_alice_" + System.currentTimeMillis() + "@example.com");
        docA.setPasswordHash(passwordEncoder.encode("pass"));
        docA.setRole(Role.PROVIDER);
        docA = userRepository.save(docA);

        MockHttpServletRequest docAReq = new MockHttpServletRequest();
        docAReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, docA);

        ProviderUpdateRequest docASched = new ProviderUpdateRequest(
                "Dr. Alice Specialist", "Dermatology", true,
                Arrays.asList("MONDAY"), "10:00", "13:00", 30
        );
        providerController.updateMyProfile(docAReq, docASched);
        Long docAProviderId = ((ProviderProfileDTO) providerController.getMyProfile(docAReq).getBody()).getId();

        // 2. Setup Doctor B
        User docB = new User();
        docB.setName("Dr. Bob Colleague");
        docB.setEmail("dr_bob_" + System.currentTimeMillis() + "@example.com");
        docB.setPasswordHash(passwordEncoder.encode("pass"));
        docB.setRole(Role.PROVIDER);
        docB = userRepository.save(docB);

        MockHttpServletRequest docBReq = new MockHttpServletRequest();
        docBReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, docB);

        // 3. Customer books an appointment with Doctor A
        User patient = new User();
        patient.setName("Patient Charlie");
        patient.setEmail("patient_charlie_" + System.currentTimeMillis() + "@example.com");
        patient.setPasswordHash(passwordEncoder.encode("pass"));
        patient.setRole(Role.CUSTOMER);
        patient = userRepository.save(patient);

        MockHttpServletRequest patientReq = new MockHttpServletRequest();
        patientReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, patient);

        LocalDate nextMon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                docAProviderId, nextMon.toString(), "10:00", "10:30", "Skin consultation"
        );
        ResponseEntity<?> bookResp = appointmentController.bookAppointment(patientReq, bookReq);
        assertEquals(HttpStatus.CREATED, bookResp.getStatusCode());
        AppointmentDTO appt = (AppointmentDTO) bookResp.getBody();
        assertEquals("PENDING", appt.getStatus());

        // 4. Doctor B attempts to confirm Doctor A's appointment -> FORBIDDEN (Cross-provider isolation)
        ResponseEntity<?> badConfirmResp = appointmentController.confirmAppointment(docBReq, appt.getId());
        assertEquals(HttpStatus.FORBIDDEN, badConfirmResp.getStatusCode());

        // 5. Doctor A confirms their own appointment -> SUCCEEDS
        ResponseEntity<?> goodConfirmResp = appointmentController.confirmAppointment(docAReq, appt.getId());
        assertEquals(HttpStatus.OK, goodConfirmResp.getStatusCode());
        AppointmentDTO confirmedAppt = (AppointmentDTO) goodConfirmResp.getBody();
        assertEquals("CONFIRMED", confirmedAppt.getStatus());

        // 6. Doctor B attempts to complete Doctor A's appointment -> FORBIDDEN
        ResponseEntity<?> badCompleteResp = appointmentController.completeAppointment(docBReq, appt.getId());
        assertEquals(HttpStatus.FORBIDDEN, badCompleteResp.getStatusCode());

        // 7. Doctor A completes their appointment -> SUCCEEDS and token status synchronizes
        ResponseEntity<?> goodCompleteResp = appointmentController.completeAppointment(docAReq, appt.getId());
        assertEquals(HttpStatus.OK, goodCompleteResp.getStatusCode());
        AppointmentDTO completedAppt = (AppointmentDTO) goodCompleteResp.getBody();
        assertEquals("COMPLETED", completedAppt.getStatus());
        assertEquals("COMPLETED", completedAppt.getTokenStatus());

        // 8. Doctor B attempts to view Doctor A's queue tokens -> Doctor B gets their own (empty) queue
        ResponseEntity<?> docBQueueResp = queueController.getProviderQueue(docBReq, nextMon);
        assertEquals(HttpStatus.OK, docBQueueResp.getStatusCode());
        List<QueueTokenDTO> docBTokens = (List<QueueTokenDTO>) docBQueueResp.getBody();
        assertTrue(docBTokens.isEmpty(), "Doctor B's queue must not expose Doctor A's patient tokens");

        // 9. Doctor A views their queue -> Doctor A sees the completed token
        ResponseEntity<?> docAQueueResp = queueController.getProviderQueue(docAReq, nextMon);
        assertEquals(HttpStatus.OK, docAQueueResp.getStatusCode());
        List<QueueTokenDTO> docATokens = (List<QueueTokenDTO>) docAQueueResp.getBody();
        assertEquals(1, docATokens.size());
        assertEquals("COMPLETED", docATokens.get(0).getStatus());
    }

    @Test
    void testPhase6AdminAnalyticsMetricsAndManagement() {
        // 1. Create Admin user
        User admin = new User();
        admin.setName("Admin Sarah");
        admin.setEmail("admin_sarah_" + System.currentTimeMillis() + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode("pass"));
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        MockHttpServletRequest adminReq = new MockHttpServletRequest();
        adminReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, admin);

        // 2. Test Admin Stats wait time metrics
        ResponseEntity<?> statsResp = adminController.getAdminStats(adminReq);
        assertEquals(HttpStatus.OK, statsResp.getStatusCode());
        AdminStatsDTO stats = (AdminStatsDTO) statsResp.getBody();
        assertNotNull(stats.getAverageWaitDisplay());
        assertNotNull(stats.getAverageWaitExplanation());
        assertTrue(stats.getAverageWaitExplanation().contains("created_at"));

        // 3. Test Admin Analytics endpoint
        ResponseEntity<?> analyticsResp = adminController.getAdminAnalytics(adminReq, null, null);
        assertEquals(HttpStatus.OK, analyticsResp.getStatusCode());
        AdminAnalyticsDTO analytics = (AdminAnalyticsDTO) analyticsResp.getBody();
        assertNotNull(analytics.getDailyBookings());
        assertNotNull(analytics.getMonthlyBookings());
        assertNotNull(analytics.getQueueTrends());
        assertNotNull(analytics.getAverageWaitDisplay());

        // 4. Test User Role Update
        User targetUser = new User();
        targetUser.setName("Role Target");
        targetUser.setEmail("target_" + System.currentTimeMillis() + "@example.com");
        targetUser.setPasswordHash(passwordEncoder.encode("pass"));
        targetUser.setRole(Role.CUSTOMER);
        targetUser = userRepository.save(targetUser);

        java.util.Map<String, String> roleReq = java.util.Map.of("role", "PROVIDER");
        ResponseEntity<?> updateRoleResp = adminController.updateUserRole(adminReq, targetUser.getId(), roleReq);
        assertEquals(HttpStatus.OK, updateRoleResp.getStatusCode());
        User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(Role.PROVIDER, updatedUser.getRole());

        // 5. Test Provider Active/Deactive Toggle
        User provUser = new User();
        provUser.setName("Dr. Toggle Target");
        provUser.setEmail("dr_toggle_" + System.currentTimeMillis() + "@example.com");
        provUser.setPasswordHash(passwordEncoder.encode("pass"));
        provUser.setRole(Role.PROVIDER);
        provUser = userRepository.save(provUser);

        MockHttpServletRequest provReq = new MockHttpServletRequest();
        provReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, provUser);
        ProviderUpdateRequest pReq = new ProviderUpdateRequest("Dr. Toggle", "Cardiology", true,
                Arrays.asList("MONDAY", "WEDNESDAY"), "09:00", "17:00", 30);
        ResponseEntity<?> profileResp = providerController.updateMyProfile(provReq, pReq);
        ProviderProfileDTO profile = (ProviderProfileDTO) profileResp.getBody();
        Long providerId = profile.getId();
        assertTrue(profile.isActive());

        // Deactivate via Admin endpoint
        ResponseEntity<?> toggleResp = adminController.toggleProviderActive(adminReq, providerId);
        assertEquals(HttpStatus.OK, toggleResp.getStatusCode());
        java.util.Optional<?> optDeactivated = (java.util.Optional<?>) toggleResp.getBody();
        assertTrue(optDeactivated.isPresent());
        ProviderProfileDTO deactivatedProv = (ProviderProfileDTO) optDeactivated.get();
        assertFalse(deactivatedProv.isActive(), "Provider should now be deactivated");

        // Reactivate via Admin endpoint
        ResponseEntity<?> reactivateResp = adminController.toggleProviderActive(adminReq, providerId);
        assertEquals(HttpStatus.OK, reactivateResp.getStatusCode());
        java.util.Optional<?> optReactivated = (java.util.Optional<?>) reactivateResp.getBody();
        assertTrue(optReactivated.isPresent());
        ProviderProfileDTO reactivatedProv = (ProviderProfileDTO) optReactivated.get();
        assertTrue(reactivatedProv.isActive(), "Provider should now be reactivated");

        // 6. Test Non-Admin cannot access Analytics or update roles
        User nonAdmin = new User();
        nonAdmin.setName("Intruder");
        nonAdmin.setEmail("intruder_" + System.currentTimeMillis() + "@example.com");
        nonAdmin.setPasswordHash(passwordEncoder.encode("pass"));
        nonAdmin.setRole(Role.CUSTOMER);
        nonAdmin = userRepository.save(nonAdmin);

        MockHttpServletRequest nonAdminReq = new MockHttpServletRequest();
        nonAdminReq.setAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, nonAdmin);

        assertEquals(HttpStatus.FORBIDDEN, adminController.getAdminAnalytics(nonAdminReq, null, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.updateUserRole(nonAdminReq, targetUser.getId(), roleReq).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, adminController.toggleProviderActive(nonAdminReq, providerId).getStatusCode());
    }
}
