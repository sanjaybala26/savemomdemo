# Phase 3 Codebase Explanation: Service Provider Profiles & Appointment Availability

This document explains every file, class, method, database migration, and design decision implemented in **Phase 3** of CareQueue. It is written in simple, clear language to help beginners understand the scheduling architecture and demo the application with confidence.

---

## 1. Overview of Phase 3

In Phase 1, we created the foundational Spring Boot + React project with MySQL connectivity and Flyway migrations.
In Phase 2, we built authentication, password hashing with BCrypt, stateless JWT issuance, and role-based access control (`CUSTOMER`, `PROVIDER`, `ADMIN`).

In **Phase 3**, we introduce **Healthcare Provider Profiles and Working Schedules**:
1. **Provider Profiles Table (`provider_profiles`)**: Stores public practice details (display name, medical specialty/description, active listing status) linked 1-to-1 to a user account with the `PROVIDER` role.
2. **Provider Availability Table (`provider_availability`)**: Stores clinic operating hours (`open_time`, `close_time`), working days of the week (e.g. `MONDAY,WEDNESDAY,FRIDAY`), and appointment session duration (e.g. `30` minutes).
3. **Dynamic Slot Generation Engine**: The backend derives available time slots on the fly for any requested calendar date without storing thousands of empty slot rows in the database.
4. **Role Security Enforced**:
   - Patients / Customers (`CUSTOMER`) can browse active doctors and view available slots, but are strictly **forbidden (HTTP 403)** from accessing provider schedule editing endpoints.
   - Healthcare Providers (`PROVIDER`) and Admins (`ADMIN`) can view and update their own profiles and operating hours.
5. **Time Zone Strategy**: Operating hours are maintained in standard 24-hour clinic local time (`HH:mm`), while creation and update audit trails use UTC instants (`Instant`).
6. **Frontend User Experience**:
   - Customer Directory with interactive doctor cards.
   - Date picker to inspect slots on any upcoming date.
   - Clear feedback when a provider is closed on a given weekday.
   - Interactive slot selection pills.
   - Provider Management Portal for doctors to configure their working days, shift hours, and session length.

---

## 2. Step-by-Step Data Flows

### Flow A: Customer Browsing Providers & Available Slots (Public)
```
[Patient Browser]
       │
       ▼ 1. Opens "Doctors & Available Slots" tab
[React App.jsx]
       │
       ▼ 2. Calls fetchActiveProviders() in api.js
[HTTP GET /api/providers]
       │
       ▼ 3. PublicProviderController.getActiveProviders()
       │    Calls ProviderService.getActiveProviders()
       │    Queries provider_profiles where is_active = true
       ▼
[Response: HTTP 200 OK] -> Returns List<ProviderProfileDTO>
       │
       ▼ 4. Patient clicks a doctor card (e.g. Dr. Sarah Jenkins)
       │    Selects a calendar date (e.g. 2026-11-11)
       ▼ 5. Calls fetchProviderSlots(providerId, "2026-11-11") in api.js
[HTTP GET /api/providers/1/slots?date=2026-11-11]
       │
       ▼ 6. PublicProviderController.getAvailableSlots(1, "2026-11-11")
       │    ProviderService.getSlotsForDate(1, "2026-11-11"):
       │    a. Parses date into LocalDate -> extracts DayOfWeek (e.g. WEDNESDAY).
       │    b. Checks if provider operates on WEDNESDAY.
       │    c. If closed: returns { isWorkingDay: false, slots: [] }.
       │    d. If open: steps through openTime (10:00) to closeTime (12:00) by 30 mins:
       │       - Slot 1: 10:00 - 10:30 ("10:00 AM - 10:30 AM")
       │       - Slot 2: 10:30 - 11:00 ("10:30 AM - 11:00 AM")
       │       - Slot 3: 11:00 - 11:30 ("11:00 AM - 11:30 AM")
       │       - Slot 4: 11:30 - 12:00 ("11:30 AM - 12:00 PM")
       ▼
[Response: HTTP 200 OK] -> Returns ProviderAvailabilityResponse with 4 slots
       │
       ▼ 7. React renders interactive time slot buttons for the patient.
```

---

