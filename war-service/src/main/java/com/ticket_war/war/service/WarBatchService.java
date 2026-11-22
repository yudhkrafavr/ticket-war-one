package com.ticket_war.war.service;

import com.ticket_war.war.dto.response.WarBatchResponse;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarBatchStatus;
import com.ticket_war.war.repository.WarBatchRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WarBatchService {

    private final WarSessionRepository warSessionRepository;
    private final WarBatchRepository warBatchRepository;

    public WarBatchService(
            WarSessionRepository warSessionRepository,
            WarBatchRepository warBatchRepository) {
        this.warSessionRepository = warSessionRepository;
        this.warBatchRepository = warBatchRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "warSessionLists", "warSessionDetails", "warBatchLists", "warBatchDetails"
    }, allEntries = true)
    public void configureForSession(WarSession session) {
        UUID warSessionId = session.getId();
        int totalBatches = session.getTotalBatches();
        Map<Integer, WarBatch> existingByNumber = new HashMap<>();
        for (WarBatch batch : warBatchRepository.findAllForUpdateByWarSessionId(warSessionId)) {
            existingByNumber.put(batch.getBatchNumber(), batch);
        }

        Instant now = Instant.now();
        for (int batchNumber = 1; batchNumber <= totalBatches; batchNumber++) {
            int participantCapacity = shareAcrossBatches(
                    session.getTotalParticipants(), totalBatches, batchNumber);
            int ticketQuota = shareAcrossBatches(
                    session.getTotalTickets(), totalBatches, batchNumber);
            WarBatch batch = existingByNumber.remove(batchNumber);

            if (batch == null) {
                batch = new WarBatch();
                batch.setId(UUID.randomUUID());
                batch.setWarSessionId(warSessionId);
                batch.setBatchNumber(batchNumber);
                batch.setParticipantCapacity(participantCapacity);
                batch.setTicketQuota(ticketQuota);
                batch.setStatus(WarBatchStatus.PENDING);
                batch.setCreatedAt(now);
                batch.setUpdatedAt(now);
                try {
                    warBatchRepository.insert(batch);
                } catch (DataIntegrityViolationException exception) {
                    throw conflict("Batch configuration changed concurrently. Please retry the request.");
                }
                continue;
            }

            batch.setParticipantCapacity(participantCapacity);
            batch.setTicketQuota(ticketQuota);
            batch.setUpdatedAt(now);
            if (warBatchRepository.update(batch) != 1) {
                throw conflict("A batch was modified concurrently. Please retry the request.");
            }
        }

        for (WarBatch obsoleteBatch : existingByNumber.values()) {
            try {
                if (warBatchRepository.delete(obsoleteBatch.getId(), warSessionId) != 1) {
                    throw conflict("A batch was modified concurrently. Please retry the request.");
                }
            } catch (DataIntegrityViolationException exception) {
                throw conflict("The batch count cannot be reduced because an existing batch is in use.");
            }
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "warBatchLists", key = "#p0")
    public List<WarBatchResponse> findAll(UUID warSessionId) {
        getSession(warSessionId);
        return warBatchRepository.findAllByWarSessionId(warSessionId).stream()
                .map(WarBatchService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "warBatchDetails", key = "#p0 + ':' + #p1")
    public WarBatchResponse findById(UUID warSessionId, UUID batchId) {
        getSession(warSessionId);
        return toResponse(warBatchRepository.findByIdAndWarSessionId(batchId, warSessionId)
                .orElseThrow(() -> batchNotFound(warSessionId, batchId)));
    }

    private WarSession getSession(UUID warSessionId) {
        return warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));
    }

    private static int shareAcrossBatches(int total, int batchCount, int batchNumber) {
        int baseShare = total / batchCount;
        int remainder = total % batchCount;
        return baseShare + (batchNumber <= remainder ? 1 : 0);
    }

    private static WarBatchResponse toResponse(WarBatch batch) {
        return new WarBatchResponse(
                batch.getId(),
                batch.getWarSessionId(),
                batch.getBatchNumber(),
                batch.getParticipantCapacity(),
                batch.getTicketQuota(),
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getUpdatedAt());
    }

    private static ResponseStatusException sessionNotFound(UUID warSessionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + warSessionId);
    }

    private static ResponseStatusException batchNotFound(UUID warSessionId, UUID batchId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Batch " + batchId + " not found under War Session " + warSessionId + ".");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
