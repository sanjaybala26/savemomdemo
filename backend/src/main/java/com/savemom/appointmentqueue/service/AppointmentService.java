package com.savemom.appointmentqueue.service;

import com.savemom.appointmentqueue.dto.AppointmentDTO;
import com.savemom.appointmentqueue.dto.AppointmentHistoryDTO;
import com.savemom.appointmentqueue.dto.BookAppointmentRequest;
import com.savemom.appointmentqueue.dto.CancelAppointmentRequest;
import com.savemom.appointmentqueue.dto.UpdateAppointmentRequest;
import com.savemom.appointmentqueue.model.*;
import com.savemom.appointmentqueue.repository.AppointmentRepository;
import com.savemom.appointmentqueue.repository.AppointmentStatusHistoryRepository;
import com.savemom.appointmentqueue.repository.ProviderAvailabilityRepository;
import com.savemom.appointmentqueue.repository.ProviderProfileRepository;
import com.savemom.appointmentqueue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing appointment booking, cancellation, ownership validation,
 * and double-booking prevention.
 */
@Service
public class AppointmentService {

    public static final ZoneId APP_TIMEZONE = ZoneId.of("Asia/Kolkata");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderAvailabilityRepository providerAvailabilityRepository;
    private final UserRepository userRepository;
    private final QueueService queueService;