### Flow B: Provider Updating Practice Hours (Protected)
```
[Doctor Browser (Logged in as PROVIDER)]
       │
       ▼ 1. Enters "My Provider Schedule" tab
[React App.jsx]
       │
       ▼ 2. Calls updateMyProviderProfile(formData) in api.js
[HTTP PUT /api/provider/me]
       │ Includes Header: Authorization: Bearer <jwt_token>
       │
       ▼ 3. JwtAuthenticationInterceptor validates token
       │    Attaches User object to request attributes
       │
       ▼ 4. ProviderController.updateMyProfile()
       │    - Checks currentUser.getRole():
       │      If role is CUSTOMER -> returns HTTP 403 Forbidden.
       │      If role is PROVIDER or ADMIN -> allows execution.
       │
       ▼ 5. ProviderService.updateProfile()
       │    - Validates: openTime < closeTime (e.g. 09:00 < 17:00).
       │    - Validates: slot duration between 10 and 240 minutes.
       │    - Validates: at least one valid weekday selected.
       │    - Updates `provider_profiles` and `provider_availability` tables.
       ▼
[Response: HTTP 200 OK] -> Returns updated ProviderProfileDTO
       │
       ▼ 6. React shows green success alert and refreshes directory.
```

---

## 3. Database Schema & Flyway Migration

### Migration File: `backend/src/main/resources/db/migration/V3__create_provider_and_availability_tables.sql`
Creates the two core relational tables:

```sql
CREATE TABLE IF NOT EXISTS provider_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    service_description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS provider_availability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL UNIQUE,
    working_days VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    open_time VARCHAR(10) NOT NULL DEFAULT '09:00',
    close_time VARCHAR(10) NOT NULL DEFAULT '17:00',
    slot_duration_minutes INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_availability_provider FOREIGN KEY (provider_id) REFERENCES provider_profiles(id) ON DELETE CASCADE
);
```

#### Why this structure?
- `user_id BIGINT UNIQUE`: Guarantees a one-to-one relationship between a login account and a clinical profile.
- `provider_id BIGINT UNIQUE`: Each provider has exactly one operating schedule configuration.
- `ON DELETE CASCADE`: If a provider user is removed, their profile and schedules are cleanly cleaned up automatically.
- `working_days VARCHAR(100)`: Stores comma-separated weekday names (`MONDAY,TUESDAY...`). Easy to inspect in MySQL CLI, simple to parse, and avoids complex join tables for a beginner codebase.

---

## 4. Backend Components

### 4.1 Models (Entities)
Located in `com.savemom.appointmentqueue.model`:

- **`ProviderProfile.java`**: Represents a doctor's public directory listing. Written without Lombok for maximum visibility.
  - Fields: `id`, `userId`, `displayName`, `serviceDescription`, `isActive`, `createdAt`, `updatedAt`.
  - Constructors: default constructor, convenience constructor, and full constructor.
- **`ProviderAvailability.java`**: Represents operating hours and shift intervals.
  - Fields: `id`, `providerId`, `workingDays`, `openTime`, `closeTime`, `slotDurationMinutes`, `createdAt`, `updatedAt`.
  - Helper method `getWorkingDaysAsList()`: Splits comma-separated strings into a clean `List<String>`.

### 4.2 Repositories
Located in `com.savemom.appointmentqueue.repository`:

- **`ProviderProfileRepository.java`**:
  - `findByUserId(Long userId)`: Looks up a profile linked to an authenticated user.
  - `findById(Long id)`: Looks up a profile by primary key.
  - `findAllActive()`: Returns all providers where `is_active = true` sorted alphabetically.
  - `save(ProviderProfile profile)`: Uses `JdbcTemplate` with `KeyHolder` to handle both INSERT and UPDATE operations safely across MySQL and H2.
- **`ProviderAvailabilityRepository.java`**:
  - `findByProviderId(Long providerId)`: Looks up schedule configuration for a provider.
  - `save(ProviderAvailability availability)`: Handles insert or update.

### 4.3 Data Transfer Objects (DTOs)
Located in `com.savemom.appointmentqueue.dto`:

