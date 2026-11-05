# CareQueue - Phase 6 Explanation Document

## 1. Overview of Phase 6
Phase 6 completes the core appointment and queue lifecycle by introducing:
1. **Customer Live Queue Tracking**: A dedicated real-time tracker for authenticated patients to view their daily token, current status, live count of people ahead, and estimated wait time without manual page refreshes.
2. **Administrative Dashboard**: A centralized, strictly secured dashboard for administrators featuring overall system KPI counts (users, providers, appointments, waiting tokens, in-progress consultations, cancellations) and detailed tabular views of users, providers, appointments, and live queue status.
3. **End-to-End Verification & Documentation**: Comprehensive API documentation, demo scripts, setup instructions, and architecture guides across the entire stack.

---

## 2. Customer Queue Tracking Architecture

### A. Calculation Rule: People Ahead in Queue
A customer's position in line is computed dynamically by querying the database for active queue tokens ahead of their own token for the same healthcare provider on the same appointment date:
```sql
SELECT COUNT(q) FROM QueueToken q 
WHERE q.provider.id = :providerId 
  AND q.appointmentDate = :appointmentDate 
  AND q.tokenNumber < :tokenNumber 
  AND q.status IN ('WAITING', 'CALLED', 'IN_PROGRESS')
```
- **Why filter by status?**
  Tokens that have already been `COMPLETED`, `CANCELLED`, or `SKIPPED` are no longer physically waiting in line or in consultation. Counting only `WAITING`, `CALLED`, and `IN_PROGRESS` ensures the patient sees the exact number of individuals currently ahead of them.
- **Why strictly earlier token numbers?**
  Token numbers are generated sequentially (`1, 2, 3...`) per provider per day. Any active token with a strictly lower number arrived earlier in the schedule.

### B. Calculation Rule: Estimated Waiting Time
Waiting time estimation is derived from the number of people ahead multiplied by the healthcare provider's configured consultation slot duration:
$$\text{Estimated Wait (minutes)} = \text{People Ahead} \times \text{Slot Duration Minutes}$$
- **Immediate Attention Override**:
  If the patient's token is already `CALLED` (the doctor just called their number) or `IN_PROGRESS` (the consultation is underway), the estimated wait is immediately set to `0 minutes`.
- **Dynamic Slot Duration**:
  The duration is looked up directly from `ProviderAvailability` (e.g. 15, 30, or 45 minutes). If no availability profile is set, the system gracefully defaults to 15 minutes per patient.

### C. Client Polling (No WebSockets Overhead)
To keep the application lightweight, performant, and resilient within short project deadlines:
- The customer queue tracking view initiates a periodic HTTP polling timer (`setInterval`) that queries `GET /api/queue/my-token` every **10 seconds** while the user is on the Live Queue Tracker tab.
- The UI displays an animated pulsing green indicator and a human-readable "Last updated: HH:MM:SS" timestamp so patients know their queue information is live and fresh.
- When the user switches tabs or navigates away, React's `useEffect` cleanup hook immediately clears the interval timer (`clearInterval`), preventing memory leaks and unnecessary network calls.

### D. Security & Data Isolation
- A customer must never be permitted to inspect another customer's queue token or private appointment details.
- All customer queue endpoints (`/api/queue/my-token` and `/api/queue/my-tokens`) resolve the authenticated customer directly from the signed JWT security context (`User currentUser`).
- If an appointment ID is requested, `QueueService` explicitly verifies:
  `if (!token.getCustomer().getId().equals(currentUser.getId()))`
  If a customer attempts to query a token belonging to another user, the backend returns an immediate **HTTP 403 Forbidden** error.

---

## 3. Admin Dashboard Architecture

### A. High-Level System KPI Counts (`AdminStatsDTO`)
The admin dashboard provides instant situational awareness of clinic operations through 7 key metric counters:
1. **Total Registered Users**: Overall customer and staff accounts in `users`.
2. **Total Healthcare Providers**: Registered doctors and clinics in `provider_profiles`.
3. **Total Booked Appointments**: All appointments ever scheduled across all statuses.
4. **Currently Waiting Patients**: Real-time count of tokens currently in status `WAITING`.
5. **In-Progress Consultations**: Tokens currently in status `IN_PROGRESS`.
6. **Completed Consultations**: Appointments successfully finished (`COMPLETED`).
7. **Cancelled Appointments**: Appointments cancelled by patients or providers (`CANCELLED`).

