# CareQueue - Healthcare Appointment & Live Queue Management System

Welcome to **CareQueue**! CareQueue is an appointment scheduling and digital live queue management application built for healthcare facilities and clinics.

- **Phase 1**: Core architecture foundation (React + Vite, Spring Boot, MySQL, Flyway migrations, and system health checks).
- **Phase 2**: User registration, login, BCrypt password hashing, stateless JWT authentication, and role-based access control (`CUSTOMER`, `PROVIDER`, `ADMIN`).
- **Phase 3**: Healthcare provider profiles, working schedules, and dynamic appointment slot generation.
- **Phase 4**: Customer appointment booking, cancellation, status audit history, and database-level double-booking prevention.
- **Phase 5**: Daily queue token sequencing, database row locks (`SELECT ... FOR UPDATE`) for race condition prevention, and provider queue management ("Call Next", "In Progress", "Complete", "Skip").
- **Phase 6**: Customer real-time queue tracking (people ahead, estimated wait time, 10s polling) and centralized administrative dashboard with overall system KPIs and audit directories.

---

## Architecture Overview

```
┌─────────────────────────────────┐                 HTTP / REST + JSON
│         React Frontend          │ <==================================================>
│     (http://localhost:5173)     │      Customer Booking / Live Queue / Admin Dash
└─────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│       Spring Boot Backend       │
│     (http://localhost:8080)     │
└────────────────┬────────────────┘
                 │
                 │ JPA / Hibernate / Flyway
                 ▼
┌─────────────────────────────────┐
│          MySQL Database         │
│         (localhost:3306)        │
└─────────────────────────────────┘
```

---

## Tech Stack & Prerequisites

- **Java**: JDK 21 (or JDK 17+)
- **Build Tool**: Apache Maven (wrapper included)
- **Node.js**: v18.x or v20.x
- **Frontend**: React 19, Vite, Vanilla CSS
- **Backend**: Spring Boot 3.4.2, Spring Security (JWT / Stateless), Spring Data JPA
- **Database**: Local MySQL 8.0 instance or Docker container
- **Database Migrations**: Flyway

---

## Environment Variables

### Backend Configuration (`backend/.env`)

| Variable | Description | Default Value |
| :--- | :--- | :--- |
| `DB_HOST` | Database host | `localhost` |
| `DB_PORT` | Database port | `3306` |
| `DB_NAME` | Database schema name | `appointment_queue_db` |
| `DB_USERNAME` | MySQL database user | `root` |
| `DB_PASSWORD` | MySQL user password | *(local password)* |
| `JWT_SECRET` | 256-bit secret string for signing HMAC-SHA256 JWTs | `demoSecretKeyMustBeAtLeast32BytesLongForSecurity!` |
| `JWT_EXPIRATION_MS` | JWT validity duration in milliseconds | `86400000` (24 hours) |

### Frontend Configuration (`frontend/.env`)

| Variable | Description | Default Value |
| :--- | :--- | :--- |
| `VITE_API_URL` | Base backend REST API URL | `http://localhost:8080/api` |

---

## Database Migrations (Flyway)

Database migrations run automatically on Spring Boot application startup located in `backend/src/main/resources/db/migration`:
- **`V1__create_users_table.sql`**: Initial users table with email, password hash, role (`CUSTOMER`, `PROVIDER`, `ADMIN`), and timestamps.
- **`V2__create_providers_and_availability_tables.sql`**: Healthcare provider profiles and availability settings (working days, hours, slot duration).
- **`V3__seed_demo_providers.sql`**: Seed data for demo healthcare providers and clinics.
- **`V4__create_appointments_tables.sql`**: Appointments table with unique time slot constraints to prevent double-booking, and audit history log.
- **`V5__create_queue_tokens_tables.sql`**: Queue tokens table (`T-001`) and daily sequence table (`queue_daily_sequences`) with unique constraints.

---

## Step 1: Database Setup

Ensure MySQL is running on `localhost:3306`. Create the database schema:

```sql
CREATE DATABASE IF NOT EXISTS appointment_queue_db;
```

---

## Step 2: Backend Setup & Run

1. Open a terminal and navigate to the `backend` folder:
   ```bash
   cd backend
   ```
2. Copy `.env.example` to `.env` and verify database credentials:
   ```bash
   cp .env.example .env
   ```
3. Run the application:
   - On Windows:
     ```cmd
     powershell -ExecutionPolicy Bypass -File run.ps1
     ```
     *(Or: `mvnw.cmd spring-boot:run`)*
   - On Linux / macOS:
     ```bash
     chmod +x ./mvnw
     ./mvnw spring-boot:run
     ```
