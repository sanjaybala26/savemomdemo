# CareQueue - Phase 5 Explanation Document

## 1. Overview of Phase 5
Phase 5 introduces **daily queue token sequencing** and **provider queue management** to CareQueue.
When a patient books an appointment, the system atomically issues a unique daily queue token (e.g. `T-001`, `T-002`) within the exact same database transaction. Healthcare providers can view their daily queue, call waiting patients in sequence, advance patient statuses through consultation stages, and handle skips or cancellations.

---

## 2. Key Architectural Decisions & Concurrency Protection

### A. Daily Per-Doctor Token Sequences
- Each healthcare provider maintains an isolated daily counter starting at 1 on every new calendar date.
- For example:
  - Doctor A on 2026-11-06 gets tokens `T-001`, `T-002`, `T-003`.
  - Doctor B on 2026-11-06 gets tokens `T-001`, `T-002`.
  - Doctor A on 2026-11-07 starts fresh at `T-001`.

### B. Two-Tier Concurrency Protection
To prevent duplicate token numbers when concurrent bookings occur at the exact same millisecond:
1. **Tier 1 (Database Pessimistic Row Lock)**:
   The table `queue_daily_sequences` holds `(provider_id, queue_date, last_token_number)`. During booking, the application executes:
   ```sql
   SELECT id, last_token_number FROM queue_daily_sequences
   WHERE provider_id = ? AND queue_date = ?
   FOR UPDATE;
   ```
   This locks the sequence row in MySQL/H2 for the duration of the booking transaction. Subsequent concurrent transactions wait until this transaction commits. The counter increments by 1, updates the row, and assigns the token number.
2. **Tier 2 (Database Engine Uniqueness Backstop)**:
   The `queue_tokens` table enforces:
   ```sql
   CONSTRAINT uq_provider_date_token UNIQUE (provider_id, appointment_date, token_number)
   ```
   Even if an unexpected bug bypassed row locking, the database engine would reject any duplicate `(provider_id, date, token_number)` combination with a unique constraint violation.

### C. Atomicity (Single Transaction)
Token generation is executed inside `AppointmentService.bookAppointment()` under `@Transactional`. If token generation or database insertion fails, the appointment creation rolls back entirely. A booking cannot succeed without its token.

### D. Queue Token Lifecycle & Cancellation Flow
Tokens transition through structured states:
- `WAITING`: Initial state when patient books an appointment.
- `CALLED`: Provider clicks "Call Next Patient" or calls a specific patient. `called_at` timestamp recorded.
- `IN_PROGRESS`: Patient enters the consultation room; doctor begins medical examination.
- `COMPLETED`: Consultation finished; `completed_at` timestamp recorded.
- `SKIPPED`: Patient was absent or not ready when called; can be re-queued.
- `CANCELLED`: If patient or doctor cancels the appointment, the queue token is automatically updated to `CANCELLED`, preserving full audit history.

---

## 3. Database Schema (`V5__create_queue_tokens_tables.sql`)

### 1. `queue_daily_sequences`
Stores daily counter rows for pessimistic locking:
- `id`: Primary key (`BIGINT AUTO_INCREMENT`).
- `provider_id`: Foreign key referencing `provider_profiles(id)`.
- `queue_date`: Calendar date (`DATE`).
- `last_token_number`: Highest issued integer counter (`INT DEFAULT 0`).
- `updated_at`: Timestamp.
- `CONSTRAINT uq_provider_date UNIQUE (provider_id, queue_date)`

### 2. `queue_tokens`
Stores patient tokens:
- `id`: Primary key (`BIGINT AUTO_INCREMENT`).
- `appointment_id`: Foreign key referencing `appointments(id)` (ON DELETE CASCADE).
- `provider_id`: Foreign key referencing `provider_profiles(id)` (ON DELETE CASCADE).
- `appointment_date`: Calendar date (`DATE`).
- `token_number`: Sequential integer (`INT`).
- `token_display`: Formatted string (e.g. `T-001`, `VARCHAR(16)`).
- `status`: Token state (`WAITING`, `CALLED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`, `CANCELLED`).
- `called_at`: Timestamp when doctor called the patient.
- `completed_at`: Timestamp when consultation concluded.
- `notes`: Consultation notes or reason.
- `CONSTRAINT uq_provider_date_token UNIQUE (provider_id, appointment_date, token_number)`

### 3. `queue_token_history`
Audit trail recording every state change:
- `id`: Primary key (`BIGINT AUTO_INCREMENT`).
- `token_id`: Foreign key referencing `queue_tokens(id)`.
- `from_status`: Previous status (null on creation).
- `to_status`: New status.
- `changed_by_user_id`: Foreign key referencing `users(id)`.
- `reason_or_notes`: Transition explanation.
- `created_at`: Timestamp.

---

## 4. Backend Components

### Model Entities
- `QueueStatus.java`: Enum representing `WAITING`, `CALLED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`, and `CANCELLED`.
- `QueueDailySequence.java`: POJO entity for sequence records.
- `QueueToken.java`: POJO entity for token records.
- `QueueTokenHistory.java`: POJO entity for audit records.

### DTOs
- `QueueTokenDTO.java`: Response DTO containing token number, formatted display (`T-001`), patient name, email, scheduled time window, status, called timestamp, and completed timestamp.
- `UpdateTokenStatusRequest.java`: Request body containing `status` and optional `notes`.
- `AppointmentDTO.java`: Enriched with `tokenNumber`, `tokenDisplay`, and `tokenStatus`.

