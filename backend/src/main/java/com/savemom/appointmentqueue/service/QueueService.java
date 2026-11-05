package com.savemom.appointmentqueue.service;

import com.savemom.appointmentqueue.dto.CustomerQueueStatusDTO;
import com.savemom.appointmentqueue.dto.QueueTokenDTO;
import com.savemom.appointmentqueue.dto.UpdateTokenStatusRequest;
import com.savemom.appointmentqueue.model.*;
import com.savemom.appointmentqueue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing daily queue token sequencing, patient call flow,
 * status transitions, customer wait time estimations, and audit trails.
 */
@Service
public class QueueService {

    private final QueueDailySequenceRepository sequenceRepository;
    private final QueueTokenRepository tokenRepository;
    private final QueueTokenHistoryRepository tokenHistoryRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderAvailabilityRepository providerAvailabilityRepository;
    private final UserRepository userRepository;
    private final AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;

    @Autowired
    public QueueService(QueueDailySequenceRepository sequenceRepository,
                        QueueTokenRepository tokenRepository,
                        QueueTokenHistoryRepository tokenHistoryRepository,
                        AppointmentRepository appointmentRepository,
                        ProviderProfileRepository providerProfileRepository,
                        ProviderAvailabilityRepository providerAvailabilityRepository,
                        UserRepository userRepository,
                        AppointmentStatusHistoryRepository appointmentStatusHistoryRepository) {
        this.sequenceRepository = sequenceRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHistoryRepository = tokenHistoryRepository;
        this.appointmentRepository = appointmentRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerAvailabilityRepository = providerAvailabilityRepository;
        this.userRepository = userRepository;
        this.appointmentStatusHistoryRepository = appointmentStatusHistoryRepository;
    }

    /**
     * Atomically generates a unique queue token for a booked appointment.
     * Must be called within the booking transaction.
     */
    @Transactional
    public QueueToken generateTokenForAppointment(Appointment appointment, Long customerUserId) {
        // Idempotency check: prevent duplicate tokens if a booking request is repeated or retried
        Optional<QueueToken> existingToken = tokenRepository.findByAppointmentId(appointment.getId());
        if (existingToken.isPresent()) {
            return existingToken.get();
        }

        // 1. Lock daily sequence row and get next token number for this provider and date
        int nextTokenNumber = sequenceRepository.getNextTokenNumberWithLock(
                appointment.getProviderId(),
                appointment.getAppointmentDate()
        );

        String tokenDisplay = String.format("T-%03d", nextTokenNumber);

        // 2. Persist queue token with WAITING status
        QueueToken token = new QueueToken();
        token.setAppointmentId(appointment.getId());
        token.setProviderId(appointment.getProviderId());
        token.setAppointmentDate(appointment.getAppointmentDate());
        token.setTokenNumber(nextTokenNumber);
        token.setTokenDisplay(tokenDisplay);
        token.setStatus(QueueStatus.WAITING);
        token.setNotes(appointment.getNotes());

        QueueToken savedToken = tokenRepository.save(token);

        // 3. Record audit trail entry
        QueueTokenHistory history = new QueueTokenHistory();
        history.setTokenId(savedToken.getId());
        history.setFromStatus(null);
        history.setToStatus(QueueStatus.WAITING);
        history.setChangedByUserId(customerUserId);
        history.setReasonOrNotes("Token issued upon appointment booking");
        tokenHistoryRepository.save(history);

        return savedToken;
    }

