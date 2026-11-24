package com.capstone.backend.goal.service;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.common.api.SafeApiLog.AnalysisEvent;
import com.capstone.backend.goal.dto.GoalDtos.CreateGoalRequest;
import com.capstone.backend.goal.dto.GoalDtos.GoalListResponse;
import com.capstone.backend.goal.dto.GoalDtos.GoalResponse;
import com.capstone.backend.goal.dto.GoalDtos.UpdateGoalRequest;
import com.capstone.backend.goal.persistence.GoalClarificationRepository;
import com.capstone.backend.goal.persistence.GoalClarificationSession;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import com.capstone.backend.goal.persistence.GoalEntity.TodoStatus;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.session.persistence.IdempotencyRecordEntity;
import com.capstone.backend.session.persistence.IdempotencyRecordRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionGoalRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 인증된 사용자의 학습 목표를 저장하고 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;
  private static final String CREATE_SCOPE = "CREATE_GOAL";
  private final GoalRepository goals;
  private final IdempotencyRecordRepository records;
  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final SessionGoalRepository sessionGoals;
  private final GoalClarificationRepository clarifications;
  private final ObjectMapper json;
  private final GoalAiClient goalAi;
  private final TransactionTemplate transactions;

  /** AI에 목표 확장을 요청하는 동안 DB 트랜잭션을 점유하지 않는다. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public GoalResponse create(FocusOnPrincipal principal, CreateGoalRequest request, String rawKey) {
    String text = validatedText(request.goalText());
    GoalProfile edited = GoalProfile.fromDraft(request.goalProfile());
    if (request.goalProfile() != null && !request.goalProfile().isNull() && edited == null) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    CreateKey key = rawKey == null ? null : createKey(rawKey, request);
    var preflight = transactions.execute(status -> {
      if (key != null) {
        lockUser(principal.userId());
        var previous = existing(principal.userId(), key);
        if (previous != null && previous.getExpiresAt().isAfter(Instant.now())) {
          return new Preflight(replay(previous, key), null);
        }
      }
      if (edited != null) {
        return new Preflight(null, edited);
      }
      var clarification = findClarification(principal.userId(), request, Instant.now());
      GoalProfile draft = confirmedProfile(clarification, request.confirmationSource(), text);
      return new Preflight(null, draft);
    });
    if (preflight.replayed() != null) {
      return preflight.replayed();
    }
    GoalProfile selected = preflight.profile() == null ? expand(text) : preflight.profile();
    return transactions.execute(status -> save(principal, request, text, selected, key));
  }

  private GoalResponse save(FocusOnPrincipal principal, CreateGoalRequest request, String text,
      GoalProfile profile, CreateKey key) {
    long userId = principal.userId();
    UserEntity user = key == null ? users.findById(userId)
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED)) : lockUser(userId);
    Instant now = Instant.now();
    if (key != null) {
      var previous = existing(userId, key);
      if (previous != null) {
        if (previous.getExpiresAt().isAfter(now)) {
          return replay(previous, key);
        }
        records.delete(previous);
        records.flush();
      }
    }
    GoalClarificationSession clarification = findClarification(userId, request, now);
    GoalProfile savedDraft = confirmedProfile(clarification, request.confirmationSource(), text);
    GoalEntity goal = GoalEntity.create(user, text, request.confirmationSource(), now);
    if (clarification != null) {
      goal.linkClarification(clarification);
      clarification.confirm(now);
    }
    GoalProfile selected = profile == null ? savedDraft : profile;
    if (selected != null) {
      goal.attachProfile(json.writeValueAsString(selected));
    }
    GoalResponse response = response(goals.save(goal));
    if (key != null) {
      records.save(IdempotencyRecordEntity.create(user, CREATE_SCOPE, key.id(), key.hash(),
          (short) 201, json.writeValueAsString(response), now, now.plus(Duration.ofHours(24))));
    }
    return response;
  }

  private IdempotencyRecordEntity existing(long userId, CreateKey key) {
    return records.findByUserIdAndScopeAndIdempotencyKey(userId, CREATE_SCOPE, key.id())
        .orElse(null);
  }

  private GoalResponse replay(IdempotencyRecordEntity record, CreateKey key) {
    if (!key.hash().equals(record.getRequestHash())) {
      throw new ApiException(ApiErrorCode.IDEMPOTENCY_CONFLICT);
    }
    return json.readValue(record.getResponseBodyJson(), GoalResponse.class);
  }

  private CreateKey createKey(String raw, CreateGoalRequest request) {
    UUID id;
    try {
      id = UUID.fromString(raw);
      if (!id.toString().equalsIgnoreCase(raw)) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    try {
      byte[] body = json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
      return new CreateKey(id, hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private record CreateKey(UUID id, String hash) {}

  private record Preflight(GoalResponse replayed, GoalProfile profile) {}

  private GoalProfile confirmedProfile(GoalClarificationSession clarification,
      ConfirmationSource source, String text) {
    if (clarification == null || source != ConfirmationSource.AI_CONFIRMED) {
      return null;
    }
    var state = json.readTree(clarification.getStateJson());
    var result = state.path("response");
    if (!"CLEAR".equals(result.path("clarityStatus").asText())
        || !text.equals(result.path("normalizedGoalText").asText())) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    return GoalProfile.fromDraft(state.path("profile"));
  }

  /** 목표 생성용 확장이다. 실패하면 범위 없이 저장하도록 null을 돌려준다. */
  private GoalProfile expand(String text) {
    try {
      return requestProfile(text);
    } catch (ApiException failure) {
      return null;
    }
  }

  /**
   * AI 범위를 받아 검증한다. 시간 초과는 GOAL_ANALYSIS_TIMEOUT, 그 밖의 실패·형식 오류는
   * GOAL_ANALYSIS_UNAVAILABLE이다. 트랜잭션 밖에서만 호출한다.
   */
  private GoalProfile requestProfile(String text) {
    long started = System.nanoTime();
    SafeApiLog.goalProfileEvent(AnalysisEvent.AI_START, started);
    long[] retryDelays = {1000, 3000};
    for (int attempt = 0; ; attempt++) {
      try {
        var value = goalAi.profile(text, LocaleContextHolder.getLocale().toLanguageTag());
        GoalProfile profile = value == null ? null
            : GoalProfile.fromDraft(value.path("goalProfile"));
        SafeApiLog.goalProfileEvent(profile == null ? AnalysisEvent.AI_FAIL : AnalysisEvent.AI_OK,
            started);
        if (profile == null) {
          throw new ApiException(ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
        }
        return profile;
      } catch (GoalAiClient.GoalAiException failure) {
        if (!failure.retryable() || attempt >= retryDelays.length) {
          SafeApiLog.goalProfileEvent(AnalysisEvent.AI_FAIL, started);
          throw new ApiException(failure.failure() == GoalAiClient.Failure.TIMEOUT
              ? ApiErrorCode.GOAL_ANALYSIS_TIMEOUT : ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
        }
        try {
          Thread.sleep(retryDelays[attempt]);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          SafeApiLog.goalProfileEvent(AnalysisEvent.AI_FAIL, started);
          throw new ApiException(ApiErrorCode.GOAL_ANALYSIS_UNAVAILABLE);
        }
      }
    }
  }

  /**
   * 저장된 목표 문구로 AI 범위를 새로 만들어 저장한다(#314). AI가 실패하면 기존 범위는 그대로 두고
   * 오류만 돌려준다. 활성 세션이 쓰는 할 일이면 409다. AI 호출 동안 잠금·트랜잭션을 잡지 않으며,
   * 그 사이 문구가 바뀌었으면 새 범위를 버리고 현재 상태를 돌려준다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public GoalResponse regenerateProfile(FocusOnPrincipal principal, String goalId) {
    long userId = principal.userId();
    String text = transactions.execute(status -> {
      GoalEntity goal = findGoal(userId, goalId);
      requireNotInActiveSession(userId, goal.getId());
      return goal.getGoalText();
    });
    GoalProfile profile = requestProfile(text);
    String profileJson = json.writeValueAsString(profile);
    return transactions.execute(status -> {
      lockUser(userId);
      GoalEntity goal = findGoal(userId, goalId);
      requireNotInActiveSession(userId, goal.getId());
      if (goal.getGoalText().equals(text)) {
        goal.replaceProfile(profileJson, Instant.now());
      }
      return response(goal);
    });
  }

  private GoalClarificationSession findClarification(Long userId, CreateGoalRequest request,
      Instant now) {
    String rawId = request.clarificationSessionId();
    if (rawId == null) {
      if (request.confirmationSource() == ConfirmationSource.AI_CONFIRMED) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
      return null;
    }
    long id;
    try {
      id = Long.parseLong(rawId);
      if (id <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.NOT_FOUND);
    }
    GoalClarificationSession session = clarifications.findOwnedForUpdate(id, userId)
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
    if (session.getStatus() != GoalClarificationSession.Status.READY
        || !session.getExpiresAt().isAfter(now)) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    return session;
  }

  /** 다른 사용자의 목표와 존재하지 않는 목표를 동일하게 처리한다. */
  public GoalResponse get(FocusOnPrincipal principal, String goalId) {
    return response(findGoal(principal.userId(), goalId));
  }

  /**
   * 본인 할 일 목록을 최신순으로 돌려준다(#313). nextCursor는 불투명 문자열이며 그대로 cursor로
   * 보내면 다음 페이지다. 잘못된 limit·cursor는 VALIDATION_ERROR다.
   */
  public GoalListResponse list(FocusOnPrincipal principal, TodoStatus todoStatus, String cursor,
      Integer requestedLimit) {
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    long idBefore = Long.MAX_VALUE;
    if (cursor != null) {
      try {
        idBefore = Long.parseLong(cursor);
      } catch (NumberFormatException exception) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
      if (idBefore <= 0) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    }
    var rows = goals.findTodoPage(principal.userId(), todoStatus, idBefore,
        PageRequest.of(0, limit + 1));
    var page = rows.subList(0, Math.min(limit, rows.size()));
    String next = rows.size() > limit ? page.getLast().getId().toString() : null;
    return new GoalListResponse(page.stream().map(this::response).toList(), next);
  }

  /**
   * 본인 목표를 활성 세션과 충돌하지 않도록 수정한다. 할 일 상태만 바꾸는 요청은 활성 세션이
   * 참조 중이어도 허용한다(#313). 문구가 함께 오면 기존 규칙을 그대로 따른다.
   */
  @Transactional
  public GoalResponse update(FocusOnPrincipal principal, String goalId, UpdateGoalRequest request) {
    boolean profileGiven = request.goalProfile() != null && !request.goalProfile().isNull();
    if (request.goalText() == null && !profileGiven && request.todoStatus() != null) {
      GoalEntity goal = findGoal(principal.userId(), goalId);
      goal.markTodo(request.todoStatus(), Instant.now());
      return response(goal);
    }
    // 범위만 보내면 문구는 그대로 둔다. 범위는 만들 때와 같은 규칙으로 검증한다(#314).
    String text = request.goalText() == null && profileGiven ? null
        : normalizeText(request.goalText());
    GoalProfile profile = profileGiven ? GoalProfile.fromDraft(request.goalProfile()) : null;
    boolean textInvalid = text != null && !isValidText(text);
    if (textInvalid || (profileGiven && profile == null)) {
      // 오류 응답의 기존 우선순위는 유지하되 변경이 없는 요청은 행 잠금을 피한다.
      GoalEntity goal = findGoal(principal.userId(), goalId);
      requireNotInActiveSession(principal.userId(), goal.getId());
      throw new ApiException(textInvalid ? ApiErrorCode.GOAL_TEXT_INVALID
          : ApiErrorCode.VALIDATION_ERROR);
    }
    lockUser(principal.userId());
    GoalEntity goal = findGoal(principal.userId(), goalId);
    requireNotInActiveSession(principal.userId(), goal.getId());
    Instant now = Instant.now();
    // 같은 문구를 다시 보내면 범위를 지우지 않는다. 문구가 바뀌면 이전 범위는 맞지 않아 비운다.
    if (text != null && !text.equals(goal.getGoalText())) {
      goal.updateText(text, now);
    }
    if (profile != null) {
      goal.replaceProfile(json.writeValueAsString(profile), now);
    }
    if (request.todoStatus() != null) {
      goal.markTodo(request.todoStatus(), now);
    }
    return response(goal);
  }

  /** 활성 세션이 참조하지 않는 목표만 논리 삭제한다. */
  @Transactional
  public void delete(FocusOnPrincipal principal, String goalId) {
    lockUser(principal.userId());
    GoalEntity goal = findGoal(principal.userId(), goalId);
    requireNotInActiveSession(principal.userId(), goal.getId());
    goal.delete(Instant.now());
  }

  private UserEntity lockUser(Long userId) {
    return users.findByIdForUpdate(userId)
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
  }

  private void requireNotInActiveSession(Long userId, Long goalId) {
    if (sessions.existsByUserIdAndGoalIdAndStatusIn(userId, goalId,
        SessionStatus.activeStatuses()) || sessionGoals.referencedByActiveSession(userId, goalId,
        SessionStatus.activeStatuses())) {
      throw new ApiException(ApiErrorCode.GOAL_UPDATE_BLOCKED);
    }
  }

  private GoalEntity findGoal(Long userId, String goalId) {
    long id;
    try {
      id = Long.parseLong(goalId);
      if (id <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.GOAL_NOT_FOUND);
    }
    return goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .filter(goal -> goal.getStatus() != GoalEntity.Status.DELETED)
        .orElseThrow(() -> new ApiException(ApiErrorCode.GOAL_NOT_FOUND));
  }

  private String validatedText(String value) {
    String text = normalizeText(value);
    if (!isValidText(text)) {
      throw new ApiException(ApiErrorCode.GOAL_TEXT_INVALID);
    }
    return text;
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.strip();
  }

  private boolean isValidText(String text) {
    int length = text.codePointCount(0, text.length());
    return length >= 2 && length <= 40;
  }

  private GoalResponse response(GoalEntity goal) {
    GoalProfile profile = goal.getProfileJson() == null ? null
        : GoalProfile.fromDraft(json.readTree(goal.getProfileJson()));
    return new GoalResponse(goal.getId().toString(), goal.getGoalText(),
        goal.getConfirmationSource(), goal.getTodoStatus(), profile);
  }
}
