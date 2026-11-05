# CareQueue - End-to-End Demo Script

This script walks through the complete end-to-end user lifecycle across all three roles (**Customer**, **Healthcare Provider / Doctor**, and **Administrator**).

---

## Prerequisites & Setup
1. **Database**: MySQL running on `localhost:3306` with database `appointment_queue_db`.
2. **Backend**: Spring Boot running on `http://localhost:8080`.
3. **Frontend**: React + Vite running on `http://localhost:5173`.

---

## Demo Roles & Test Accounts

| Role | Email | Password | Display Name |
| :--- | :--- | :--- | :--- |
| **Provider (Doctor)** | `dr.sarah@clinic.org` | `doctor123` | Dr. Sarah Jenkins, MD |
| **Customer (Patient 1)** | `alice@example.com` | `password123` | Alice Smith |
| **Customer (Patient 2)** | `bob@example.com` | `password123` | Bob Jones |
| **Administrator** | `admin@carequeue.com` | `adminpassword123` | System Administrator |

*(Note: If you are setting up on a clean database, you can register these accounts or use the SQL seed script in the README).*

---

## Step 1: Provider Working Schedule & Availability
1. Open your browser and navigate to `http://localhost:5173`.
2. Click **Sign In** and log in as the doctor:
   - **Email**: `dr.sarah@clinic.org`
   - **Password**: `doctor123`
3. Notice the navigation shows:
   - **"🩺 Doctor Queue"**
   - **"⚙️ My Schedule"**
4. Click **"⚙️ My Schedule"**:
   - Verify or set working hours: `09:00` to `17:00`.
   - Set slot duration: `30` minutes.
   - Check working days: Monday through Friday.
   - Click **Save Availability**. The system confirms the schedule is updated.
5. Log out.

---

## Step 2: Customer 1 Books an Appointment & Receives Token
1. Click **Sign In** and log in as Customer 1:
   - **Email**: `alice@example.com`
   - **Password**: `password123`
2. Navigate to **"📅 Book Appointment"**:
   - Select doctor: **Dr. Sarah Jenkins, MD (Cardiology)**.
   - Select an upcoming weekday date (e.g., using the interactive date pill selector).
   - Click the earliest available slot: `09:00 - 09:30`.
   - Reason for visit: *"Routine cardiac checkup"*.
   - Click **Confirm Booking**.
3. **Observation**:
   - The booking confirmation card displays immediately.
   - Notice the generated daily queue token: **`T-001`**.
   - The status is **`WAITING`**.

---

## Step 3: Customer 2 Books on the Same Day (Queue Sequencing)
1. In an Incognito window (or after logging out), log in as Customer 2:
   - **Email**: `bob@example.com`
   - **Password**: `password123`
2. Navigate to **"📅 Book Appointment"**:
   - Select **Dr. Sarah Jenkins, MD**.
   - Select the **same date** as Customer 1.
   - Notice slot `09:00 - 09:30` is now marked as **Booked**.
   - Select the next slot: `09:30 - 10:00`.
   - Reason for visit: *"Follow-up consultation"*.
   - Click **Confirm Booking**.
3. **Observation**:
   - Customer 2 receives token **`T-002`**.
   - Counter sequence increments automatically with database row-lock protection.

---

## Step 4: Customer Live Queue Tracking (10-Second Polling)
1. As Customer 2 (`bob@example.com`), click the **"⏳ Live Queue Tracker"** tab in the top navigation.
2. **Observation**:
   - **Token Code**: Displays `T-002`.
   - **Status**: `WAITING`.
   - **People Ahead**: `1` (Alice Smith with `T-001` is ahead).
   - **Estimated Wait Time**: `30 mins` ($1 \text{ person ahead} \times 30\text{-minute slot duration}$).
   - Notice the pulsing green live indicator: *"Auto-refreshing every 10s (Last updated: ...)"*.

---

## Step 5: Provider Queue Actions (Calling & Progressing)
1. Switch back to your provider browser window (or log in as `dr.sarah@clinic.org`).
2. Click **"🩺 Doctor Queue"**:
   - Select the appointment date.
   - Both tokens (`T-001` Alice and `T-002` Bob) appear in the table.
3. Click the **"📢 Call Next Patient"** button (or click **Call** on `T-001`):
   - Token `T-001` advances to **`CALLED`**.
4. Return to Customer 2's window:
   - Within 10 seconds (or on the next poll cycle), Customer 2's tracker shows Alice is now `CALLED`.
5. In the provider window, click **Start Consultation** on `T-001`:
   - Token `T-001` transitions to **`IN_PROGRESS`**.
6. When examination finishes, click **Complete Consultation**:
   - Token `T-001` transitions to **`COMPLETED`**.
7. In Customer 2's window:
   - As `T-001` completes, the polling refresh updates Customer 2's view:
   - **People Ahead**: changes to **`0`**.
   - **Estimated Wait Time**: changes to **`0 mins`**.
8. Provider clicks **"📢 Call Next Patient"**:
   - Token `T-002` (Bob) is now **`CALLED`**!
   - Bob's Live Queue Tracker updates immediately with a celebration banner: *"You have been called! Please proceed to Dr. Sarah Jenkins, MD's room."*

---

## Step 6: Administrator Dashboard & Oversight
1. Log out and sign in as the administrator:
   - **Email**: `admin@carequeue.com`
   - **Password**: `admin123`
2. Notice the navigation now includes **"📊 Admin Dashboard"**. Click it.
3. **Observation**:
   - **Overview KPI Tiles**:
     - *Total Users*: Count of all registered accounts.
     - *Total Providers*: Doctor count.
     - *Total Appointments*: Booking count.
     - *Waiting Tokens*: Active waiting patients.
     - *In Progress*: Consultations currently underway.
     - *Completed*: Finished consultations.
     - *Cancelled*: Cancelled visits.
   - **Directory Subtabs**:
     - Click **"Queue Activity"**: View real-time audit trail of all issued tokens across doctors with call/complete timestamps.
     - Click **"Users"**: View registered patient and staff accounts.
     - Click **"Doctors"**: View clinic doctor profiles and schedules.
     - Click **"Appointments"**: View comprehensive appointment history.

---

## Step 7: Security Verification (Access Control)
1. Open browser Developer Tools (Console / Network).
2. Log out and log in as a Customer (`alice@example.com`).
3. Attempt to fetch admin data directly:
   ```javascript
   fetch('/api/admin/stats', { credentials: 'include' }).then(r => console.log(r.status));
   ```
4. **Observation**:
   - The backend immediately responds with **`403 Forbidden`**.
   - Non-administrators cannot access administrative metrics or customer directories.
