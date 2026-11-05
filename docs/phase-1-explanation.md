# Phase 1 Codebase Explanation & Beginner Guide

This document explains every file, class, method, and important configuration created in **Phase 1** of the CareQueue appointment and queue management project. It is written in simple, conversational language so that any beginner can understand the concepts, explain them during a project review or demo, and follow how all components work together.

---

## 1. High-Level Architecture & End-to-End Data Flow

The Phase 1 architecture connects three independent tiers:
1. **Frontend (Presentation Tier)**: React running in the user's web browser, bundled with Vite.
2. **Backend (Application Tier)**: Java Spring Boot REST API listening on port 8080.
3. **Database (Data Tier)**: MySQL relational database running on port 3306.

### The Step-by-Step Data Flow

```
User Browser
    │
    ▼
1. App.jsx (React) loads in the browser.
    │
    ▼
2. useEffect() hook runs immediately on page load and calls checkBackendHealth().
    │
    ▼
3. api.js executes fetch('http://localhost:8080/api/health').
    │
    ▼ (HTTP Request travels across network to port 8080)
    │
4. CorsConfig.java intercepts request:
   - Checks origin 'http://localhost:5173'.
   - Origin is in allowed list, so browser is granted permission to read response.
    │
    ▼
5. HealthController.java receives GET /api/health:
   - Injects the javax.sql.DataSource.
   - Calls checkDatabaseConnection() helper.
   - Runs connection.isValid(2) against MySQL.
    │
    ▼
6. MySQL answers that connection is active.
    │
    ▼
7. HealthController instantiates HealthResponse DTO:
   - status: "UP"
   - appName: "CareQueue - Appointment & Queue Management"
   - database: "UP (Connected)"
   - timestamp: current UTC ISO string
    │
    ▼ (HTTP 200 OK + JSON payload travels back to browser)
    │
8. api.js parses response JSON and returns { isOnline: true, data: ... }.
    │
    ▼
9. App.jsx updates React state (setStatus('online'), setHealthData(...)).
    │
    ▼
10. Browser re-renders UI:
    - Badge switches from yellow "Checking..." to green "Backend Online".
    - Details card displays live status, service name, DB status, and timestamp.
```

---

## 2. Backend Files Walkthrough (`backend/`)

### `pom.xml` (Maven Project Object Model)
- **What it is**: The configuration blueprint for Maven, Java's project and dependency management tool.
- **Key Sections**:
  - `<parent>`: Inherits sensible default configurations from `spring-boot-starter-parent` (version `3.3.2`).
  - `<properties>`: Specifies `<java.version>21</java.version>` so code is compiled with modern Java features.
  - `<dependencies>`:
    - `spring-boot-starter-web`: Brings in Spring MVC and an embedded Tomcat web server to process HTTP requests.
    - `spring-boot-starter-jdbc`: Provides `DataSource` management and HikariCP connection pooling to communicate with MySQL.
    - `mysql-connector-j`: The official JDBC driver allowing Java to communicate with MySQL.
    - `flyway-core` & `flyway-mysql`: The migration engine that executes versioned SQL scripts against MySQL.
    - `h2`: An in-memory SQL database used exclusively during unit tests (`<scope>test</scope>`) so tests run without needing MySQL active.
    - `spring-boot-starter-test`: Standard testing library including JUnit 5 and Spring Test utilities.
  - `<plugins>`: Includes `spring-boot-maven-plugin` to package the app into a runnable jar.

### `AppointmentQueueApplication.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/AppointmentQueueApplication.java`
- **Purpose**: The main starting point of the Java backend.
- **Important Code**:
  - `@SpringBootApplication`: A composite Spring annotation that enables component scanning (finding `@RestController`, `@Configuration`), auto-configuration, and property binding.
  - `main(String[] args)`: The standard Java method that launches the application using `SpringApplication.run(...)`.

