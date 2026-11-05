# Phase 4 Codebase Explanation: Appointment Booking, Cancellation, & Double-Booking Prevention

This document explains every file, class, method, database migration, and design decision implemented in **Phase 4** of CareQueue. It is written in simple, clear language to help beginners explain the booking architecture and demo the system with confidence.

---

## 1. Overview of Phase 4

In Phase 3, we built healthcare provider profiles, working schedules, and dynamic slot derivation.
In **Phase 4**, we introduce **Appointment Booking and Cancellation**:
1. **Appointments Table (`appointments`)**: Records appointments linking an authenticated patient (`customer_id`), doctor (`provider_id`), calendar date, and time slot.
2. **Lifecycle Statuses**: Every appointment follows a structured lifecycle: `PENDING`, `CONFIRMED`, `COMPLETED`, and `CANCELLED`. In this phase, all newly booked appointments are created with status **`PENDING`**.
3. **Double-Booking Prevention**:
   - Multi-tier defense using database-backed unique locks and `@Transactional` isolation.
   - Prevents race conditions when two patients simultaneously click to book the same slot.
4. **Ownership & Access Control**:
   - Customers can only view their own booked appointments.
   - Customers can only cancel their own appointments (strictly blocking cross-patient tampering with HTTP 403 Forbidden).
5. **Slot Lock Release on Cancellation**:
   - When an appointment is cancelled, its active slot lock is cleared, immediately releasing the slot so other patients can book it.
6. **Audit History Table (`appointment_status_history`)**:
   - Immutable log capturing every status change (e.g., initial creation to `PENDING`, or `PENDING` to `CANCELLED`) with user ID, timestamp, and optional reason.
7. **Frontend Experience**:
   - Slot selection with an interactive booking confirmation card.
   - Real-time slot status (`Available` vs `Booked`).
   - "My Appointments" dashboard showing upcoming/past visits, status badges, cancel button, and an expandable audit trail.

---

## 2. Step-by-Step Data Flows

### Flow A: Customer Booking an Appointment (`POST /api/appointments`)
```
[Patient Browser]
       │
       ▼ 1. Patient selects Dr. Sarah Jenkins, picks date (e.g. 2026-11-06), and clicks 10:00 AM slot
[React App.jsx]
       │
       ▼ 2. Form displays: Date, Doctor, Time, and optional "Reason for visit"
       │    Patient clicks "Confirm & Book Appointment"
       ▼ 3. Calls bookAppointment() in api.js
[HTTP POST /api/appointments]
       │ Header: Authorization: Bearer <customer_jwt_token>
       │ Body: { providerId: 1, appointmentDate: "2026-11-06", startTime: "10:00", endTime: "10:30", notes: "Prenatal checkup" }
       │
       ▼ 4. JwtAuthenticationInterceptor validates token
       │    Attaches authenticated User to request attribute
       │
       ▼ 5. AppointmentController.bookAppointment()
       │    Delegates to AppointmentService.bookAppointment(user, request)
       │
       ▼ 6. AppointmentService (@Transactional):
       │    a. Validates date is not in the past.
       │    b. Checks provider exists and is active.
       │    c. Verifies provider's workingDays includes this weekday (FRIDAY).
       │    d. Verifies slot is within operating hours (10:00 >= 10:00 and 10:30 <= 14:00).
       │    e. Tier 1 Check: Queries appointmentRepository.findActiveByProviderAndSlot().
       │       If an active booking exists -> throws IllegalStateException (HTTP 409 Conflict).
       │    f. Tier 2 Guard: Sets active_slot_lock = "1_2026-11-06_10:00".
       │    g. Inserts record into `appointments` with status = PENDING.
       │       If concurrent race condition occurs, MySQL unique constraint `uq_active_slot`
       │       triggers DuplicateKeyException -> caught and returns HTTP 409 Conflict.
       │    h. Inserts record into `appointment_status_history`:
       │       (fromStatus: null, toStatus: PENDING, changedBy: customerId, reason: "Appointment booked by patient").
       ▼
[Response: HTTP 201 Created] -> Returns AppointmentDTO
       │
       ▼ 7. React displays green success banner, marks slot as "Booked", and refreshes appointments list.
```

---

