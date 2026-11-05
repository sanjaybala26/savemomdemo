# Phase 2 Codebase Explanation: Authentication & Role-Based Access Control

This document explains every file, class, method, database migration, and design decision implemented in **Phase 2** of CareQueue. It is written in simple, clear language to help beginners explain the authentication architecture and demo the system with confidence.

---

## 1. Overview of Phase 2

In Phase 1, we established a runnable foundation with Spring Boot, MySQL, Flyway, and a React landing page.
In Phase 2, we introduce **user identity and security**:
1. **User Storage**: A relational `users` table created with Flyway versioned migration `V2__create_users_table.sql`.
2. **Roles Supported**:
   - `CUSTOMER`: Default patient role for booking appointments and generating queue tokens.
   - `PROVIDER`: Healthcare specialist (doctor, nurse) managing queues and patient consultations.
   - `ADMIN`: Clinic administrator configuring facilities, provider schedules, and staff roles.
3. **Password Security**: Strong, salted one-way hashing with **BCrypt**. Plain-text passwords are never stored in the database or returned in API responses.
4. **Stateless Sessions (JWT)**: Digital JSON Web Tokens signed with HMAC-SHA256 allow the frontend to authenticate subsequent requests without server-side session memory.
5. **Protected Endpoints**: The `/api/auth/me` endpoint is protected by an interceptor, validating the Bearer token before returning profile and role details.
6. **Frontend User Interface**: Interactive React forms for registration, login, profile view with color-coded role badges, and logout.

---

## 2. Step-by-Step Data Flow

### Flow A: Public User Registration (`POST /api/auth/register`)
```
[User Browser]
       │
       ▼ 1. Enters Full Name, Email, and Password (min 6 chars)
[React App.jsx]
       │
       ▼ 2. Calls registerUser() in api.js
[HTTP POST /api/auth/register]
       │
       ▼ 3. CorsConfig verifies allowed origin
[AuthController.register()]
       │
       ├─► 4. Validates inputs: checks name length, email regex, password length >= 6.
       ├─► 5. Checks UserRepository.existsByEmail(): if duplicate, returns HTTP 409 Conflict.
       ├─► 6. Hashes password using PasswordEncoder (BCrypt with random salt).
       ├─► 7. Enforces Role: ALWAYS sets role to Role.CUSTOMER (users cannot self-promote).
       ├─► 8. Inserts record into MySQL `users` table via JdbcTemplate.
       ├─► 9. Issues signed JWT token via JwtService.
       ▼
[Response: HTTP 201 Created]
       │ Returns: { token: "...", tokenType: "Bearer", user: { id, name, email, role: "CUSTOMER" } }
       ▼
[React App.jsx]
       │ Saves token to localStorage and sets currentUser state.
       ▼
[UI Updates to Logged-In Profile View]
```

---

### Flow B: User Login (`POST /api/auth/login`)
```
[User Browser]
       │
       ▼ 1. Enters Email and Password
[React App.jsx]
       │
       ▼ 2. Calls loginUser() in api.js
[HTTP POST /api/auth/login]
       │
       ▼ 3. AuthController.login()
       ├─► 4. Finds user by email in `users` table. If not found, returns HTTP 401 Unauthorized.
       ├─► 5. Verifies password using PasswordEncoder.matches(rawPassword, storedHash).
       │      If hash does not match, returns HTTP 401 Unauthorized ("Invalid email or password").
       ├─► 6. Generates a signed JWT token containing email, userId, and role.
       ▼
[Response: HTTP 200 OK]
       │ Returns: { token: "...", tokenType: "Bearer", user: { id, name, email, role } }
       ▼
[React App.jsx]
       │ Saves token to localStorage and displays profile card with role badge.
```

---

### Flow C: Accessing Protected Endpoint (`GET /api/auth/me`)
```
[User Browser / Refresh]
       │
       ▼ 1. api.js retrieves token from localStorage
[HTTP GET /api/auth/me]
       │ Header: "Authorization: Bearer <jwt_token>"
       ▼
[JwtAuthenticationInterceptor.preHandle()]
       ├─► 2. Checks for "Authorization: Bearer ..." header.
       ├─► 3. Calls JwtService.validateTokenAndGetEmail(token).
       │      - Verifies HMAC-SHA256 signature with secret key.
       │      - Verifies expiration timestamp (exp).
       ├─► 4. If invalid or expired, aborts and returns HTTP 401 Unauthorized JSON.
       ├─► 5. If valid, looks up user in database and attaches to request attribute "currentUser".
       ▼
[AuthController.getCurrentUser()]
       │
       ▼ 6. Extracts "currentUser" from request attribute and returns UserProfileResponse.
[Response: HTTP 200 OK]
       │ { id: 1, name: "...", email: "...", role: "CUSTOMER", createdAt: "..." }
```

