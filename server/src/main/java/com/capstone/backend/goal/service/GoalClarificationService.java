package com.capstone.backend.goal.service;

import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.goal.dto.GoalDtos.ClarificationAnswer;
import com.capstone.backend.goal.dto.GoalDtos.ClarifyGoalRequest;
import com.capstone.backend.goal.dto.GoalDtos.ClarifyGoalResponse;
import com.capstone.backend.goal.persistence.GoalClarificationRepository;
import com.capstone.backend.goal.persistence.GoalClarificationSession;
import com.capstone.backend.goal.persistence.GoalClarificationSession.Status;
import com.capstone.backend.goal.service.GoalAiClient.GoalAiException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** AI 호출과 사용자별 명확화 이력의 상태 전환을 조정한다. */
@Service
@RequiredArgsConstructor
public class GoalClarificationService {
  private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);
  private static final long[] RETRY_DELAYS_MS = {1000, 3000};
  private final GoalClarificationRepository sessions;
  private final UserRepository users;
  private final GoalAiClient client;
  private final GoalAiResponseMapper mapper;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;

  /** 대화 이력을 검증한 뒤 AI 응답을 저장한다. */
  public ClarifyGoalResponse clarify(FocusOnPrincipal principal, ClarifyGoalRequest request) {
    validate(request);
    Claim claim = transactions.execute(status -> claim(principal.userId(), request));
    if (claim.cached() != null) {
      return claim.cached();
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("requestId", UUID.randomUUID().toString());
      body.put("goalText", claim.originalText());
      body.put("conversationTurn", request.conversationTurn());
      body.put("previousAnswers", request.previousAnswers());
      body.put("clarificationSessionId", claim.id().toString());
      if (!claim.originalText().equals(request.goalText().strip())) {
        body.put("selectedGoalText", request.goalText().strip());
      }
      ClarifyGoalResponse result = mapper.map(callWithRetry(body), request.conversationTurn(),
          claim.id().toString());
      if (request.conversationTurn() >= 2 && result.question() != null) {
        throw new IllegalArgumentException("Goal AI exceeded question limit");
      }
      transactions.executeWithoutResult(
          status -> finish(principal.userId(), claim, request, result));
      return result;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> release(principal.userId(), claim));
      if (exception instanceof GoalAiException failure) {
        throw new ApiException(failure.failure() == GoalAiClient.Failure.TIMEOUT
            ? ApiErrorCode.GOAL_ANALYSIS_TIMEOUT : ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
      }
      if (exception instanceof IllegalArgumentException) {
        throw new ApiException(ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
      }
      throw exception;
    }
  }

  private void validate(ClarifyGoalRequest request) {
    String text = request.goalText().strip();
    int size = text.codePointCount(0, text.length());
    if (size < 2 || size > 40 || request.conversationTurn() < 0
        || request.conversationTurn() > 2
        || request.conversationTurn() != request.previousAnswers().size()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    for (ClarificationAnswer answer : request.previousAnswers()) {
      if (answer.question().length() > 1000 || answer.answer().length() > 1000) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    }
  }

  private Claim claim(Long userId, ClarifyGoalRequest request) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    if (request.clarificationSessionId() == null) {
      if (request.conversationTurn() != 0) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
      var user = users.findById(userId)
          .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
      GoalClarificationSession created = sessions.saveAndFlush(
          GoalClarificationSession.create(user, request.goalText().strip(), now));
      return new Claim(created.getId(), created.getGoalText(), now, null, true);
    }
    GoalClarificationSession session = findOwned(userId, request.clarificationSessionId());
    if (!session.getExpiresAt().isAfter(now) || session.getStatus() == Status.CONFIRMED) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    if (session.getStatus() == Status.PROCESSING
        && session.getUpdatedAt().plus(PROCESSING_LEASE).isAfter(now)) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
    ConversationState previous = json.readValue(session.getStateJson(), ConversationState.class);
    if (previous.response() != null && previous.request().equals(request)) {
      return new Claim(session.getId(), session.getGoalText(), now, previous.response(), false);
    }
    validateContinuation(session, previous, request);
    session.begin(now);
    return new Claim(session.getId(), session.getGoalText(), now, null, false);
  }

  private void validateContinuation(GoalClarificationSession session, ConversationState state,
      ClarifyGoalRequest request) {
    if (state.response() == null || state.request() == null) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    List<ClarificationAnswer> oldAnswers = state.request().previousAnswers();
    List<ClarificationAnswer> answers = request.previousAnswers();
    boolean answerAdded = answers.size() == oldAnswers.size() + 1
        && answers.subList(0, oldAnswers.size()).equals(oldAnswers)
        && state.response().question() != null
        && state.response().question().equals(answers.get(answers.size() - 1).question());
    boolean selected = answers.equals(oldAnswers)
        && (state.response().suggestions().contains(request.goalText().strip())
        || state.response().goalCandidates().contains(request.goalText().strip()));
    if ((!answerAdded && !selected) || (answerAdded
        && !request.goalText().strip().equals(state.request().goalText().strip()))) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }

  private void finish(Long userId, Claim claim, ClarifyGoalRequest request,
      ClarifyGoalResponse response) {
    GoalClarificationSession session = findOwned(userId, claim.id().toString());
    if (session.getStatus() != Status.PROCESSING
        || !session.getUpdatedAt().equals(claim.claimedAt())
        || !session.getExpiresAt().isAfter(Instant.now())) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    session.complete(json.writeValueAsString(new ConversationState(request, response)),
        request.conversationTurn(), Instant.now());
  }

  private void release(Long userId, Claim claim) {
    GoalClarificationSession session = findOwned(userId, claim.id().toString());
    if (session.getStatus() == Status.PROCESSING
        && session.getUpdatedAt().equals(claim.claimedAt())) {
      if (claim.newSession()) {
        sessions.delete(session);
      } else {
        session.release(Instant.now());
      }
    }
  }

  private GoalClarificationSession findOwned(Long userId, String rawId) {
    try {
      long id = Long.parseLong(rawId);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return sessions.findOwnedForUpdate(id, userId)
          .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.NOT_FOUND);
    }
  }

  private tools.jackson.databind.JsonNode callWithRetry(Map<String, Object> body) {
    for (int attempt = 0; ; attempt++) {
      try {
        return client.clarify(body);
      } catch (GoalAiException exception) {
        if (!exception.retryable() || attempt >= RETRY_DELAYS_MS.length) {
          throw exception;
        }
        try {
          Thread.sleep(RETRY_DELAYS_MS[attempt]);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new ApiException(ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
        }
      }
    }
  }

  private record Claim(Long id, String originalText, Instant claimedAt,
      ClarifyGoalResponse cached, boolean newSession) {}

  private record ConversationState(ClarifyGoalRequest request, ClarifyGoalResponse response) {}
}