### Flow B: Customer Cancelling an Appointment (`PUT /api/appointments/{id}/cancel`)
```
[Patient Browser]
       │
       ▼ 1. Patient views "My Appointments" tab and clicks "Cancel Appointment"
[React App.jsx]
       │
       ▼ 2. Prompts patient for cancellation reason (e.g. "Schedule conflict")
       │    Calls cancelAppointment(apptId, reason) in api.js
[HTTP PUT /api/appointments/1/cancel]
       │ Header: Authorization: Bearer <customer_jwt_token>
       │ Body: { reason: "Schedule conflict" }
       │
       ▼ 3. JwtAuthenticationInterceptor validates token
       │
       ▼ 4. AppointmentController.cancelAppointment()
       │    Delegates to AppointmentService.cancelAppointment(user, id, request)
       │
       ▼ 5. AppointmentService (@Transactional):
       │    a. Finds appointment by ID (or returns HTTP 404).
       │    b. Ownership Check:
       │       if (appt.getCustomerId() != user.getId() && user.role != ADMIN)
       │         -> throws SecurityException (HTTP 403 Forbidden).
       │    c. Status Validation:
       │       If already CANCELLED or COMPLETED -> returns HTTP 400 Bad Request.
       │    d. Updates appointment:
       │       status = CANCELLED
       │       active_slot_lock = NULL  <-- Crucial: releases unique database lock!
       │    e. Inserts record into `appointment_status_history`:
       │       (fromStatus: PENDING, toStatus: CANCELLED, changedBy: customerId, reason: "Schedule conflict").
       ▼
[Response: HTTP 200 OK] -> Returns updated AppointmentDTO (status: CANCELLED)
       │
       ▼ 6. React updates appointment status badge to gray CANCELLED,
            and the 10:00 AM slot becomes available again for other patients to book!
```

---

## 3. Database Schema & Flyway Migration

### Migration File: `backend/src/main/resources/db/migration/V4__create_appointments_and_history_tables.sql`