### Repositories
- `QueueDailySequenceRepository.java`:
  - `getNextTokenNumberWithLock(Long providerId, LocalDate date)`:
    Executes `SELECT ... FOR UPDATE` on the daily sequence row. If no row exists yet for today, inserts starting at 1. Handles concurrent initial inserts via `DuplicateKeyException` catch & locked re-select.
- `QueueTokenRepository.java`:
  - `save(QueueToken token)`: Inserts token into MySQL/H2 using `GeneratedKeyHolder`.
  - `findById(Long id)`: Finds token by ID.
  - `findByAppointmentId(Long appointmentId)`: Finds token linked to an appointment.
  - `findByProviderIdAndDate(Long providerId, LocalDate date)`: Returns all tokens for a provider's daily clinic queue ordered by `token_number ASC`.
  - `findNextWaitingToken(Long providerId, LocalDate date)`: Returns the first token with `status = 'WAITING'` ordered by `token_number ASC LIMIT 1`.
  - `update(QueueToken token)`: Updates status, timestamps, notes, and `updated_at`.
- `QueueTokenHistoryRepository.java`:
  - `save(QueueTokenHistory history)`: Persists audit log entries.
  - `findByTokenId(Long tokenId)`: Lists status change history in chronological order.

### Services
- `QueueService.java`:
  - `generateTokenForAppointment(appointment, customerId)`: Calls sequence lock repository, persists `QueueToken` with `WAITING`, records audit history.
  - `cancelTokenForAppointment(appointmentId, changedByUserId, reason)`: Transitions token to `CANCELLED` and writes to audit history.
  - `getProviderQueue(user, date)`: Validates provider/admin permissions, fetches daily tokens, and transforms to DTOs.
  - `callNextPatient(user, date)`: Finds the next `WAITING` token in sequence, transitions status to `CALLED`, sets `calledAt = Instant.now()`, logs history, and returns updated DTO.
  - `updateTokenStatus(user, tokenId, request)`: Enforces provider ownership (cannot touch other doctors' queues), transitions status (`IN_PROGRESS`, `COMPLETED`, `SKIPPED`, etc.), updates timestamps, and logs history.
- `AppointmentService.java`:
  - Hooks `queueService.generateTokenForAppointment(...)` directly inside `bookAppointment(...)`.
  - Hooks `queueService.cancelTokenForAppointment(...)` directly inside `cancelAppointment(...)`.
  - Enriches `toDTO(...)` with token display and status.

### Controllers & Security
- `QueueController.java` (`/api/queue`):
  - `GET /api/queue/provider?date=YYYY-MM-DD`: Protected for `PROVIDER` and `ADMIN`.
  - `POST /api/queue/call-next?date=YYYY-MM-DD`: Protected for `PROVIDER` and `ADMIN`.
  - `PUT /api/queue/tokens/{id}/status`: Protected for `PROVIDER` and `ADMIN`.
  - Customer attempts to call queue management endpoints receive `HTTP 403 Forbidden`.
- `CorsConfig.java`:
  - Registered `/api/queue/**` with `JwtAuthenticationInterceptor`.

---

## 5. Frontend Components

### API Layer (`frontend/src/services/api.js`)
- `fetchProviderQueue(date)`: Calls `GET /api/queue/provider?date=...`.
- `callNextQueueToken(date)`: Calls `POST /api/queue/call-next?date=...`.
- `updateQueueTokenStatus(tokenId, status, notes)`: Calls `PUT /api/queue/tokens/{id}/status`.

### User Interface (`frontend/src/App.jsx` & `frontend/src/App.css`)
- **Customer Token Visibility**:
  - Immediately upon booking, displays an animated success banner:
    `🎟️ Your Clinic Queue Token: #T-001 (WAITING)`
  - Displays token badge on appointment cards: `🎟️ Token #T-001 (WAITING)` with color coding.
- **Provider Clinic Queue Dashboard** (`📋 Clinic Queue` tab):
  - **Date Selector**: Switch between clinic dates (defaults to today).
  - **"Call Next Waiting Patient" Master Banner**: Shows who is next in line with one-click call button.
  - **Queue Metrics**: Badges for Total, Waiting, Called, In Consultation, Completed, Skipped, and Cancelled.
  - **Patient Queue Cards**: Displays large formatted token number (`T-001`), patient name, email, time window, notes, and call timestamps.
  - **Status Controls**:
    - `WAITING` -> "📢 Call Patient"
    - `CALLED` -> "▶️ Start Consultation" or "⏭️ Skip Patient"
    - `IN_PROGRESS` -> "✅ Complete Visit"
    - "✕ Cancel Token" with confirmation.

---

## 6. How "Call Next" Chooses a Token
1. The provider clicks "Call Next Patient" on the frontend.
2. The request routes to `POST /api/queue/call-next?date=YYYY-MM-DD`.
3. `QueueService` validates provider authorization and queries:
   ```sql
   SELECT * FROM queue_tokens
   WHERE provider_id = ? AND appointment_date = ? AND status = 'WAITING'
   ORDER BY token_number ASC
   LIMIT 1;
   ```
4. The patient with the lowest integer token number who is currently `WAITING` is selected.
5. The token's status transitions from `WAITING` to `CALLED`, and `called_at` is set to the current timestamp.
6. A `QueueTokenHistory` record is written to preserve the audit trail.
7. The updated `QueueTokenDTO` is returned to the provider dashboard.