---

### Flow D: Logout
```
[User clicks "Log Out"]
       │
       ▼ 1. React handleLogout() triggers.
       ▼ 2. clearStoredToken() deletes 'carequeue_auth_token' from browser localStorage.
       ▼ 3. React sets currentUser to null.
       ▼ 4. UI returns immediately to Login / Register form view.
```

---

## 3. Backend Files Explained

### `db/migration/V2__create_users_table.sql`
- **Purpose**: Creates the `users` table in MySQL during application startup.
- **Columns**:
  - `id`: Auto-incrementing primary key.
  - `name`: User's display name (`VARCHAR(100)`).
  - `email`: User's login address (`VARCHAR(150) UNIQUE`). Indexed for sub-millisecond lookups.
  - `password_hash`: The 60-character BCrypt hash string (`VARCHAR(255)`).
  - `role`: Role string (`CUSTOMER`, `PROVIDER`, `ADMIN`), defaulting to `'CUSTOMER'`.
  - `created_at`: Automatic timestamp of account creation.

### `model/Role.java`
- **Purpose**: An enumeration specifying the three supported authorization roles: `CUSTOMER`, `PROVIDER`, and `ADMIN`.
- **Method `isValid(String)`**: Safe utility to validate role input strings without throwing unhandled exceptions.

### `model/User.java`
- **Purpose**: Domain entity modeling a user row.
- **Design Decision**: Written explicitly without Lombok (explicit private fields, constructors, getters, and setters) to keep all logic understandable and transparent.

### `repository/UserRepository.java`
- **Purpose**: Data access layer executing SQL queries via Spring's `JdbcTemplate`.
- **Key Methods**:
  - `findByEmail(String email)`: Finds user by email (case-insensitive) using parameterized SQL (`LOWER(email) = LOWER(?)`) to prevent SQL injection.
  - `existsByEmail(String email)`: Quick count query to detect duplicate registrations before insert.
  - `save(User user)`: Performs `INSERT INTO users` for new accounts, extracting the auto-generated numeric ID using Spring's `KeyHolder`.
  - `updateUserRole(Long userId, Role newRole)`: Administrative helper to update user roles.

### `security/BCrypt.java` and `security/PasswordEncoder.java`
- **Purpose**: Protects user credentials using the industry-standard BCrypt one-way password hashing algorithm.
- **Why BCrypt**:
  - **Salted**: Generates a cryptographically secure random 16-byte salt for every password, ensuring identical passwords yield completely different hashes (neutralizing rainbow table attacks).
  - **Slow / Adaptive**: Configured with a work factor of 10 ($2^{10} = 1024$ key expansion rounds), making brute-force dictionary attacks computationally infeasible.
  - **Constant-Time Comparison**: `slowEquals()` compares hashes in fixed time to protect against timing side-channel attacks.

### `security/JwtService.java`
- **Purpose**: Issues and validates stateless JSON Web Tokens.
- **Structure**:
  - **Header**: Base64Url-encoded `{"alg":"HS256","typ":"JWT"}`.
  - **Payload**: Base64Url-encoded claims (`sub` = email, `userId`, `name`, `role`, `iat`, `exp`).
  - **Signature**: HMAC-SHA256 computed over `header + "." + payload` using `JWT_SECRET`.
- **Configuration**:
  - `app.jwt.secret`: Read from environment variable `JWT_SECRET` with a secure default for local testing.
  - `app.jwt.expiration-ms`: Read from environment variable `JWT_EXPIRATION_MS` (defaults to 24 hours).

### `security/JwtAuthenticationInterceptor.java`
- **Purpose**: Pre-handle interceptor that inspects incoming requests.
- **Logic**:
  - Checks for header `Authorization: Bearer <token>`.
  - If token is missing, invalid, or expired, halts execution and writes a structured HTTP 401 JSON error (`ErrorResponse`).
  - If token is valid, attaches the loaded `User` object as request attribute `currentUser` for the controller.