```sql
CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time VARCHAR(10) NOT NULL,
    end_time VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    active_slot_lock VARCHAR(80) NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS appointment_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    reason_or_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Why this design prevents double-booking:
- `active_slot_lock VARCHAR(80) NULL UNIQUE`:
  - When an appointment is booked, `active_slot_lock` is set to `"providerId_YYYY-MM-DD_HH:mm"` (e.g. `"1_2026-11-06_10:00"`).
  - The `UNIQUE` constraint guarantees that the database engine itself will reject any second attempt to insert or hold that same key.
  - When an appointment is cancelled, `active_slot_lock` is reset to `NULL`.
  - In SQL standard, MySQL, and H2, multiple rows are permitted to contain `NULL` in a `UNIQUE` column without conflict. This allows slots to be cancelled and immediately re-booked cleanly.

---

## 4. Backend Classes & Methods

### 4.1 Entities & Enums
- **`AppointmentStatus.java`**: Enum containing `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`.
- **`Appointment.java`**: Domain model with `id`, `customerId`, `providerId`, `appointmentDate`, `startTime`, `endTime`, `status`, `notes`, `activeSlotLock`, `createdAt`, `updatedAt`.
- **`AppointmentStatusHistory.java`**: Audit entity tracking `appointmentId`, `fromStatus`, `toStatus`, `changedByUserId`, `reasonOrNotes`, `createdAt`.

### 4.2 Repositories
- **`AppointmentRepository.java`**:
  - `findById(Long id)`: Looks up appointment.
  - `findByCustomerId(Long customerId)`: Returns patient's appointments sorted by date/time descending.
  - `findActiveByProviderAndDate(Long providerId, LocalDate date)`: Returns all non-cancelled appointments for a provider on a date.
  - `findActiveByProviderAndSlot(Long providerId, LocalDate date, String startTime)`: Checks if a slot is currently occupied.
  - `save(Appointment appt)`: Handles insert (with generated keys) or update.
- **`AppointmentStatusHistoryRepository.java`**:
  - `save(AppointmentStatusHistory history)`: Inserts audit history rows.
  - `findByAppointmentId(Long appointmentId)`: Returns chronological audit log.

### 4.3 DTOs
- **`BookAppointmentRequest.java`**: Holds `providerId`, `appointmentDate`, `startTime`, `endTime`, `notes`.
- **`CancelAppointmentRequest.java`**: Holds optional `reason`.
- **`AppointmentDTO.java`**: Enriched public DTO with `customerName`, `customerEmail`, `providerName`, `serviceDescription`, status, dates, and times.
- **`AppointmentHistoryDTO.java`**: DTO representing audit history records.
- **`TimeSlotDTO.java`**: Updated to include `isBooked` boolean so the frontend slot picker knows which slots are already taken.

### 4.4 Services
- **`AppointmentService.java`**:
  - `bookAppointment(User customer, BookAppointmentRequest request)`: Validates inputs, checks operating hours, applies 2-tier double-booking lock, inserts appointment as `PENDING`, and records initial history.
  - `getAppointmentsForCustomer(User customer)`: Returns all appointments for the patient.
  - `cancelAppointment(User customer, Long appointmentId, CancelAppointmentRequest request)`: Enforces ownership, sets status to `CANCELLED`, clears `activeSlotLock`, and logs reason in history.
  - `getAppointmentHistory(User customer, Long appointmentId)`: Returns chronological status transitions.
- **`ProviderService.java`**:
  - Updated `getSlotsForDate(Long providerId, String dateString)`: Queries active appointments for that day and automatically sets `isBooked: true` on occupied slots.

### 4.5 Controllers & Security
- **`AppointmentController.java` (`/api/appointments`)**:
  - Protected by `JwtAuthenticationInterceptor`.
  - `POST /api/appointments`: Books appointment. Returns HTTP 201 Created or HTTP 409 Conflict on double-booking.
  - `GET /api/appointments/my`: Returns logged-in patient's appointments.
  - `PUT /api/appointments/{id}/cancel`: Cancels patient's appointment. Returns HTTP 403 Forbidden if attempting to cancel someone else's booking.
  - `GET /api/appointments/{id}/history`: Returns audit transitions.

---

## 5. Frontend Architecture

### 5.1 API Client (`frontend/src/services/api.js`)
Four new functions:
- `bookAppointment(bookingData)`: calls `POST /api/appointments`.
- `fetchMyAppointments()`: calls `GET /api/appointments/my`.
- `cancelAppointment(appointmentId, reason)`: calls `PUT /api/appointments/{id}/cancel`.
- `fetchAppointmentHistory(appointmentId)`: calls `GET /api/appointments/{id}/history`.

### 5.2 User Interface (`frontend/src/App.jsx` & `App.css`)
- **Interactive Slot Booking**:
  - Unavailable slots are styled as disabled pills with strikethroughs and a red "Booked" badge.
  - Available slots can be clicked to highlight.
  - An interactive booking box appears with consultation notes input and a "Confirm & Book" button.
- **"My Appointments" Tab**:
  - Displays appointments with color-coded status badges:
    - Amber: `PENDING`
    - Green: `CONFIRMED`
    - Blue: `COMPLETED`
    - Gray: `CANCELLED`
  - "✕ Cancel Appointment" button calls `cancelAppointment()` and immediately updates the status.
  - "📜 View Audit History" expands an audit timeline showing status transitions, timestamps, and notes.

---

## 6. Automated Test Suite

All 12 backend unit and integration tests in `AppointmentQueueApplicationTests.java` passed:
1. `contextLoads`: Context loads all controllers and services.
2. `healthEndpointReturnsOk`: Health check returns UP.
3. `testRegistrationFlowAlwaysCreatesCustomer`: Public registration assigns `CUSTOMER`.
4. `testDuplicateRegistrationFails`: Conflict on duplicate email.
5. `testLoginWithValidAndInvalidCredentials`: Login verification.
6. `testProtectedMeEndpointWithJwt`: JWT authentication interceptor.
7. `testCustomerCannotAccessProviderManagementEndpoints`: Customer receives HTTP 403 on provider settings.
8. `testProviderProfileManagementAndSlotDerivation`: Provider working hours configuration and slot generation.
9. `testProviderScheduleValidationRejectsInvalidHours`: Validates opening/closing times.
10. `testAppointmentBookingAndDoubleBookingPrevention`: **Verifies booking with status PENDING, marks slot as booked, and rejects double-booking attempts with HTTP 409 CONFLICT.**
11. `testCustomerCannotCancelOtherCustomerAppointment`: **Verifies that Customer B receives HTTP 403 FORBIDDEN when attempting to cancel Customer A's appointment.**
12. `testAppointmentCancellationReleasesSlotForRebooking`: **Verifies that cancelling an appointment updates status to CANCELLED, logs audit history, and releases the slot lock so another patient can re-book.**

Run the test suite offline with:
```powershell
Set-Location backend
& "D:\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" test -o
```
Result: **12 tests run, 0 failures, 0 errors, BUILD SUCCESS.**