### B. Admin Data Directories
Administrators can inspect four structured tabular directories directly from the dashboard:
1. **Queue Activity Table**: Live audit feed displaying Token ID, Token Code (`T-001`), Provider Name, Patient Name, Date, Status badge, Called At timestamp, and Completed At timestamp.
2. **User Management Table**: System user registry displaying User ID, Email address, Full Name, Role (`CUSTOMER`, `PROVIDER`, `ADMIN`), and Account Creation timestamp.
3. **Provider Directory**: Provider ID, Doctor Name, Specialization, Clinic Name, Slot Duration, and Active Working Days.
4. **All Appointments View**: Complete booking log showing Appointment ID, Patient, Doctor, Date, Time Slot, and Booking Status.

### C. Server-Side Authorization Enforcement
Hiding frontend navigation tabs or buttons is **not** sufficient security. The backend enforces strict role-based access control:
- Every endpoint under `/api/admin/**` invokes `verifyAdmin(currentUser)`:
  ```java
  private void verifyAdmin(User currentUser) {
      if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
          throw new SecurityException("Access denied: Administrator privileges required");
      }
  }
  ```
- Non-admin tokens (whether from customers or providers) receiving these requests immediately trigger an **HTTP 403 Forbidden** response with `{ "error": "FORBIDDEN", "message": "Access denied: Administrator privileges required" }`.
- Verified by automated unit and integration tests.

---

## 4. Detailed Code Changes & Explanations

### Backend

#### 1. `CustomerQueueStatusDTO.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/dto/CustomerQueueStatusDTO.java`
- **Purpose**: Data transfer object holding all queue tracking information sent to the customer frontend.
- **Fields**:
  - `tokenId`: Unique queue token ID.
  - `appointmentId`: Associated appointment ID.
  - `customerName`: Patient's full name.
  - `providerName`: Healthcare provider's full name.
  - `appointmentDate`: Calendar date of the consultation.
  - `startTime`: Scheduled appointment slot time.
  - `tokenDisplay`: Formatted token string (e.g. `T-001`).
  - `status`: Current queue token state (`WAITING`, `CALLED`, `IN_PROGRESS`, etc.).
  - `peopleAhead`: Calculated integer of active patients ahead in line.
  - `estimatedWaitMinutes`: Calculated minutes until consultation.
  - `slotDurationMinutes`: Provider's consultation length.
  - `calledAt`: Timestamp when provider called the token.
  - `lastUpdated`: Timestamp of response generation.

#### 2. `AdminStatsDTO.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/dto/AdminStatsDTO.java`
- **Purpose**: Encapsulates summary counts for the administrative dashboard overview.
- **Fields**: `totalUsers`, `totalProviders`, `totalAppointments`, `waitingTokens`, `inProgressTokens`, `completedAppointments`, `cancelledAppointments`.

#### 3. `QueueTokenRepository.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/repository/QueueTokenRepository.java`
- **Key Methods Added**:
  - `countActiveTokensAhead(providerId, appointmentDate, tokenNumber)`: Custom `@Query` counting tokens ahead in statuses `WAITING`, `CALLED`, or `IN_PROGRESS`.
  - `findByCustomerId(customerId)`: Retrieves all queue tokens booked by a specific customer, ordered newest first.
  - `countByStatus(status)`: Aggregate count for specific statuses (e.g. `WAITING`, `IN_PROGRESS`).
  - `countByStatusIn(statuses)`: Aggregate count across multiple status criteria.

#### 4. `QueueService.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/service/QueueService.java`
- **Key Methods Added**:
  - `toCustomerDTO(QueueToken token)`: Computes people ahead, queries the provider's working day duration, derives estimated wait minutes, and sets the current timestamp.
  - `getActiveCustomerToken(User customer, Long appointmentId)`: Finds the customer's active token. If an explicit `appointmentId` is passed, ensures the token actually belongs to the requesting customer.
  - `getCustomerQueueTokens(User customer)`: Returns all historical and active tokens for the customer.
  - `getAllTokens()`: Admin-only retrieval of all queue activity ordered by creation date descending.