    /**
     * Cancels the queue token associated with an appointment.
     * Preserves audit history.
     */
    @Transactional
    public void cancelTokenForAppointment(Long appointmentId, Long changedByUserId, String reason) {
        Optional<QueueToken> tokenOpt = tokenRepository.findByAppointmentId(appointmentId);
        if (tokenOpt.isEmpty()) {
            return;
        }

        QueueToken token = tokenOpt.get();
        if (token.getStatus() == QueueStatus.CANCELLED) {
            return;
        }

        QueueStatus prevStatus = token.getStatus();
        token.setStatus(QueueStatus.CANCELLED);
        if (reason != null && !reason.trim().isEmpty()) {
            token.setNotes(reason.trim());
        }
        tokenRepository.update(token);

        QueueTokenHistory history = new QueueTokenHistory();
        history.setTokenId(token.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(QueueStatus.CANCELLED);
        history.setChangedByUserId(changedByUserId);
        history.setReasonOrNotes(reason != null ? reason : "Appointment cancelled");
        tokenHistoryRepository.save(history);
    }

    /**
     * Marks the queue token associated with an appointment as COMPLETED.
     * Preserves audit history.
     */
    @Transactional
    public void completeTokenForAppointment(Long appointmentId, Long changedByUserId, String notes) {
        Optional<QueueToken> tokenOpt = tokenRepository.findByAppointmentId(appointmentId);
        if (tokenOpt.isEmpty()) {
            return;
        }

        QueueToken token = tokenOpt.get();
        if (token.getStatus() == QueueStatus.COMPLETED) {
            return;
        }

        QueueStatus prevStatus = token.getStatus();
        token.setStatus(QueueStatus.COMPLETED);
        if (token.getCompletedAt() == null) {
            token.setCompletedAt(Instant.now());
        }
        if (notes != null && !notes.trim().isEmpty()) {
            token.setNotes(notes.trim());
        }
        tokenRepository.update(token);

        QueueTokenHistory history = new QueueTokenHistory();
        history.setTokenId(token.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(QueueStatus.COMPLETED);
        history.setChangedByUserId(changedByUserId);
        history.setReasonOrNotes(notes != null ? notes : "Consultation completed");
        tokenHistoryRepository.save(history);
    }

    /**
     * Retrieves the daily consultation queue for a provider on a specific date.
     * Providers can only view their own queue; Admins can view any queue.
     */
    public List<QueueTokenDTO> getProviderQueue(User user, LocalDate date) {
        Long providerId = resolveProviderId(user);
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        List<QueueToken> tokens = tokenRepository.findByProviderIdAndDate(providerId, targetDate);

        return tokens.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Calls the next patient in WAITING status for today's queue.
     * Transitions token status from WAITING to CALLED and records timestamp.
     */
    @Transactional
    public QueueTokenDTO callNextPatient(User user, LocalDate date) {
        Long providerId = resolveProviderId(user);
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        QueueToken nextToken = tokenRepository.findNextWaitingToken(providerId, targetDate)
                .orElseThrow(() -> new NoSuchElementException("No waiting patients in queue for " + targetDate));

        QueueStatus prevStatus = nextToken.getStatus();
        nextToken.setStatus(QueueStatus.CALLED);
        nextToken.setCalledAt(Instant.now());
        tokenRepository.update(nextToken);

        QueueTokenHistory history = new QueueTokenHistory();
        history.setTokenId(nextToken.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(QueueStatus.CALLED);
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes("Called next patient by doctor");
        tokenHistoryRepository.save(history);

        // Keep appointment status consistent: transition PENDING to CONFIRMED
        appointmentRepository.findById(nextToken.getAppointmentId()).ifPresent(appt -> {
            if (appt.getStatus() == AppointmentStatus.PENDING) {
                appt.setStatus(AppointmentStatus.CONFIRMED);
                appointmentRepository.save(appt);

                AppointmentStatusHistory apptHistory = new AppointmentStatusHistory();
                apptHistory.setAppointmentId(appt.getId());
                apptHistory.setFromStatus(AppointmentStatus.PENDING);
                apptHistory.setToStatus(AppointmentStatus.CONFIRMED);
                apptHistory.setChangedByUserId(user.getId());
                apptHistory.setReasonOrNotes("Appointment confirmed as patient was called by doctor");
                appointmentStatusHistoryRepository.save(apptHistory);
            }
        });

        return toDTO(nextToken);
    }

    /**
     * Updates a token's status (e.g. IN_PROGRESS, COMPLETED, SKIPPED, CANCELLED).
     * Enforces that providers can only modify tokens in their own queue.
     */
    @Transactional
    public QueueTokenDTO updateTokenStatus(User user, Long tokenId, UpdateTokenStatusRequest request) {
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Status is required");
        }

        QueueStatus newStatus;
        try {
            newStatus = QueueStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid queue status: " + request.getStatus() +
                    ". Must be WAITING, CALLED, IN_PROGRESS, COMPLETED, SKIPPED, or CANCELLED.");
        }

        QueueToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NoSuchElementException("Queue token not found with ID: " + tokenId));

        // Ownership enforcement: provider can only modify their own queue
        if (user.getRole() == Role.PROVIDER) {
            Long myProviderId = resolveProviderId(user);
            if (!token.getProviderId().equals(myProviderId)) {
                throw new SecurityException("You do not have permission to manage this patient's token");
            }
        }

        QueueStatus prevStatus = token.getStatus();
        token.setStatus(newStatus);

        if (newStatus == QueueStatus.CALLED && token.getCalledAt() == null) {
            token.setCalledAt(Instant.now());
        } else if (newStatus == QueueStatus.COMPLETED && token.getCompletedAt() == null) {
            token.setCompletedAt(Instant.now());
        }

        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            token.setNotes(request.getNotes().trim());
        }

        tokenRepository.update(token);

        // Keep appointment status and audit history in sync with queue token progress
        if (newStatus == QueueStatus.COMPLETED) {
            appointmentRepository.findById(token.getAppointmentId()).ifPresent(appt -> {
                AppointmentStatus prevApptStatus = appt.getStatus();
                if (prevApptStatus != AppointmentStatus.COMPLETED) {
                    appt.setStatus(AppointmentStatus.COMPLETED);
                    appt.setActiveSlotLock(null); // Release lock upon completion
                    appointmentRepository.save(appt);

                    AppointmentStatusHistory apptHistory = new AppointmentStatusHistory();
                    apptHistory.setAppointmentId(appt.getId());
                    apptHistory.setFromStatus(prevApptStatus);
                    apptHistory.setToStatus(AppointmentStatus.COMPLETED);
                    apptHistory.setChangedByUserId(user.getId());
                    apptHistory.setReasonOrNotes("Completed via queue consultation finish");
                    appointmentStatusHistoryRepository.save(apptHistory);
                }
            });
        } else if (newStatus == QueueStatus.CALLED || newStatus == QueueStatus.IN_PROGRESS) {
            appointmentRepository.findById(token.getAppointmentId()).ifPresent(appt -> {
                if (appt.getStatus() == AppointmentStatus.PENDING) {
                    appt.setStatus(AppointmentStatus.CONFIRMED);
                    appointmentRepository.save(appt);

                    AppointmentStatusHistory apptHistory = new AppointmentStatusHistory();
                    apptHistory.setAppointmentId(appt.getId());
                    apptHistory.setFromStatus(AppointmentStatus.PENDING);
                    apptHistory.setToStatus(AppointmentStatus.CONFIRMED);
                    apptHistory.setChangedByUserId(user.getId());
                    apptHistory.setReasonOrNotes("Appointment confirmed as patient was called into consultation");
                    appointmentStatusHistoryRepository.save(apptHistory);
                }
            });
        } else if (newStatus == QueueStatus.CANCELLED) {
            appointmentRepository.findById(token.getAppointmentId()).ifPresent(appt -> {
                if (appt.getStatus() != AppointmentStatus.CANCELLED) {
                    AppointmentStatus prevApptStatus = appt.getStatus();
                    appt.setStatus(AppointmentStatus.CANCELLED);
                    appt.setActiveSlotLock(null); // Release lock upon cancellation
                    appointmentRepository.save(appt);

                    AppointmentStatusHistory apptHistory = new AppointmentStatusHistory();
                    apptHistory.setAppointmentId(appt.getId());
                    apptHistory.setFromStatus(prevApptStatus);
                    apptHistory.setToStatus(AppointmentStatus.CANCELLED);
                    apptHistory.setChangedByUserId(user.getId());
                    apptHistory.setReasonOrNotes(request.getNotes() != null ? request.getNotes().trim() : "Cancelled by provider via queue");
                    appointmentStatusHistoryRepository.save(apptHistory);
                }
            });
        }

        QueueTokenHistory history = new QueueTokenHistory();
        history.setTokenId(token.getId());
        history.setFromStatus(prevStatus);
        history.setToStatus(newStatus);
        history.setChangedByUserId(user.getId());
        history.setReasonOrNotes(request.getNotes() != null ? request.getNotes().trim() : ("Status updated to " + newStatus));
        tokenHistoryRepository.save(history);

        return toDTO(token);
    }