- **`ProviderProfileDTO.java`**: Unified DTO merging profile fields and availability fields. Ensures raw database entities with foreign keys or timestamps are never directly exposed to the frontend.
- **`TimeSlotDTO.java`**: Represents a bookable appointment interval with `startTime` ("10:00"), `endTime` ("10:30"), and `formatted` ("10:00 AM - 10:30 AM").
- **`ProviderUpdateRequest.java`**: Request body payload containing fields a doctor can modify.
- **`ProviderAvailabilityResponse.java`**: Response containing date metadata, `isWorkingDay` boolean, and the computed list of `TimeSlotDTO` items.

### 4.4 Service Layer: `ProviderService.java`
Located in `com.savemom.appointmentqueue.service`:

- **`getOrCreateProfileForUser(User user)`**: Automatically provides a default profile and 9-to-5 Monday–Friday schedule if a newly promoted provider user accesses their settings for the first time.
- **`updateProfile(User user, ProviderUpdateRequest request)`**:
  - Validates `displayName` length >= 2.
  - Parses times into `LocalTime` and verifies `openTime.isBefore(closeTime)`.
  - Verifies slot duration is within safe clinical bounds (10 to 240 minutes) and does not exceed the total working window.
  - Validates that selected days match valid Java `DayOfWeek` values.
  - Saves updates to both profile and availability tables.
- **`getActiveProviders()`**: Assembles active providers into public DTOs.
- **`getSlotsForDate(Long providerId, String dateString)`**:
  - Parses the calendar date into `java.time.LocalDate`.
  - Inspects `date.getDayOfWeek()`.
  - If the weekday is not in `workingDays`: returns `isWorkingDay = false` with an empty slots list and a friendly explanatory message.
  - If open: loops `LocalTime current = openTime` up to `closeTime` advancing by `slotDurationMinutes` on each iteration, formatting each slot for display.

### 4.5 Controllers & Role Restrictions

- **`ProviderController.java` (`/api/provider`)**:
  - Guarded by `JwtAuthenticationInterceptor`.
  - Restricts `/api/provider/me` (both GET and PUT):
    ```java
    if (currentUser.getRole() != Role.PROVIDER && currentUser.getRole() != Role.ADMIN) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "Forbidden", "Access denied. Only healthcare providers and administrators can manage provider settings."));
    }
    ```
  - This guarantees patients (`CUSTOMER`) can never tamper with or alter doctor schedules.

- **`PublicProviderController.java` (`/api/providers`)**:
  - Publicly accessible without JWT credentials.
  - `GET /api/providers`: Returns all active doctors.
  - `GET /api/providers/{id}`: Returns single doctor information.
  - `GET /api/providers/{id}/slots?date=YYYY-MM-DD`: Returns calculated slots for any date.

- **`CorsConfig.java`**:
  - Registered `/api/provider/**` with `jwtAuthenticationInterceptor` alongside `/api/auth/me`.

---

## 5. Time Zone Strategy

A common point of confusion in scheduling apps is time zone handling. CareQueue adopts a standard, resilient architecture:

1. **Clinic Operating Hours (`open_time`, `close_time`)**:
   - Stored as local 24-hour time strings (`"09:00"`, `"17:00"`).
   - *Why?* A doctor's office opens at 9:00 AM local clinic time regardless of daylight saving shifts or the patient's browser time zone. Storing standard `HH:mm` eliminates time drift.
2. **Calendar Dates (`date`)**:
   - Handled in standard ISO-8601 format (`YYYY-MM-DD`).
   - Slot generation operates on `LocalDate` and `LocalTime`.
3. **Database Audit Timestamps (`created_at`, `updated_at`)**:
   - Stored as UTC `Instant` values (`TIMESTAMP` in MySQL) to maintain reliable auditing across server regions.

---

## 6. Frontend Architecture

### 6.1 API Client (`frontend/src/services/api.js`)
Four new functions:
- `fetchActiveProviders()`: calls `GET /api/providers`.
- `fetchProviderSlots(providerId, date)`: calls `GET /api/providers/{id}/slots?date=YYYY-MM-DD`.
- `fetchMyProviderProfile()`: calls `GET /api/provider/me` passing `Authorization: Bearer <token>`.
- `updateMyProviderProfile(data)`: calls `PUT /api/provider/me` passing `Authorization: Bearer <token>` and JSON body.

