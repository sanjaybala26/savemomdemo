package com.savemom.appointmentqueue.service;

import com.savemom.appointmentqueue.dto.ProviderAvailabilityResponse;
import com.savemom.appointmentqueue.dto.ProviderProfileDTO;
import com.savemom.appointmentqueue.dto.ProviderUpdateRequest;
import com.savemom.appointmentqueue.dto.TimeSlotDTO;
import com.savemom.appointmentqueue.model.Appointment;
import com.savemom.appointmentqueue.model.ProviderAvailability;
import com.savemom.appointmentqueue.model.ProviderProfile;
import com.savemom.appointmentqueue.model.User;
import com.savemom.appointmentqueue.repository.AppointmentRepository;
import com.savemom.appointmentqueue.repository.ProviderAvailabilityRepository;
import com.savemom.appointmentqueue.repository.ProviderProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing provider profile details, scheduling configuration, and slot derivation.
 */
@Service
public class ProviderService {

    public static final ZoneId APP_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderAvailabilityRepository providerAvailabilityRepository;
    private final AppointmentRepository appointmentRepository;

    @Autowired
    public ProviderService(ProviderProfileRepository providerProfileRepository,
                           ProviderAvailabilityRepository providerAvailabilityRepository,
                           AppointmentRepository appointmentRepository) {
        this.providerProfileRepository = providerProfileRepository;
        this.providerAvailabilityRepository = providerAvailabilityRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Retrieves or auto-initializes the profile and schedule for a provider user.
     */
    public ProviderProfileDTO getOrCreateProfileForUser(User user) {
        ProviderProfile profile = providerProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    // Initialize a default profile for the provider
                    ProviderProfile newProfile = new ProviderProfile(
                            null,
                            user.getId(),
                            user.getName(),
                            "General Healthcare Services",
                            true
                    );
                    return providerProfileRepository.save(newProfile);
                });

        ProviderAvailability availability = providerAvailabilityRepository.findByProviderId(profile.getId())
                .orElseGet(() -> {
                    // Initialize default business hours: Mon-Fri 09:00 - 17:00, 30 min slots
                    ProviderAvailability newAvail = new ProviderAvailability(
                            null,
                            profile.getId(),
                            "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
                            "09:00",
                            "17:00",
                            30
                    );
                    return providerAvailabilityRepository.save(newAvail);
                });