4. Verify backend health check:
   ```bash
   curl http://localhost:8080/api/health
   ```
   *Expected response:* `{"status":"UP","database":"CONNECTED"}`

---

## Step 3: Frontend Setup & Run

1. Open a separate terminal and navigate to `frontend`:
   ```bash
   cd frontend
   ```
2. Install npm dependencies (if not already installed):
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. Open your browser and navigate to:
   ```
   http://localhost:5173
   ```

---

## Demo Accounts & Role Elevation

| Role | Email | Password | Display Name |
| :--- | :--- | :--- | :--- |
| **Doctor / Provider** | `dr.sarah@clinic.org` | `doctor123` | Dr. Sarah Jenkins, MD |
| **Patient / Customer** | `alice@example.com` | `password123` | Alice Smith |
| **Administrator** | `admin@carequeue.com` | `adminpassword123` | System Administrator |

To create an administrator or promote any registered account, execute in MySQL:
```sql
USE appointment_queue_db;
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@carequeue.com';
```

---

## Complete REST API Reference

### Health & Authentication
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/health` | Public | System status and database connectivity |
| `POST` | `/api/auth/register` | Public | Register new patient account (`CUSTOMER` role) |
| `POST` | `/api/auth/login` | Public | Authenticate user and return JWT bearer token |
| `GET` | `/api/auth/me` | Authenticated | Retrieve profile details of logged-in user |

### Healthcare Providers & Availability
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/providers` | Public | List all active healthcare providers |
| `GET` | `/api/providers/{id}/slots?date=YYYY-MM-DD` | Public | Dynamic slot availability and booking status |
| `GET` | `/api/provider/me` | Provider | Retrieve own provider profile and working hours |
| `PUT` | `/api/provider/me` | Provider / Admin | Update doctor profile, working days, and slot duration |

### Appointments
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/appointments` | Customer | Book appointment and automatically issue daily queue token |
| `GET` | `/api/appointments/my` | Customer | View customer's booked appointments |
| `PUT` | `/api/appointments/{id}/cancel` | Customer / Provider | Cancel appointment and mark queue token as `CANCELLED` |

### Queue Management & Customer Tracking
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/queue/my-token` | Customer | Customer live queue status, people ahead, and estimated wait |
| `GET` | `/api/queue/my-tokens` | Customer | List of all queue tokens issued to the customer |
| `GET` | `/api/provider/queue?date=YYYY-MM-DD` | Provider | Doctor view of daily queue tokens for given date |
| `POST` | `/api/provider/queue/next` | Provider | Call next waiting patient in line |
| `PUT` | `/api/provider/queue/{id}/status` | Provider | Update token status (`IN_PROGRESS`, `COMPLETED`, `SKIPPED`) |

### Administration
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/stats` | Admin Only | System-wide KPI summary statistics |
| `GET` | `/api/admin/users` | Admin Only | Complete registered users directory |
| `GET` | `/api/admin/providers` | Admin Only | Healthcare provider directory |
| `GET` | `/api/admin/appointments` | Admin Only | Complete appointment booking logs |
| `GET` | `/api/admin/queue` | Admin Only | Live queue token audit transactions |

---

## Production Deployment Steps

### 1. Build Backend JAR
```bash
cd backend
mvn clean package -DskipTests
```
The compiled executable will be located at `backend/target/appointment-queue-0.0.1-SNAPSHOT.jar`. Run with:
```bash
java -jar -Dspring.profiles.active=prod target/appointment-queue-0.0.1-SNAPSHOT.jar
```

### 2. Build Frontend Static Assets
```bash
cd frontend
npm run build
```
The optimized bundle will be generated in `frontend/dist/`. Serve via NGINX, Apache, or cloud hosting (Netlify, Vercel, S3).

---

## Detailed Documentation Guides

- [Phase 1 Explanation](docs/phase-1-explanation.md): Architecture foundation, Flyway, and CORS.
- [Phase 2 Explanation](docs/phase-2-explanation.md): User registration, BCrypt, JWT, role-based access, and security tradeoffs.
- [Phase 3 Explanation](docs/phase-3-explanation.md): Service provider profiles, working schedules, dynamic slot derivation, and role-based management.
- [Phase 4 Explanation](docs/phase-4-explanation.md): Appointment booking, cancellation, status history, and double-booking prevention.
- [Phase 5 Explanation](docs/phase-5-explanation.md): Daily queue token sequencing, database row locks, "call next" patient selection, and queue controls.
- [Phase 6 Explanation](docs/phase-6-explanation.md): Customer live queue tracking, wait calculation, polling mechanism, and administrative dashboard.
- [Demo Script](docs/demo-script.md): Step-by-step walkthrough across Patient, Doctor, and Admin roles.
