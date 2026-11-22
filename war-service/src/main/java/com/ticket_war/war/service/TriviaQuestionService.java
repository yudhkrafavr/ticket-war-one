package com.ticket_war.war.service;

import com.ticket_war.war.dto.request.CreateTriviaQuestionRequest;
import com.ticket_war.war.dto.request.TriviaOptionRequest;
import com.ticket_war.war.dto.request.UpdateTriviaQuestionRequest;
import com.ticket_war.war.dto.response.TriviaOptionResponse;
import com.ticket_war.war.dto.response.TriviaQuestionResponse;
import com.ticket_war.war.entity.TriviaOption;
import com.ticket_war.war.entity.TriviaQuestion;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.repository.TriviaQuestionRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TriviaQuestionService {

    private final WarSessionRepository warSessionRepository;
    private final TriviaQuestionRepository triviaQuestionRepository;

    public TriviaQuestionService(
            WarSessionRepository warSessionRepository,
            TriviaQuestionRepository triviaQuestionRepository) {
        this.warSessionRepository = warSessionRepository;
        this.triviaQuestionRepository = triviaQuestionRepository;
    }

    @Transactional
    public TriviaQuestionResponse create(UUID warSessionId, CreateTriviaQuestionRequest request) {
        getDraftSessionForUpdate(warSessionId);
        validateOptions(request.getOptions());
        if (triviaQuestionRepository.existsQuestionOrder(warSessionId, request.getQuestionOrder())) {
            throw conflict("questionOrder already exists in this War Session.");
        }

        Instant now = Instant.now();
        TriviaQuestion question = new TriviaQuestion();
        question.setId(UUID.randomUUID());
        question.setWarSessionId(warSessionId);
        question.setQuestionText(request.getQuestionText());
        question.setQuestionOrder(request.getQuestionOrder());
        question.setCreatedAt(now);
        question.setUpdatedAt(now);

        try {
            question = triviaQuestionRepository.insertQuestion(question);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("questionOrder already exists in this War Session.");
        }

        List<TriviaOption> options = insertOptions(question.getId(), request.getOptions(), now);
        return toResponse(question, options);
    }

    @Transactional(readOnly = true)
    public List<TriviaQuestionResponse> findAll(UUID warSessionId) {
        getSession(warSessionId);
        List<TriviaQuestion> questions = triviaQuestionRepository.findAllByWarSessionId(warSessionId);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<UUID> questionIds = questions.stream().map(TriviaQuestion::getId).toList();
        Map<UUID, List<TriviaOption>> optionsByQuestion =
                triviaQuestionRepository.findOptionsByQuestionIds(questionIds).stream()
                        .collect(Collectors.groupingBy(TriviaOption::getQuestionId));

        return questions.stream()
                .map(question -> toResponse(question,
                        optionsByQuestion.getOrDefault(question.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TriviaQuestionResponse findById(UUID warSessionId, UUID questionId) {
        getSession(warSessionId);
        TriviaQuestion question = getQuestion(warSessionId, questionId);
        List<TriviaOption> options = triviaQuestionRepository.findOptionsByQuestionId(questionId);
        return toResponse(question, options);
    }

    @Transactional
    public TriviaQuestionResponse update(
            UUID warSessionId,
            UUID questionId,
            UpdateTriviaQuestionRequest request) {
        getDraftSessionForUpdate(warSessionId);
        TriviaQuestion question = getQuestion(warSessionId, questionId);
        validateOptions(request.getOptions());
        if (triviaQuestionRepository.existsQuestionOrderExcluding(
                warSessionId, request.getQuestionOrder(), questionId)) {
            throw conflict("questionOrder already exists in this War Session.");
        }

        question.setQuestionText(request.getQuestionText());
        question.setQuestionOrder(request.getQuestionOrder());
        question.setUpdatedAt(Instant.now());
        if (triviaQuestionRepository.updateQuestion(question) != 1) {
            throw conflict("The question was modified concurrently. Please retry the request.");
        }

        Instant now = Instant.now();
        triviaQuestionRepository.deleteOptionsByQuestionId(questionId);
        List<TriviaOption> options = insertOptions(questionId, request.getOptions(), now);
        return toResponse(question, options);
    }

    @Transactional
    public void delete(UUID warSessionId, UUID questionId) {
        getDraftSessionForUpdate(warSessionId);
        getQuestion(warSessionId, questionId);
        triviaQuestionRepository.deleteOptionsByQuestionId(questionId);
        if (triviaQuestionRepository.deleteQuestion(questionId, warSessionId) != 1) {
            throw conflict("The question was modified concurrently. Please retry the request.");
        }
    }

    private List<TriviaOption> insertOptions(
            UUID questionId,
            List<TriviaOptionRequest> requests,
            Instant now) {
        List<TriviaOption> options = new ArrayList<>();
        for (TriviaOptionRequest request : requests) {
            if (triviaQuestionRepository.existsOptionOrder(questionId, request.getOptionOrder())) {
                throw badRequest("optionOrder must be unique within the question.");
            }
            TriviaOption option = new TriviaOption();
            option.setId(UUID.randomUUID());
            option.setQuestionId(questionId);
            option.setOptionText(request.getOptionText());
            option.setOptionOrder(request.getOptionOrder());
            option.setIsCorrect(request.getIsCorrect());
            option.setCreatedAt(now);
            option.setUpdatedAt(now);
            try {
                if (triviaQuestionRepository.insertOption(option) != 1) {
                    throw new IllegalStateException("Trivia option could not be inserted.");
                }
            } catch (DataIntegrityViolationException exception) {
                throw badRequest("optionOrder must be unique within the question.");
            }
            options.add(option);
        }
        options.sort(Comparator.comparing(TriviaOption::getOptionOrder));
        return options;
    }

    private static void validateOptions(List<TriviaOptionRequest> options) {
        if (options == null || options.size() < 2) {
            throw badRequest("At least two options are required for every question.");
        }

        long correctOptionCount = 0;
        List<Integer> optionOrders = new ArrayList<>();
        for (TriviaOptionRequest option : options) {
            if (option == null
                    || option.getOptionText() == null
                    || option.getOptionText().isBlank()
                    || option.getOptionText().length() > 500
                    || option.getOptionOrder() == null
                    || option.getOptionOrder() <= 0
                    || option.getIsCorrect() == null) {
                throw badRequest("Each option requires non-blank text (maximum 500 characters), a positive optionOrder, and isCorrect.");
            }
            optionOrders.add(option.getOptionOrder());
            if (Boolean.TRUE.equals(option.getIsCorrect())) {
                correctOptionCount++;
            }
        }

        if (optionOrders.stream().distinct().count() != optionOrders.size()) {
            throw badRequest("optionOrder must be unique within the question.");
        }
        if (correctOptionCount != 1) {
            throw badRequest("Every question must have exactly one correct option.");
        }
    }

    private WarSession getDraftSessionForUpdate(UUID warSessionId) {
        WarSession session = warSessionRepository.findByIdForUpdate(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));
        if (session.getStatus() != WarSessionStatus.DRAFT) {
            throw conflict("Questions can only be modified while the War Session is in DRAFT status.");
        }
        return session;
    }

    private WarSession getSession(UUID warSessionId) {
        return warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));
    }

    private TriviaQuestion getQuestion(UUID warSessionId, UUID questionId) {
        return triviaQuestionRepository.findByIdAndWarSessionId(questionId, warSessionId)
                .orElseThrow(() -> questionNotFound(warSessionId, questionId));
    }

    private static TriviaQuestionResponse toResponse(
            TriviaQuestion question,
            List<TriviaOption> options) {
        List<TriviaOptionResponse> optionResponses = options.stream()
                .sorted(Comparator.comparing(TriviaOption::getOptionOrder))
                .map(option -> new TriviaOptionResponse(
                        option.getId(),
                        option.getOptionText(),
                        option.getOptionOrder(),
                        option.getIsCorrect()))
                .toList();
        return new TriviaQuestionResponse(
                question.getId(),
                question.getWarSessionId(),
                question.getQuestionText(),
                question.getQuestionOrder(),
                optionResponses,
                question.getCreatedAt(),
                question.getUpdatedAt());
    }

    private static ResponseStatusException sessionNotFound(UUID warSessionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + warSessionId);
    }

    private static ResponseStatusException questionNotFound(UUID warSessionId, UUID questionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Trivia Question " + questionId + " not found under War Session " + warSessionId + ".");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