### `config/CorsConfig.java`
- Configures Spring MVC to register `JwtAuthenticationInterceptor` specifically for protected paths like `/api/auth/me`.

### `controller/AuthController.java`
- Exposes three core endpoints:
  - `POST /api/auth/register`: Public registration. Validates email and password, enforces `Role.CUSTOMER`, hashes password with BCrypt, stores user, and returns JWT.
  - `POST /api/auth/login`: Authenticates credentials against BCrypt hash and returns JWT.
  - `GET /api/auth/me`: Returns the authenticated user's profile (`id`, `name`, `email`, `role`, `createdAt`).

---

## 4. Frontend Files Explained

### `services/api.js`
- Contains API client functions and token storage utilities:
  - `saveToken(token)`: Stores the JWT string in `localStorage`.
  - `getStoredToken()`: Reads the token from `localStorage`.
  - `clearStoredToken()`: Removes the token on logout.
  - `registerUser(name, email, password)`: Sends POST to `/api/auth/register`.
  - `loginUser(email, password)`: Sends POST to `/api/auth/login`.
  - `fetchCurrentUser()`: Sends GET to `/api/auth/me` with `Authorization: Bearer <token>`.

### `App.jsx`
- React state:
  - `currentUser`: Holds the logged-in user profile, or `null` if unauthenticated.
  - `authTab`: Toggles between `'login'` and `'register'`.
  - `authSuccess` & `authError`: Inline user feedback messages.
- Behavior:
  - Automatically attempts to restore user session on initial load using `fetchCurrentUser()`.
  - Shows clear, friendly validation errors (e.g. duplicate email, wrong password, short passwords).
  - Switches to a personalized dashboard view upon login displaying the user's role badge (`CUSTOMER`, `PROVIDER`, or `ADMIN`).
  - Provides a **Log Out** button that clears the token and resets state.

---

## 5. Security Tradeoffs & Plain-Language Explanation

### Where the Token is Stored: `localStorage` vs `HttpOnly Cookies`
In this implementation, the JWT token is stored in the browser's `localStorage`:
- **Why `localStorage` was chosen for Phase 2**:
  - **Simplicity & Readability**: Extremely beginner-friendly, works seamlessly across local ports (`5173` to `8080`), and requires no complex cookie domain configuration.
  - **Immune to CSRF**: Standard fetch requests manually attaching `Authorization: Bearer ...` headers cannot be automatically submitted by cross-origin attacker scripts, eliminating Cross-Site Request Forgery (CSRF).
- **The Tradeoff (XSS Risk)**:
  - If malicious JavaScript (Cross-Site Scripting / XSS) were injected into the frontend, it could read `localStorage`.
  - In high-security production financial/healthcare systems, tokens can alternatively be issued in `HttpOnly; Secure; SameSite=Strict` cookies, preventing JavaScript access at the cost of requiring CSRF protection tokens.

### Preventing Self-Promotion to Privileged Roles
- **Vulnerability Prevented**: In poorly designed APIs, registration requests accept a `role` field (e.g. `{ "email": "...", "role": "ADMIN" }`).
- **CareQueue Defense**: The `RegisterRequest` DTO and `AuthController.register()` completely ignore any role passed by the client and **strictly hardcode `newUser.setRole(Role.CUSTOMER)`**.

---

## 6. How to Create Local Provider and Admin Demo Accounts

Because users cannot self-assign privileged roles, here is how you create `PROVIDER` and `ADMIN` accounts for testing:

1. **Register normally** through the web UI at `http://localhost:5173`:
   - Name: `Dr. Marcus Vance`, Email: `doctor@example.com`, Password: `password123`
   - Name: `Alice Administrator`, Email: `admin@example.com`, Password: `password123`
2. **Elevate the role** directly in your MySQL database:
   ```sql
   USE appointment_queue_db;

   -- Promote Dr. Marcus Vance to PROVIDER:
   UPDATE users SET role = 'PROVIDER' WHERE email = 'doctor@example.com';

   -- Promote Alice Administrator to ADMIN:
   UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
   ```
   *(Helper queries are also provided in [`backend/src/main/resources/db/dev-assign-roles.sql`](file:///d:/SaveMom/backend/src/main/resources/db/dev-assign-roles.sql))*
3. **Log back in**: The CareQueue dashboard will immediately reflect the updated role with the corresponding green **PROVIDER** or purple **ADMIN** badge!