        return toDTO(profile, availability);
    }

    /**
     * Updates a provider's profile information and working hours with validation.
     */
    public ProviderProfileDTO updateProfile(User user, ProviderUpdateRequest request) {
        // 1. Validate display name
        if (request.getDisplayName() == null || request.getDisplayName().trim().length() < 2) {
            throw new IllegalArgumentException("Display name must be at least 2 characters");
        }

        // 2. Validate open and close times
        if (request.getOpenTime() == null || request.getCloseTime() == null) {
            throw new IllegalArgumentException("Opening time and closing time are required");
        }

        LocalTime openTime;
        LocalTime closeTime;
        try {
            openTime = LocalTime.parse(request.getOpenTime().trim(), ISO_TIME);
            closeTime = LocalTime.parse(request.getCloseTime().trim(), ISO_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Times must be in 24-hour HH:mm format (e.g. 09:00, 17:00)");
        }

        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("Opening time must be strictly before closing time");
        }

        // 3. Validate slot duration
        int duration = (request.getSlotDurationMinutes() != null) ? request.getSlotDurationMinutes() : 30;
        if (duration < 10 || duration > 240) {
            throw new IllegalArgumentException("Slot duration must be between 10 and 240 minutes");
        }

        if (openTime.plusMinutes(duration).isAfter(closeTime)) {
            throw new IllegalArgumentException("Slot duration cannot exceed the total working window");
        }

        // 4. Validate working days
        List<String> rawDays = request.getWorkingDays();
        if (rawDays == null || rawDays.isEmpty()) {
            throw new IllegalArgumentException("At least one working day must be selected");
        }

        List<String> validDays = new ArrayList<>();
        for (String day : rawDays) {
            String cleanDay = day.trim().toUpperCase(Locale.ROOT);
            try {
                DayOfWeek.valueOf(cleanDay);
                if (!validDays.contains(cleanDay)) {
                    validDays.add(cleanDay);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid day of week: " + day);
            }
        }

        // 5. Retrieve or create profile
        ProviderProfile profile = providerProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> new ProviderProfile(null, user.getId(), user.getName(), "", true));

        profile.setDisplayName(request.getDisplayName().trim());
        if (request.getServiceDescription() != null) {
            profile.setServiceDescription(request.getServiceDescription().trim());
        }
        if (request.getActive() != null) {
            profile.setActive(request.getActive());
        }
        ProviderProfile savedProfile = providerProfileRepository.save(profile);

        // 6. Retrieve or create availability
        ProviderAvailability availability = providerAvailabilityRepository.findByProviderId(savedProfile.getId())
                .orElseGet(() -> new ProviderAvailability(null, savedProfile.getId(), "", "09:00", "17:00", 30));

        availability.setWorkingDays(String.join(",", validDays));
        availability.setOpenTime(openTime.format(ISO_TIME));
        availability.setCloseTime(closeTime.format(ISO_TIME));
        availability.setSlotDurationMinutes(duration);
        ProviderAvailability savedAvail = providerAvailabilityRepository.save(availability);

        return toDTO(savedProfile, savedAvail);
    }

    /**
     * Returns a list of all active providers visible to customers.
     */
    public List<ProviderProfileDTO> getActiveProviders() {
        List<ProviderProfile> profiles = providerProfileRepository.findAllActive();
        List<ProviderProfileDTO> dtoList = new ArrayList<>();
        for (ProviderProfile profile : profiles) {
            Optional<ProviderAvailability> availOpt = providerAvailabilityRepository.findByProviderId(profile.getId());
            dtoList.add(toDTO(profile, availOpt.orElse(null)));
        }
        return dtoList;
    }

    /**
     * Returns a list of all providers including inactive (for administrator dashboard).
     */
    public List<ProviderProfileDTO> getAllProviders() {
        List<ProviderProfile> profiles = providerProfileRepository.findAll();
        List<ProviderProfileDTO> dtoList = new ArrayList<>();
        for (ProviderProfile profile : profiles) {
            Optional<ProviderAvailability> availOpt = providerAvailabilityRepository.findByProviderId(profile.getId());
            dtoList.add(toDTO(profile, availOpt.orElse(null)));
        }
        return dtoList;
    }

    /**
     * Returns a single provider profile by provider id.
     */
    public Optional<ProviderProfileDTO> getProviderById(Long id) {
        return providerProfileRepository.findById(id).map(profile -> {
            Optional<ProviderAvailability> availOpt = providerAvailabilityRepository.findByProviderId(profile.getId());
            return toDTO(profile, availOpt.orElse(null));
        });
    }

    /**
     * Derives available time slots for a provider on a specific calendar date.
     * Checks if the date falls on a working day and generates slots from openTime to closeTime.
     */
    public ProviderAvailabilityResponse getSlotsForDate(Long providerId, String dateString) {
        // 1. Validate date parameter
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date parameter is required in YYYY-MM-DD format");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateString.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD");
        }

        // 2. Look up provider
        ProviderProfile profile = providerProfileRepository.findById(providerId)
                .orElseThrow(() -> new NoSuchElementException("Provider not found with ID: " + providerId));

        if (!profile.isActive()) {
            return new ProviderAvailabilityResponse(
                    profile.getId(),
                    profile.getDisplayName(),
                    date.toString(),
                    date.getDayOfWeek().name(),
                    false,
                    Collections.emptyList(),
                    "Provider is currently inactive"
            );
        }

        // 3. Look up availability schedule
        ProviderAvailability availability = providerAvailabilityRepository.findByProviderId(providerId)
                .orElse(null);

        if (availability == null) {
            return new ProviderAvailabilityResponse(
                    profile.getId(),
                    profile.getDisplayName(),
                    date.toString(),
                    date.getDayOfWeek().name(),
                    false,
                    Collections.emptyList(),
                    "No availability schedule configured for this provider"
            );
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<String> workingDays = availability.getWorkingDaysAsList();

        // 4. Verify if dayOfWeek is in provider's working days
        boolean isWorkingDay = workingDays.stream()
                .anyMatch(day -> day.equalsIgnoreCase(dayOfWeek.name()));

        if (!isWorkingDay) {
            return new ProviderAvailabilityResponse(
                    profile.getId(),
                    profile.getDisplayName(),
                    date.toString(),
                    dayOfWeek.name(),
                    false,
                    Collections.emptyList(),
                    "Provider is closed on " + dayOfWeek.name() + "s"
            );
        }

        // 5. Generate consecutive slots from openTime to closeTime
        LocalTime openTime = LocalTime.parse(availability.getOpenTime(), ISO_TIME);
        LocalTime closeTime = LocalTime.parse(availability.getCloseTime(), ISO_TIME);
        int durationMinutes = availability.getSlotDurationMinutes();

        LocalDate today = LocalDate.now(APP_TIMEZONE);
        LocalTime nowTime = LocalTime.now(APP_TIMEZONE);
        boolean isToday = date.isEqual(today);
        boolean isPastDate = date.isBefore(today);

        List<Appointment> bookedList = appointmentRepository.findActiveByProviderAndDate(providerId, date);
        Set<String> bookedStartTimes = bookedList.stream()
                .map(Appointment::getStartTime)
                .collect(Collectors.toSet());

        List<TimeSlotDTO> slots = new ArrayList<>();
        LocalTime slotStart = openTime;

        while (!slotStart.plusMinutes(durationMinutes).isAfter(closeTime)) {
            LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
            String startIso = slotStart.format(ISO_TIME);
            String endIso = slotEnd.format(ISO_TIME);
            String formatted = slotStart.format(TIME_FORMATTER) + " - " + slotEnd.format(TIME_FORMATTER);
            boolean isBooked = bookedStartTimes.contains(startIso);
            // Slot is marked as past if the date is in the past, or if today and slotStart is before/at current time
            boolean isPast = isPastDate || (isToday && !slotStart.isAfter(nowTime));

            slots.add(new TimeSlotDTO(startIso, endIso, formatted, isBooked, isPast));
            slotStart = slotEnd;
        }

        return new ProviderAvailabilityResponse(
                profile.getId(),
                profile.getDisplayName(),
                date.toString(),
                dayOfWeek.name(),
                true,
                slots,
                "Available time slots generated successfully",
                nowTime.format(ISO_TIME),
                APP_TIMEZONE.getId()
        );
    }

    /**
     * Maps database entities to a unified ProviderProfileDTO.
     */
    private ProviderProfileDTO toDTO(ProviderProfile profile, ProviderAvailability availability) {
        ProviderProfileDTO dto = new ProviderProfileDTO();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUserId());
        dto.setDisplayName(profile.getDisplayName());
        dto.setServiceDescription(profile.getServiceDescription());
        dto.setActive(profile.isActive());

        if (availability != null) {
            dto.setWorkingDays(availability.getWorkingDaysAsList());
            dto.setOpenTime(availability.getOpenTime());
            dto.setCloseTime(availability.getCloseTime());
            dto.setSlotDurationMinutes(availability.getSlotDurationMinutes());
        } else {
            dto.setWorkingDays(Collections.emptyList());
            dto.setOpenTime("09:00");
            dto.setCloseTime("17:00");
            dto.setSlotDurationMinutes(30);
        }
        return dto;
    }
}