    @Autowired
    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentStatusHistoryRepository historyRepository,
                              ProviderProfileRepository providerProfileRepository,
                              ProviderAvailabilityRepository providerAvailabilityRepository,
                              UserRepository userRepository,
                              QueueService queueService) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerAvailabilityRepository = providerAvailabilityRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
    }

    /**
     * Books an appointment slot for a customer.
     * Prevents double-booking using transaction checks and database-backed unique constraints.
     */
    @Transactional
    public AppointmentDTO bookAppointment(User customer, BookAppointmentRequest request) {
        // 1. Basic validation
        if (request.getProviderId() == null) {
            throw new IllegalArgumentException("Provider ID is required");
        }
        if (request.getAppointmentDate() == null || request.getAppointmentDate().trim().isEmpty()) {
            throw new IllegalArgumentException("Appointment date is required (YYYY-MM-DD)");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Slot start time and end time are required");
        }

        LocalDate appointmentDate;
        try {
            appointmentDate = LocalDate.parse(request.getAppointmentDate().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD");
        }

        LocalDate today = LocalDate.now(APP_TIMEZONE);
        LocalTime nowTime = LocalTime.now(APP_TIMEZONE);

        if (appointmentDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot book appointments for past dates");
        }

        LocalTime requestedStart;
        LocalTime requestedEnd;
        try {
            requestedStart = LocalTime.parse(request.getStartTime());
            requestedEnd = LocalTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected HH:mm");
        }

        // Validate that requested slot has not already passed if booking for today
        if (appointmentDate.isEqual(today) && !requestedStart.isAfter(nowTime)) {
            throw new IllegalArgumentException("Cannot book a past appointment slot. Selected slot " +
                    request.getStartTime() + " has already passed (current time: " +
                    nowTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " " + APP_TIMEZONE.getId() + ")");
        }

        // 2. Validate provider is active
        ProviderProfile provider = providerProfileRepository.findById(request.getProviderId())
                .orElseThrow(() -> new NoSuchElementException("Provider not found with ID: " + request.getProviderId()));

        if (!provider.isActive()) {
            throw new IllegalStateException("Selected provider is currently not accepting appointments");
        }

        // 3. Self-booking prevention: A doctor or user cannot book an appointment with themselves
        if (provider.getUserId() != null && provider.getUserId().equals(customer.getId())) {
            throw new IllegalArgumentException("Doctors cannot book an appointment with themselves. Please select another doctor.");
        }

        // 4. Validate provider schedule for this weekday
        ProviderAvailability availability = providerAvailabilityRepository.findByProviderId(provider.getId())
                .orElseThrow(() -> new IllegalStateException("Provider schedule is not configured"));

        DayOfWeek dayOfWeek = appointmentDate.getDayOfWeek();
        boolean isOpen = availability.getWorkingDaysAsList().stream()
                .anyMatch(d -> d.equalsIgnoreCase(dayOfWeek.name()));
        if (!isOpen) {
            throw new IllegalStateException("Provider does not hold consultations on " + dayOfWeek.name() + "s");
        }

        // 4. Validate time window falls within provider opening hours and configured slot intervals
        LocalTime openTime = LocalTime.parse(availability.getOpenTime());
        LocalTime closeTime = LocalTime.parse(availability.getCloseTime());

        if (requestedStart.isBefore(openTime) || requestedEnd.isAfter(closeTime) || !requestedStart.isBefore(requestedEnd)) {
            throw new IllegalArgumentException("Requested time slot is outside the provider's operating hours");
        }

        int durationMinutes = availability.getSlotDurationMinutes();
        if (!requestedStart.plusMinutes(durationMinutes).equals(requestedEnd)) {
            throw new IllegalArgumentException("Requested time slot duration does not match provider's configured duration (" + durationMinutes + " mins)");
        }

        long minutesFromOpen = java.time.Duration.between(openTime, requestedStart).toMinutes();
        if (minutesFromOpen % durationMinutes != 0) {
            throw new IllegalArgumentException("Requested time slot does not align with provider's slot schedule");
        }

        // 5. Tier 1 double-booking check: verify slot is not already booked
        Optional<Appointment> existingActive = appointmentRepository.findActiveByProviderAndSlot(
                provider.getId(), appointmentDate, request.getStartTime()
        );
        if (existingActive.isPresent()) {
            throw new IllegalStateException("This time slot is already booked. Please choose another slot.");
        }

        // 6. Tier 2 double-booking lock: set active_slot_lock = providerId_date_startTime
        String slotLock = provider.getId() + "_" + appointmentDate + "_" + request.getStartTime();

        Appointment newAppt = new Appointment();
        newAppt.setCustomerId(customer.getId());
        newAppt.setProviderId(provider.getId());
        newAppt.setAppointmentDate(appointmentDate);
        newAppt.setStartTime(request.getStartTime());
        newAppt.setEndTime(request.getEndTime());
        newAppt.setStatus(AppointmentStatus.PENDING); // Phase 4 requirement: create new appointments as PENDING
        newAppt.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        newAppt.setActiveSlotLock(slotLock);

        Appointment savedAppt;
        try {
            savedAppt = appointmentRepository.save(newAppt);
        } catch (DataIntegrityViolationException e) {
            // Concurrent race condition caught by the database unique constraint active_slot_lock
            throw new IllegalStateException("This time slot was just booked by another patient. Please choose another slot.");
        }

        // 7. Record status history audit record
        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointmentId(savedAppt.getId());
        history.setFromStatus(null);
        history.setToStatus(AppointmentStatus.PENDING);
        history.setChangedByUserId(customer.getId());
        history.setReasonOrNotes("Appointment booked by patient");
        historyRepository.save(history);

        // 8. Generate daily queue token in the exact same transaction
        queueService.generateTokenForAppointment(savedAppt, customer.getId());

        return toDTO(savedAppt, customer, provider);
    }

    /**
     * Retrieves all appointments relevant for the authenticated user.
     * Patients see appointments they booked.
     * Healthcare providers see appointments booked with them (and any they booked).
     * Administrators see all appointments.
     */
    public List<AppointmentDTO> getAppointmentsForCustomer(User user) {
        List<Appointment> appointments;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> profOpt = providerProfileRepository.findByUserId(user.getId());
            if (profOpt.isPresent()) {
                appointments = appointmentRepository.findByProviderIdOrCustomerId(profOpt.get().getId(), user.getId());
            } else {
                appointments = appointmentRepository.findByCustomerId(user.getId());
            }
        } else if (user.getRole() == Role.ADMIN) {
            appointments = appointmentRepository.findAll();
        } else {
            appointments = appointmentRepository.findByCustomerId(user.getId());
        }

        List<AppointmentDTO> dtoList = new ArrayList<>();
        for (Appointment appt : appointments) {
            User patient = userRepository.findById(appt.getCustomerId()).orElse(null);
            ProviderProfile provider = providerProfileRepository.findById(appt.getProviderId()).orElse(null);
            dtoList.add(toDTO(appt, patient, provider));
        }
        return dtoList;
    }

    /**
     * Retrieves a single appointment by ID.
     * Enforces that customers view their own, providers view their assigned, and admins view all.
     */
    public AppointmentDTO getAppointmentById(User user, Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        boolean isPatient = appt.getCustomerId().equals(user.getId());
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isPatient && !isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to view this appointment");
        }

        User patient = userRepository.findById(appt.getCustomerId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(appt.getProviderId()).orElse(null);
        return toDTO(appt, patient, provider);
    }

    /**
     * Updates permitted appointment details (notes and/or rescheduled slot).
     * Validates provider, operating hours, slot alignment, and double-booking conflict.
     * Preserves associated queue token without creating duplicates.
     */
    @Transactional
    public AppointmentDTO updateAppointmentDetails(User user, Long appointmentId, UpdateAppointmentRequest request) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        // Ownership validation: customer who booked it, assigned provider, or admin
        boolean isPatient = appt.getCustomerId().equals(user.getId());
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isPatient && !isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to update this appointment");
        }

        // Status validation: cannot update cancelled or completed appointments
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled appointments cannot be updated or rescheduled");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Completed appointments cannot be updated or rescheduled");
        }

        boolean scheduleChanged = false;
        Long targetProviderId = request.getProviderId() != null ? request.getProviderId() : appt.getProviderId();

        LocalDate targetDate = appt.getAppointmentDate();
        if (request.getAppointmentDate() != null && !request.getAppointmentDate().trim().isEmpty()) {
            try {
                targetDate = LocalDate.parse(request.getAppointmentDate().trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD");
            }
        }

        String targetStart = (request.getStartTime() != null && !request.getStartTime().trim().isEmpty())
                ? request.getStartTime().trim() : appt.getStartTime();
        String targetEnd = (request.getEndTime() != null && !request.getEndTime().trim().isEmpty())
                ? request.getEndTime().trim() : appt.getEndTime();

        if (!targetProviderId.equals(appt.getProviderId())
                || !targetDate.equals(appt.getAppointmentDate())
                || !targetStart.equals(appt.getStartTime())
                || !targetEnd.equals(appt.getEndTime())) {
            scheduleChanged = true;
        }

        if (scheduleChanged) {
            // 1. Validate target provider
            ProviderProfile targetProvider = providerProfileRepository.findById(targetProviderId)
                    .orElseThrow(() -> new NoSuchElementException("Provider not found with ID: " + targetProviderId));

            if (!targetProvider.isActive()) {
                throw new IllegalStateException("Selected provider is currently not accepting appointments");
            }

            // Self-booking prevention: doctor cannot book/schedule appointment with themselves
            if (targetProvider.getUserId() != null && targetProvider.getUserId().equals(appt.getCustomerId())) {
                throw new IllegalArgumentException("Doctors cannot book an appointment with themselves. Please select another doctor.");
            }

            // 2. Validate date and live time constraints
            LocalDate today = LocalDate.now(APP_TIMEZONE);
            LocalTime nowTime = LocalTime.now(APP_TIMEZONE);

            if (targetDate.isBefore(today)) {
                throw new IllegalArgumentException("Cannot schedule appointments for past dates");
            }

            LocalTime reqStart;
            LocalTime reqEnd;
            try {
                reqStart = LocalTime.parse(targetStart);
                reqEnd = LocalTime.parse(targetEnd);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid time format. Expected HH:mm");
            }

            if (targetDate.isEqual(today) && !reqStart.isAfter(nowTime)) {
                throw new IllegalArgumentException("Cannot schedule a past appointment slot. Selected slot " +
                        targetStart + " has already passed (current time: " +
                        nowTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " " + APP_TIMEZONE.getId() + ")");
            }

            // 3. Validate provider availability for the target day of week
            ProviderAvailability avail = providerAvailabilityRepository.findByProviderId(targetProvider.getId())
                    .orElseThrow(() -> new IllegalStateException("Provider schedule is not configured"));

            DayOfWeek dayOfWeek = targetDate.getDayOfWeek();
            boolean isOpen = avail.getWorkingDaysAsList().stream()
                    .anyMatch(d -> d.equalsIgnoreCase(dayOfWeek.name()));
            if (!isOpen) {
                throw new IllegalStateException("Provider does not hold consultations on " + dayOfWeek.name() + "s");
            }

            // 4. Validate time window within operating hours and slot duration
            LocalTime openTime = LocalTime.parse(avail.getOpenTime());
            LocalTime closeTime = LocalTime.parse(avail.getCloseTime());

            if (reqStart.isBefore(openTime) || reqEnd.isAfter(closeTime) || !reqStart.isBefore(reqEnd)) {
                throw new IllegalArgumentException("Requested time slot is outside the provider's operating hours");
            }

            int durationMinutes = avail.getSlotDurationMinutes();
            if (!reqStart.plusMinutes(durationMinutes).equals(reqEnd)) {
                throw new IllegalArgumentException("Requested slot duration does not match provider's configured duration (" + durationMinutes + " mins)");
            }

            long minutesFromOpen = java.time.Duration.between(openTime, reqStart).toMinutes();
            if (minutesFromOpen % durationMinutes != 0) {
                throw new IllegalArgumentException("Requested time slot does not align with provider's slot schedule");
            }

            // 5. Check slot conflict / double-booking
            Optional<Appointment> existingActive = appointmentRepository.findActiveByProviderAndSlot(
                    targetProvider.getId(), targetDate, targetStart
            );
            if (existingActive.isPresent() && !existingActive.get().getId().equals(appt.getId())) {
                throw new IllegalStateException("This time slot is already booked. Please choose another slot.");
            }

            // Update slot lock and appointment schedule fields
            String newLock = targetProvider.getId() + "_" + targetDate + "_" + targetStart;
            appt.setProviderId(targetProvider.getId());
            appt.setAppointmentDate(targetDate);
            appt.setStartTime(targetStart);
            appt.setEndTime(targetEnd);
            appt.setActiveSlotLock(newLock);
        }

        // Update notes if provided
        if (request.getNotes() != null) {
            appt.setNotes(request.getNotes().trim());
        }

        Appointment updatedAppt;
        try {
            updatedAppt = appointmentRepository.save(appt);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("This time slot was just booked by another patient. Please choose another slot.");
        }

        // Synchronize associated queue token without creating duplicate tokens
        queueService.syncTokenWithAppointment(updatedAppt);

        // Record status history audit record
        String auditDetail = scheduleChanged
                ? ("Appointment rescheduled to " + targetDate + " (" + targetStart + " - " + targetEnd + ")" +
                   (request.getNotes() != null ? " with notes: " + request.getNotes().trim() : ""))
                : ("Appointment notes updated: " + (request.getNotes() != null ? request.getNotes().trim() : "None"));

        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointmentId(updatedAppt.getId());
        history.setFromStatus(updatedAppt.getStatus());
        history.setToStatus(updatedAppt.getStatus());
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes(auditDetail);
        historyRepository.save(history);

        User patient = userRepository.findById(updatedAppt.getCustomerId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(updatedAppt.getProviderId()).orElse(null);
        return toDTO(updatedAppt, patient, provider);
    }

    /**
     * Cancels an appointment if eligible.
     * Enforces ownership, releases the active slot lock, and logs the change in history.
     */
    @Transactional
    public AppointmentDTO cancelAppointment(User user, Long appointmentId, CancelAppointmentRequest request) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        // Ownership validation:
        // Allowed if user is the patient who booked it, the doctor for this visit, or an admin
        boolean isPatient = appt.getCustomerId().equals(user.getId());
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isPatient && !isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to cancel this appointment");
        }

        // Status validation
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Appointment is already cancelled");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Completed appointments cannot be cancelled");
        }

        AppointmentStatus previousStatus = appt.getStatus();
        appt.setStatus(AppointmentStatus.CANCELLED);
        // Releasing activeSlotLock allows this time slot to be booked again by other patients!
        appt.setActiveSlotLock(null);

        Appointment updatedAppt = appointmentRepository.save(appt);

        // Record status history audit record
        String reason = (request != null && request.getReason() != null && !request.getReason().trim().isEmpty())
                ? request.getReason().trim()
                : (isDoctor ? "Cancelled by doctor" : "Cancelled by patient");

        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointmentId(updatedAppt.getId());
        history.setFromStatus(previousStatus);
        history.setToStatus(AppointmentStatus.CANCELLED);
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes(reason);
        historyRepository.save(history);

        // Cancel associated queue token in same transaction and preserve token audit trail
        queueService.cancelTokenForAppointment(updatedAppt.getId(), user.getId(), reason);

        User patient = userRepository.findById(updatedAppt.getCustomerId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(updatedAppt.getProviderId()).orElse(null);
        return toDTO(updatedAppt, patient, provider);
    }

    /**
     * Confirms a pending appointment (callable by assigned provider or admin).
     */
    @Transactional
    public AppointmentDTO confirmAppointment(User user, Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        // Ownership validation: only the doctor for this visit or an admin can confirm
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to confirm this appointment");
        }

        if (appt.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING appointments can be confirmed");
        }

        AppointmentStatus prevStatus = appt.getStatus();
        appt.setStatus(AppointmentStatus.CONFIRMED);
        Appointment updated = appointmentRepository.save(appt);

        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointmentId(updated.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(AppointmentStatus.CONFIRMED);
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes("Appointment confirmed by doctor");
        historyRepository.save(history);

        User patient = userRepository.findById(updated.getCustomerId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(updated.getProviderId()).orElse(null);
        return toDTO(updated, patient, provider);
    }

    /**
     * Completes an appointment (callable by assigned provider or admin).
     * Synchronizes associated queue token and records status audit trail.
     */
    @Transactional
    public AppointmentDTO completeAppointment(User user, Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        // Ownership validation: only the doctor for this visit or an admin can complete
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to complete this appointment");
        }

        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Appointment is already completed");
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled appointments cannot be completed");
        }

        AppointmentStatus prevStatus = appt.getStatus();
        appt.setStatus(AppointmentStatus.COMPLETED);
        appt.setActiveSlotLock(null); // Release lock upon completion
        Appointment updated = appointmentRepository.save(appt);

        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointmentId(updated.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(AppointmentStatus.COMPLETED);
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes("Appointment marked completed by doctor");
        historyRepository.save(history);

        // Synchronize queue token to COMPLETED
        queueService.completeTokenForAppointment(updated.getId(), user.getId(), "Consultation completed");

        User patient = userRepository.findById(updated.getCustomerId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(updated.getProviderId()).orElse(null);
        return toDTO(updated, patient, provider);
    }

    /**
     * Updates an appointment's status (PENDING, CONFIRMED, COMPLETED, CANCELLED).
     * Validates provider ownership and keeps queue tokens synchronized.
     */
    @Transactional
    public AppointmentDTO updateAppointmentStatus(User user, Long appointmentId, String newStatusStr, String reason) {
        if (newStatusStr == null || newStatusStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Status is required");
        }
        AppointmentStatus targetStatus;
        try {
            targetStatus = AppointmentStatus.valueOf(newStatusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid appointment status: " + newStatusStr +
                    ". Allowed statuses: PENDING, CONFIRMED, COMPLETED, CANCELLED.");
        }

        if (targetStatus == AppointmentStatus.CONFIRMED) {
            return confirmAppointment(user, appointmentId);
        } else if (targetStatus == AppointmentStatus.COMPLETED) {
            return completeAppointment(user, appointmentId);
        } else if (targetStatus == AppointmentStatus.CANCELLED) {
            return cancelAppointment(user, appointmentId, new CancelAppointmentRequest(reason != null ? reason : "Cancelled by provider"));
        } else if (targetStatus == AppointmentStatus.PENDING) {
            Appointment appt = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

            boolean isDoctor = false;
            if (user.getRole() == Role.PROVIDER) {
                Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
                if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                    isDoctor = true;
                }
            }
            boolean isAdmin = user.getRole() == Role.ADMIN;
            if (!isDoctor && !isAdmin) {
                throw new SecurityException("You do not have permission to modify this appointment");
            }

            AppointmentStatus prevStatus = appt.getStatus();
            appt.setStatus(AppointmentStatus.PENDING);
            Appointment updated = appointmentRepository.save(appt);

            AppointmentStatusHistory history = new AppointmentStatusHistory();
            history.setAppointmentId(updated.getId());
            history.setFromStatus(prevStatus);
            history.setToStatus(AppointmentStatus.PENDING);
            history.setChangedByUserId(user.getId());
            history.setReasonOrNotes(reason != null ? reason : "Reset to PENDING by doctor");
            historyRepository.save(history);

            User patient = userRepository.findById(updated.getCustomerId()).orElse(null);
            ProviderProfile provider = providerProfileRepository.findById(updated.getProviderId()).orElse(null);
            return toDTO(updated, patient, provider);
        }

        throw new IllegalArgumentException("Unsupported appointment status: " + targetStatus);
    }

    /**
     * Retrieves audit history for an appointment (accessible by customer, assigned provider, or admin).
     */
    public List<AppointmentHistoryDTO> getAppointmentHistory(User user, Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));

        boolean isPatient = appt.getCustomerId().equals(user.getId());
        boolean isDoctor = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> myProf = providerProfileRepository.findByUserId(user.getId());
            if (myProf.isPresent() && myProf.get().getId().equals(appt.getProviderId())) {
                isDoctor = true;
            }
        }
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isPatient && !isDoctor && !isAdmin) {
            throw new SecurityException("You do not have permission to view history for this appointment");
        }

        List<AppointmentStatusHistory> historyList = historyRepository.findByAppointmentId(appointmentId);
        List<AppointmentHistoryDTO> dtos = new ArrayList<>();
        for (AppointmentStatusHistory h : historyList) {
            User changer = userRepository.findById(h.getChangedByUserId()).orElse(null);
            String userName = changer != null ? changer.getName() : "User #" + h.getChangedByUserId();
            String userRole = changer != null && changer.getRole() != null ? changer.getRole().name() : "USER";

            dtos.add(new AppointmentHistoryDTO(
                    h.getId(),
                    h.getAppointmentId(),
                    h.getFromStatus() != null ? h.getFromStatus().name() : null,
                    h.getToStatus().name(),
                    h.getChangedByUserId(),
                    userName,
                    userRole,
                    h.getReasonOrNotes(),
                    h.getCreatedAt() != null ? h.getCreatedAt().toString() : null
            ));
        }
        return dtos;
    }

    private AppointmentDTO toDTO(Appointment appt, User customer, ProviderProfile provider) {
        AppointmentDTO dto = new AppointmentDTO(
                appt.getId(),
                appt.getCustomerId(),
                customer != null ? customer.getName() : "Customer #" + appt.getCustomerId(),
                customer != null ? customer.getEmail() : "",
                appt.getProviderId(),
                provider != null ? provider.getDisplayName() : "Provider #" + appt.getProviderId(),
                provider != null ? provider.getServiceDescription() : "",
                appt.getAppointmentDate() != null ? appt.getAppointmentDate().toString() : null,
                appt.getStartTime(),
                appt.getEndTime(),
                appt.getStatus().name(),
                appt.getNotes(),
                appt.getCreatedAt() != null ? appt.getCreatedAt().toString() : null,
                appt.getUpdatedAt() != null ? appt.getUpdatedAt().toString() : null
        );

        Optional<QueueToken> tokenOpt = queueService.findByAppointmentId(appt.getId());
        if (tokenOpt.isPresent()) {
            QueueToken qt = tokenOpt.get();
            dto.setTokenNumber(qt.getTokenNumber());
            dto.setTokenDisplay(qt.getTokenDisplay());
            dto.setTokenStatus(qt.getStatus().name());
        }

        return dto;
    }
}
