-- ==============================================================================
-- Development-Only Helper: Assigning Roles (Phase 2)
-- ==============================================================================
-- In production, roles are managed by authorized administrators through
-- dedicated administration dashboards and audit logs.
-- Users CANNOT self-assign PROVIDER or ADMIN roles via public registration.
--
-- For local testing and demos, you can elevate any registered user to
-- 'PROVIDER' or 'ADMIN' by running these queries in your MySQL client:

-- 1. Elevate a user to PROVIDER (Doctor / Nurse / Specialist):
-- UPDATE users SET role = 'PROVIDER' WHERE email = 'doctor@example.com';

-- 2. Elevate a user to ADMIN (Clinic Administrator):
-- UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';

-- 3. Reset a user back to CUSTOMER:
-- UPDATE users SET role = 'CUSTOMER' WHERE email = 'user@example.com';

-- 4. View all registered users and their current roles:
-- SELECT id, name, email, role, created_at FROM users;

-- 5. View all provider profiles and availability schedules (Phase 3):
-- SELECT p.id AS provider_id, p.display_name, p.is_active, a.working_days, a.open_time, a.close_time, a.slot_duration_minutes
-- FROM provider_profiles p
-- LEFT JOIN provider_availability a ON p.id = a.provider_id;