#### 5. `QueueController.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/controller/QueueController.java`
- **Key Endpoints Added**:
  - `GET /api/queue/my-token`: Fetches the current user's most relevant active queue token with live wait metrics.
  - `GET /api/queue/my-tokens`: Lists all queue tokens associated with the logged-in customer.

#### 6. `AdminController.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/controller/AdminController.java`
- **Key Endpoints Added**:
  - `GET /api/admin/stats`: Returns system-wide KPI summary metrics.
  - `GET /api/admin/users`: Returns complete user account list.
  - `GET /api/admin/providers`: Returns provider profile records.
  - `GET /api/admin/appointments`: Returns all appointment records.
  - `GET /api/admin/queue`: Returns all queue token transactions.
- **Authorization**: Enforces `verifyAdmin(currentUser)` across all endpoints.

#### 7. `CorsConfig.java`
- Added `/api/admin/**` to the interceptor registry to ensure JWT authentication headers are processed for admin endpoints.

#### 8. `ProviderAvailabilityResponse.java` & `TimeSlotDTO.java`
- Added explicit Jackson bean getters `getIsWorkingDay()` and `getIsBooked()` to eliminate boolean serialization mismatches between backend JSON responses and frontend state.

---

### Frontend

#### 1. `frontend/src/services/api.js`
Added dedicated API helper functions with token injection and error handling:
- `fetchMyActiveQueueToken(appointmentId)`
- `fetchMyQueueTokens()`
- `fetchAdminStats()`
- `fetchAdminUsers()`
- `fetchAdminProviders()`
- `fetchAdminAppointments()`
- `fetchAdminQueue()`

#### 2. `frontend/src/App.jsx`
- **Navigation**:
  - Added **"⏳ Live Queue Tracker"** tab (available to all logged-in customers).
  - Added **"📊 Admin Dashboard"** tab (rendered only when `user.role === 'ADMIN'`).
- **Live Queue Tracker View**:
  - Stepper visualizer displaying progression: `WAITING` $\rightarrow$ `CALLED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`.
  - Prominent hero cards showing Token Code (`T-001`), People Ahead, Estimated Wait Time, Doctor Name, Date, and Time.
  - 10-second automatic polling cycle with live pulsating status indicator.
  - Active token selector when a customer has multiple appointments.
- **Admin Dashboard View**:
  - 7 KPI metric tiles with color-coded gradients.
  - Subtab navigation: "Queue Activity", "Users", "Doctors", and "Appointments".
  - Clean data tables with status badges and formatted date/time stamps.
- **Enhanced Date Selection**:
  - Interactive 7-day pill selector (`getUpcomingDates(7)`) indicating working days and clinic closures.

#### 3. `frontend/src/App.css`
- Added CSS classes for live tracker hero cards, pulse animation (`@keyframes pulse`), stepper progress bar, admin KPI grid cards, and admin data tables.

---

## 5. Integration Test Suite (`AppointmentQueueApplicationTests.java`)
Added 4 automated test scenarios covering Phase 6:
1. `testPhase6CustomerQueueTracking`: Books an appointment, issues token `T-001`, and verifies `peopleAhead == 0` and `estimatedWaitMinutes == 0`.
2. `testPhase6CustomerForbiddenFromViewingOthersTokens`: Creates two distinct customers; asserts customer A cannot access customer B's token (returns HTTP 403 Forbidden).
3. `testPhase6AdminDashboardStatsAndLists`: Queries `/api/admin/stats`, `/api/admin/users`, `/api/admin/providers`, `/api/admin/appointments`, and `/api/admin/queue` using an ADMIN token, verifying HTTP 200 and non-empty responses.
4. `testPhase6NonAdminForbiddenFromAdminEndpoints`: Executes a query against `/api/admin/stats` using a CUSTOMER token, verifying the backend strictly responds with HTTP 403 Forbidden.
- **Total Test Suite Status**: **20 passing tests, 0 failures, 0 errors**.