### `config/CorsConfig.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/config/CorsConfig.java`
- **Purpose**: Solves the browser Cross-Origin Resource Sharing (CORS) restriction.
- **Why it is needed**: Browsers block a frontend at `http://localhost:5173` from making AJAX/fetch requests to a backend at `http://localhost:8080` unless the backend explicitly declares that the frontend origin is allowed.
- **Key Code**:
  - `@Value("${app.cors.allowed-origins:...}")`: Injects the list of allowed origins from `application.properties` (or environment variables), with safe defaults for local development.
  - `addCorsMappings(CorsRegistry registry)`: Maps `/**` (all endpoints) to permit requests from the allowed origins with methods `GET, POST, PUT, DELETE, OPTIONS`.

### `dto/HealthResponse.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/dto/HealthResponse.java`
- **Purpose**: A plain old Java object (POJO) used as a Data Transfer Object (DTO). It defines the exact JSON response structure of the health endpoint.
- **Design Decision (No Lombok)**:
  - All fields (`status`, `appName`, `database`, `timestamp`), constructors, getters, and setters are explicitly written out.
  - This avoids compiler plugins, makes the object completely transparent, and helps beginners see exactly how Spring's Jackson serializer converts Java properties to JSON fields.

### `controller/HealthController.java`
- **Location**: `backend/src/main/java/com/savemom/appointmentqueue/controller/HealthController.java`
- **Purpose**: Exposes the REST API endpoint `GET /api/health`.
- **Methods**:
  - `HealthController(DataSource dataSource)`: Explicit constructor-based dependency injection. Spring automatically passes the active database connection pool (`DataSource`).
  - `checkHealth()`: Annotated with `@GetMapping("/health")`. When an HTTP GET request hits `/api/health`, this method builds and returns a `ResponseEntity<HealthResponse>` with HTTP status 200 OK.
  - `checkDatabaseConnection()`: Helper method that borrows a connection from the `DataSource` pool and tests `connection.isValid(2)`. If MySQL is running and reachable within 2 seconds, it returns `"UP (Connected)"`. If MySQL is unavailable, it gracefully catches the exception and returns `"DOWN (error message)"` instead of crashing the server.

### `resources/application.properties`
- **Location**: `backend/src/main/resources/application.properties`
- **Purpose**: Central configuration for the Spring Boot application.
- **Important Keys**:
  - `server.port=${SERVER_PORT:8080}`: Sets the port where the backend listens. Reads environment variable `SERVER_PORT` if provided, otherwise defaults to `8080`.
  - `spring.datasource.url=${DB_URL:jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:appointment_queue_db}...}`: Configures the JDBC connection string using environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`) with fallback defaults.
  - `spring.datasource.username=${DB_USERNAME:root}`: MySQL user.
  - `spring.datasource.password=${DB_PASSWORD:}`: MySQL password.
  - `spring.flyway.enabled=${FLYWAY_ENABLED:true}`: Instructs Flyway to automatically scan and apply migrations on startup.
  - `spring.flyway.locations=classpath:db/migration`: Tells Flyway where migration SQL files live.
  - `app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:...}`: Injects frontend origins for `CorsConfig.java`.

### `resources/db/migration/V1__initial_setup.sql`
- **Location**: `backend/src/main/resources/db/migration/V1__initial_setup.sql`
- **Purpose**: The first database migration script executed by Flyway.
- **Naming Convention**: `V1__<description>.sql` tells Flyway this is version 1.
- **Contents**:
  - Creates table `system_metadata` (with columns `id`, `property_key`, `property_value`, `created_at`).
  - Inserts a baseline row with key `'phase'` and value `'1_initial_setup'`.
  - Confirms Flyway can connect to MySQL, execute schema DDL, and track migration history in its internal `flyway_schema_history` table.

### `test/resources/application.properties` & `AppointmentQueueApplicationTests.java`
- **Purpose**: Provides automated verification of the backend.
- The test properties point to `jdbc:h2:mem:testdb` so that running `mvn test` validates application startup, dependency injection, and REST endpoints even if MySQL is not currently running.

---

## 3. Frontend Files Walkthrough (`frontend/`)

### `package.json`
- **Location**: `frontend/package.json`
- **Purpose**: Defines frontend dependencies and npm scripts (`npm run dev`, `npm run build`).
- **Dependencies**:
  - `react` & `react-dom`: Core React UI rendering libraries.
  - `vite` & `@vitejs/plugin-react`: Modern development server and fast bundler.

### `vite.config.js`
- **Location**: `frontend/vite.config.js`
- **Purpose**: Configures Vite to use the React plugin and sets the local development server to run on port `5173`.

### `src/services/api.js`
- **Location**: `frontend/src/services/api.js`
- **Purpose**: A clean API helper service isolating all network requests from React UI components.
- **Key Code**:
  - `const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'`: Uses Vite's environment variable system to point to the backend server with a reliable local fallback.
  - `checkBackendHealth()`: An asynchronous function that performs `fetch(`${API_BASE_URL}/api/health`)`. It returns a simple status object `{ isOnline: true/false, data, error }`, safely handling network failures (like when the backend is offline).

