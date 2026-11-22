package com.ticket_war.war.service;

import com.ticket_war.war.dto.request.RegisterParticipantRequest;
import com.ticket_war.war.dto.response.ParticipantAccessValidationResponse;
import com.ticket_war.war.dto.response.AdminParticipantResponse;
import com.ticket_war.war.dto.response.ParticipantProfileResponse;
import com.ticket_war.war.dto.response.RegisterParticipantResponse;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarParticipantStatus;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.repository.WarParticipantRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import com.ticket_war.war.util.ParticipantAccessTokenUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WarParticipantService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)*$",
            Pattern.CASE_INSENSITIVE);

    private final WarSessionRepository warSessionRepository;
    private final WarParticipantRepository warParticipantRepository;
    private final ParticipantEmailService participantEmailService;

    public WarParticipantService(
            WarSessionRepository warSessionRepository,
            WarParticipantRepository warParticipantRepository,
            ParticipantEmailService participantEmailService) {
        this.warSessionRepository = warSessionRepository;
        this.warParticipantRepository = warParticipantRepository;
        this.participantEmailService = participantEmailService;
    }

    @Transactional
    @CacheEvict(cacheNames = {"warSessionLists", "warSessionDetails"}, allEntries = true)
    public RegisterParticipantResponse register(
            UUID warSessionId,
            RegisterParticipantRequest request) {
        String email = normalizeAndValidateEmail(request.getEmail());
        WarSession session = warSessionRepository.findByIdForUpdate(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));

        if (session.getStatus() != WarSessionStatus.SCHEDULED) {
            throw conflict("Participants can register only for a SCHEDULED War Session.");
        }
        if (session.getRegistrationOpensAt() == null || session.getRegistrationClosesAt() == null) {
            throw conflict("The War Session registration window is not configured.");
        }

        Instant now = Instant.now();
        if (now.isBefore(session.getRegistrationOpensAt())
                || now.isAfter(session.getRegistrationClosesAt())) {
            throw conflict("Registration is not currently open for this War Session.");
        }
        if (warParticipantRepository.countByWarSessionId(warSessionId) >= session.getTotalParticipants()) {
            throw conflict("The War Session has reached its participant capacity.");
        }
        if (warParticipantRepository.existsByWarSessionIdAndEmail(warSessionId, email)) {
            throw conflict("This email is already registered for the War Session.");
        }

        ParticipantAccessTokenUtil.GeneratedToken token = ParticipantAccessTokenUtil.generate();
        WarParticipant participant = new WarParticipant();
        participant.setId(UUID.randomUUID());
        participant.setWarSessionId(warSessionId);
        participant.setBatchId(null);
        participant.setEmail(email);
        participant.setAccessTokenHash(token.hash());
        participant.setRegisteredAt(now);
        participant.setQueueEnteredAt(null);
        participant.setQueuePosition(null);
        participant.setStatus(WarParticipantStatus.PREREGISTERED);
        participant.setCreatedAt(now);
        participant.setUpdatedAt(now);

        try {
            WarParticipant registered = warParticipantRepository.insert(participant);
            participantEmailService.registrationAccess(email, session.getName(), token.rawToken());
            return new RegisterParticipantResponse(
                    registered.getId(),
                    registered.getWarSessionId(),
                    registered.getEmail(),
                    registered.getRegisteredAt(),
                    registered.getStatus(),
                    token.rawToken());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("This email is already registered for the War Session.");
        }
    }

    @Transactional
    public void requestAccessLink(UUID warSessionId, RegisterParticipantRequest request) {
        String email = normalizeAndValidateEmail(request.getEmail());
        WarSession session = warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));

        warParticipantRepository.findByWarSessionIdAndEmailForUpdate(warSessionId, email)
                .ifPresent(participant -> {
                    ParticipantAccessTokenUtil.GeneratedToken token = ParticipantAccessTokenUtil.generate();
                    participant.setAccessTokenHash(token.hash());
                    participantEmailService.registrationAccess(email, session.getName(), token.rawToken());
                });
    }

    @Transactional(readOnly = true)
    public ParticipantAccessValidationResponse validateAccess(String accessToken) {
        WarParticipant participant = findByAccessToken(accessToken);
        return new ParticipantAccessValidationResponse(
                true,
                participant.getId(),
                participant.getWarSessionId(),
                participant.getEmail(),
                participant.getStatus());
    }

    @Transactional(readOnly = true)
    public ParticipantProfileResponse getProfile(String accessToken) {
        WarParticipant participant = findByAccessToken(accessToken);
        return toProfile(participant);
    }

    @Transactional(readOnly = true)
    public List<AdminParticipantResponse> getAdminRoster(UUID warSessionId) {
        warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));
        return warParticipantRepository.findAdminRosterByWarSessionId(warSessionId);
    }

    @Transactional(readOnly = true)
    public WarParticipant getAuthenticatedParticipant(String accessToken) {
        return findByAccessToken(accessToken);
    }

    private WarParticipant findByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthorized();
        }
        String hash = ParticipantAccessTokenUtil.hash(accessToken);
        return warParticipantRepository.findByAccessTokenHash(hash)
                .orElseThrow(WarParticipantService::unauthorized);
    }

    private static String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw badRequest("email must not be blank.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalizedEmail.indexOf('@');
        if (atIndex <= 0
                || atIndex != normalizedEmail.lastIndexOf('@')
                || atIndex > 64
                || normalizedEmail.length() > 254
                || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw badRequest("email must be a valid email address.");
        }
        return normalizedEmail;
    }

    private static ParticipantProfileResponse toProfile(WarParticipant participant) {
        return new ParticipantProfileResponse(
                participant.getId(),
                participant.getWarSessionId(),
                participant.getEmail(),
                participant.getStatus());
    }

    private static ResponseStatusException sessionNotFound(UUID warSessionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + warSessionId);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Participant access token is invalid.");
    }
}