    public Optional<QueueToken> findByAppointmentId(Long appointmentId) {
        return tokenRepository.findByAppointmentId(appointmentId);
    }

    /**
     * Synchronizes an existing queue token when appointment details or schedule are updated.
     * Prevents duplicate tokens while keeping provider, date, and notes in sync.
     */
    @Transactional
    public void syncTokenWithAppointment(Appointment appointment) {
        Optional<QueueToken> tokenOpt = tokenRepository.findByAppointmentId(appointment.getId());
        if (tokenOpt.isPresent()) {
            QueueToken token = tokenOpt.get();
            token.setProviderId(appointment.getProviderId());
            token.setAppointmentDate(appointment.getAppointmentDate());
            if (appointment.getNotes() != null) {
                token.setNotes(appointment.getNotes());
            }
            tokenRepository.update(token);
        }
    }

    private Long resolveProviderId(User user) {
        if (user.getRole() == Role.PROVIDER) {
            ProviderProfile profile = providerProfileRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new NoSuchElementException("Provider profile not found for user: " + user.getName()));
            return profile.getId();
        } else if (user.getRole() == Role.ADMIN) {
            List<ProviderProfile> profiles = providerProfileRepository.findAll();
            if (profiles.isEmpty()) {
                throw new NoSuchElementException("No provider profiles registered in system");
            }
            return profiles.get(0).getId();
        } else {
            throw new SecurityException("Only healthcare providers and administrators can manage clinic queues");
        }
    }

    public QueueTokenDTO toDTO(QueueToken token) {
        Appointment appt = appointmentRepository.findById(token.getAppointmentId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(token.getProviderId()).orElse(null);
        User customer = (appt != null) ? userRepository.findById(appt.getCustomerId()).orElse(null) : null;

        return new QueueTokenDTO(
                token.getId(),
                token.getAppointmentId(),
                token.getProviderId(),
                provider != null ? provider.getDisplayName() : "Provider #" + token.getProviderId(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : "Patient",
                customer != null ? customer.getEmail() : "",
                token.getAppointmentDate() != null ? token.getAppointmentDate().toString() : "",
                appt != null ? appt.getStartTime() : "",
                appt != null ? appt.getEndTime() : "",
                token.getTokenNumber(),
                token.getTokenDisplay(),
                token.getStatus().name(),
                token.getCalledAt() != null ? token.getCalledAt().toString() : null,
                token.getCompletedAt() != null ? token.getCompletedAt().toString() : null,
                token.getNotes(),
                token.getCreatedAt() != null ? token.getCreatedAt().toString() : null,
                token.getUpdatedAt() != null ? token.getUpdatedAt().toString() : null
        );
    }

    /**
     * Converts a raw QueueToken entity into a CustomerQueueStatusDTO with people ahead and wait time calculations.
     */
    public CustomerQueueStatusDTO toCustomerDTO(QueueToken token) {
        Appointment appt = appointmentRepository.findById(token.getAppointmentId()).orElse(null);
        ProviderProfile provider = providerProfileRepository.findById(token.getProviderId()).orElse(null);
        Optional<ProviderAvailability> availOpt = providerAvailabilityRepository.findByProviderId(token.getProviderId());
        int slotDuration = availOpt.map(ProviderAvailability::getSlotDurationMinutes).orElse(30);

        int peopleAhead = 0;
        int estimatedWaitMinutes = 0;

        if (token.getStatus() == QueueStatus.WAITING) {
            // Count active tokens ahead with lower token_number on the same date
            peopleAhead = tokenRepository.countActiveTokensAhead(
                    token.getProviderId(),
                    token.getAppointmentDate(),
                    token.getTokenNumber()
            );
            // Simple robust estimation: people ahead multiplied by slot duration
            estimatedWaitMinutes = peopleAhead * slotDuration;
        } else if (token.getStatus() == QueueStatus.CALLED) {
            peopleAhead = 0;
            estimatedWaitMinutes = 0;
        } else if (token.getStatus() == QueueStatus.IN_PROGRESS) {
            peopleAhead = 0;
            estimatedWaitMinutes = 0;
        }

        Optional<QueueToken> currentlyServingOpt = tokenRepository.findCurrentlyServing(token.getProviderId(), token.getAppointmentDate());
        String currentlyServing = currentlyServingOpt.map(QueueToken::getTokenDisplay).orElse("None");

        User customer = (appt != null) ? userRepository.findById(appt.getCustomerId()).orElse(null) : null;

        int queuePosition = 0;
        if (token.getStatus() == QueueStatus.WAITING) {
            queuePosition = peopleAhead + 1;
        } else if (token.getStatus() == QueueStatus.CALLED || token.getStatus() == QueueStatus.IN_PROGRESS) {
            queuePosition = 1;
        }

        return new CustomerQueueStatusDTO(
                token.getId(),
                token.getAppointmentId(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : "Patient",
                token.getProviderId(),
                provider != null ? provider.getDisplayName() : "Healthcare Provider",
                provider != null ? provider.getServiceDescription() : "Medical Consultation",
                token.getAppointmentDate() != null ? token.getAppointmentDate().toString() : "",
                appt != null ? appt.getStartTime() : "",
                appt != null ? appt.getEndTime() : "",
                token.getTokenNumber(),
                token.getTokenDisplay(),
                token.getStatus().name(),
                peopleAhead,
                queuePosition,
                estimatedWaitMinutes,
                slotDuration,
                currentlyServing,
                token.getCalledAt() != null ? token.getCalledAt().toString() : null,
                token.getCompletedAt() != null ? token.getCompletedAt().toString() : null,
                token.getNotes(),
                Instant.now().toString()
        );
    }

    /**
     * Retrieves the active queue token for an authenticated customer.
     * Prevents access to other customers' tokens.
     */
    public Optional<CustomerQueueStatusDTO> getActiveCustomerToken(User customer, Long appointmentId) {
        if (appointmentId != null) {
            Appointment appt = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new NoSuchElementException("Appointment not found with ID: " + appointmentId));
            if (!appt.getCustomerId().equals(customer.getId()) && customer.getRole() != Role.ADMIN) {
                throw new SecurityException("You do not have permission to view this queue token");
            }
            return tokenRepository.findByAppointmentId(appointmentId).map(this::toCustomerDTO);
        }

        List<QueueToken> tokens = tokenRepository.findByCustomerId(customer.getId());
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        // Prioritize active tokens (WAITING, CALLED, IN_PROGRESS)
        Optional<QueueToken> activeToken = tokens.stream()
                .filter(t -> t.getStatus() == QueueStatus.WAITING ||
                             t.getStatus() == QueueStatus.CALLED ||
                             t.getStatus() == QueueStatus.IN_PROGRESS)
                .findFirst();

        return activeToken.map(this::toCustomerDTO).or(() -> Optional.of(toCustomerDTO(tokens.get(0))));
    }

    /**
     * Retrieves all queue tokens booked by an authenticated customer.
     */
    public List<CustomerQueueStatusDTO> getCustomerQueueTokens(User customer) {
        List<QueueToken> tokens = tokenRepository.findByCustomerId(customer.getId());
        return tokens.stream().map(this::toCustomerDTO).collect(Collectors.toList());
    }

    /**
     * Lists all queue activity across providers (for admin dashboard).
     */
    public List<QueueTokenDTO> getAllTokens() {
        List<QueueToken> tokens = tokenRepository.findAll();
        return tokens.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Retrieves status transition audit history for a queue token with ownership verification.
     */
    public List<QueueTokenHistory> getTokenHistory(User user, Long tokenId) {
        QueueToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NoSuchElementException("Queue token not found with ID: " + tokenId));

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isProvider = false;
        if (user.getRole() == Role.PROVIDER) {
            Optional<ProviderProfile> prof = providerProfileRepository.findByUserId(user.getId());
            if (prof.isPresent() && prof.get().getId().equals(token.getProviderId())) {
                isProvider = true;
            }
        }
        boolean isCustomer = false;
        Optional<Appointment> apptOpt = appointmentRepository.findById(token.getAppointmentId());
        if (apptOpt.isPresent() && apptOpt.get().getCustomerId().equals(user.getId())) {
            isCustomer = true;
        }

        if (!isAdmin && !isProvider && !isCustomer) {
            throw new SecurityException("You do not have permission to view history for this queue token");
        }

        return tokenHistoryRepository.findByTokenId(tokenId);
    }
}