### `src/App.jsx`
- **Location**: `frontend/src/App.jsx`
- **Purpose**: Main React component containing the landing page and backend health indicator.
- **State Variables**:
  - `status`: String tracking connection state (`'checking'`, `'online'`, or `'offline'`).
  - `healthData`: Holds the JSON payload received from `GET /api/health`.
  - `errorMessage`: Stores error text when the backend is unreachable.
  - `lastChecked`: Displays the timestamp of the most recent health check.
- **Methods**:
  - `testConnection()`: Calls `checkBackendHealth()` and updates state based on the result.
  - `useEffect(() => { testConnection(); }, [])`: Runs once when the page loads to immediately verify backend connectivity.
- **UI Sections**:
  - Hero header with CareQueue branding.
  - Live **Backend Connectivity** card displaying reactive badge colors (green, yellow, red), connection parameters, and a manual **"Re-check Connection"** button.
  - Phase 1 Architecture & roadmap feature cards (Appointment Booking, Digital Queue Tokens, Flyway Migrations).

### `src/App.css` and `src/index.css`
- **Purpose**: Clean, responsive styling using standard CSS variables (`--primary-color`, `--bg-color`, etc.) without third-party CSS dependencies.

---

## 4. Database Setup & Docker Walkthrough

### `docker-compose.yml`
- **Location**: `docker-compose.yml` (project root)
- **Purpose**: Allows launching a containerized MySQL 8 database with a single command (`docker compose up -d`).
- **Configuration**:
  - Image: `mysql:8.0`.
  - Port mapping: `3306:3306`.
  - Container health check: Runs `mysqladmin ping` every 10 seconds to verify MySQL readiness.
  - Volume: `mysql_data` persists database files across container restarts.

### Using an Existing Local MySQL Server
If Docker is not used, Spring Boot connects directly to any local MySQL instance on port 3306 by creating the database `appointment_queue_db` and configuring username and password in `backend/.env`.

---

## 5. Security & Secrets Management

- Source files (`application.properties`, `api.js`, etc.) **never contain hardcoded passwords**.
- `.env.example` templates are provided in the project root, `backend/`, and `frontend/` showing required variables with safe placeholder values.
- `.gitignore` ensures that `.env` files and build artifacts (`node_modules/`, `target/`, `dist/`) are never committed to version control.

---

## 6. How to Demo Phase 1

1. **Show the Codebase**: Point out the clean structure in `backend/` and `frontend/`.
2. **Start the Backend**: Run `mvnw.cmd spring-boot:run` in `backend/`. Show the terminal output where Spring Boot starts on port 8080 and Flyway executes `V1__initial_setup.sql`.
3. **Verify the API**: Open `http://localhost:8080/api/health` in the browser to show the JSON response with `"status": "UP"` and `"database": "UP (Connected)"`.
4. **Start the Frontend**: Run `npm run dev` in `frontend/` and navigate to `http://localhost:5173`.
5. **Demonstrate Live Integration**: Show the green **"Backend Online"** badge on the CareQueue landing page. Stop the backend temporarily and click **"Re-check Connection"** to show the UI reactively transition to **"Backend Unreachable"**, then restart the backend and show it recover immediately.