### 6.2 User Interface (`frontend/src/App.jsx` & `App.css`)
- **Navigation Tabs**:
  - `Doctors & Available Slots`: Open to everyone.
  - `My Provider Schedule`: Visible only when logged in as `PROVIDER` or `ADMIN`.
  - `Account & Login`: Displays user profile, role badge, or login/register forms.
- **Provider Directory**:
  - Displays doctor cards with avatar, name, specialty, operating days, and hours.
  - Clicking a doctor selects them and reveals the slot viewer.
- **Slot Picker**:
  - HTML5 Date Picker (`type="date"`, min date = today).
  - Automatically fetches slots when date or selected doctor changes.
  - Shows clear states:
    - *Loading state*: Spinning loader indicator.
    - *Closed day*: Informative blue alert stating the doctor is closed on that day of the week.
    - *Open day*: Interactive grid of time slot badges (`09:00 AM - 09:30 AM`).
    - *Selection indicator*: Highlights selected slot with a note that booking will occur in Phase 4.
- **Provider Settings Form**:
  - Interactive weekday toggle pills (Mon through Sun).
  - Time pickers for open and close times.
  - Dropdown for session duration (15, 20, 30, 45, 60 minutes).
  - Checkbox toggle for active listing status.
  - Instant synchronization with the public directory upon saving.

---

## 7. Creating Local Provider & Admin Demo Accounts

By design, public registration at `POST /api/auth/register` strictly assigns the `CUSTOMER` role to prevent unauthorized privilege escalation.

To create a `PROVIDER` or `ADMIN` account for local testing, follow this simple development procedure:

### Step 1: Register an account in the web UI
1. Navigate to the **Account & Login** tab in the browser.
2. Click **Register**.
3. Create an account, for example:
   - Name: `Dr. Sarah Jenkins`
   - Email: `sarah.jenkins@clinic.org`
   - Password: `doctorPass123`
4. The account is created with role `CUSTOMER`.

### Step 2: Elevate the account role in MySQL
Open PowerShell or your MySQL CLI and run:

```sql
-- Connect to the database
USE carequeue;

-- Promote the account to PROVIDER
UPDATE users 
SET role = 'PROVIDER' 
WHERE email = 'sarah.jenkins@clinic.org';

-- (Optional) If creating an administrator:
-- UPDATE users SET role = 'ADMIN' WHERE email = 'admin@clinic.org';
```

### Step 3: Log in as Provider
1. In the web UI, log out and log back in with `sarah.jenkins@clinic.org`.
2. Notice the green **PROVIDER** badge.
3. The **"⚙️ My Provider Schedule"** tab is now unlocked!
4. You can edit your clinic working hours, select working days, and save.
5. Switch to the **"🏥 Doctors & Available Slots"** tab to immediately see your profile and calculated appointment slots.

---

## 8. Verification & Test Suite

All Phase 1, Phase 2, and Phase 3 capabilities are covered by automated Spring Boot tests in `AppointmentQueueApplicationTests.java`:
1. `contextLoads`: Verifies all controllers, repositories, and security beans load.
2. `healthEndpointReturnsOk`: Verifies `GET /api/health` returns UP.
3. `testRegistrationFlowAlwaysCreatesCustomer`: Verifies public registration enforces `CUSTOMER` role and BCrypt encryption.
4. `testDuplicateRegistrationFails`: Confirms HTTP 409 Conflict on existing email.
5. `testLoginWithValidAndInvalidCredentials`: Verifies password verification and rejection of invalid credentials.
6. `testProtectedMeEndpointWithJwt`: Verifies JWT issuance and interceptor enforcement.
7. `testCustomerCannotAccessProviderManagementEndpoints`: **Verifies that patients receive HTTP 403 Forbidden when attempting to access or modify provider settings.**
8. `testProviderProfileManagementAndSlotDerivation`: **Verifies end-to-end provider schedule configuration and slot calculation across open and closed weekdays.**
9. `testProviderScheduleValidationRejectsInvalidHours`: **Verifies validation error on inverted working hours or impossible slot durations.**

To run the automated tests offline:
```powershell
Set-Location backend
& "D:\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" test -o
```
Result: **9 tests run, 0 failures, 0 errors, BUILD SUCCESS.**
