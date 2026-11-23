package com.capstone.backend.goal.service;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
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
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 인증된 사용자의 학습 목표를 저장하고 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;
  private final GoalRepository goals;
  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final GoalClarificationRepository clarifications;
  private final ObjectMapper json;
  private final GoalProfileExpander expander;
  private final TransactionTemplate transactions;

  /**
   * 확정 방식과 명확화 이력을 검증하고 목표를 저장한다. 세분화가 없으면 저장을 끝낸 뒤 트랜잭션
   * 밖에서 Goal AI로 세분화해 붙인다(#314). 세분화가 실패해도 목표는 저장되고 goalProfile은 null이다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public GoalResponse create(FocusOnPrincipal principal, CreateGoalRequest request) {
    GoalEntity saved = transactions.execute(status -> save(principal, request));
    if (saved.getProfileJson() != null) {
      return response(saved);
    }
    return expandAfterCommit(principal.userId(), saved);
  }

  private GoalEntity save(FocusOnPrincipal principal, CreateGoalRequest request) {
    String text = validatedText(request.goalText());
    UserEntity user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = now();
    GoalClarificationSession clarification = findClarification(principal.userId(), request, now);
    GoalEntity goal = GoalEntity.create(user, text, request.confirmationSource(), now);
    boolean aiConfirmedText = false;
    if (clarification != null) {
      if (request.confirmationSource() == ConfirmationSource.AI_CONFIRMED) {
        var result = json.readTree(clarification.getStateJson()).path("response");
        boolean candidate = false;
        for (var value : result.path("goalCandidates")) {
          candidate |= text.equals(value.asText());
        }
        // CLEAR의 확정 문구 또는 함께 받은 후보 중 하나만 AI 확인 목표로 저장한다(#315).
        if (!"CLEAR".equals(result.path("clarityStatus").asText())
            || !(text.equals(result.path("normalizedGoalText").asText()) || candidate)) {
          throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
        }
        aiConfirmedText = text.equals(result.path("normalizedGoalText").asText());
      }
      goal.linkClarification(clarification);
      // 다듬기 세분화는 확정 문구의 것이다. 후보를 고른 목표는 저장 뒤 따로 세분화한다.
      if (request.confirmationSource() == ConfirmationSource.AI_CONFIRMED && aiConfirmedText) {
        // 확정 응답과 같은 대화 상태에 보관한 세분화만 목표에 붙인다.
        var profile = json.readTree(clarification.getStateJson()).path("profile");
        if (profile.isObject()) {
          goal.attachProfile(json.writeValueAsString(profile));
        }
      }
      clarification.confirm(now);
    }
    return goals.save(goal);
  }

  /**
   * 잠금·트랜잭션 밖에서 세분화한 뒤 짧은 트랜잭션으로 붙인다. 그 사이 문구가 바뀌었거나 사용자가
   * 세분화를 직접 넣었거나 목표가 지워졌으면 결과를 버린다.
   */
  private GoalResponse expandAfterCommit(Long userId, GoalEntity goal) {
    String text = goal.getGoalText();
    GoalProfile profile = expander.expand(text);
    if (profile == null) {
      return response(goal);
    }
    String profileJson = json.writeValueAsString(profile);
    return transactions.execute(status -> {
      lockUser(userId);
      var current = goals.findByIdAndUserIdAndDeletedAtIsNull(goal.getId(), userId)
          .filter(value -> value.getStatus() != GoalEntity.Status.DELETED).orElse(null);
      if (current == null) {
        return response(goal);
      }
      if (current.getProfileJson() == null && current.getGoalText().equals(text)) {
        current.attachProfile(profileJson);
      }
      return response(current);
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
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public GoalResponse update(FocusOnPrincipal principal, String goalId, UpdateGoalRequest request) {
    String profileJson = submittedProfile(request.goalProfile());
    boolean profileGiven = request.goalProfile() != null && !request.goalProfile().isNull();
    var result = transactions.execute(status -> applyUpdate(principal.userId(), goalId,
        request, profileGiven, profileJson));
    // 문구만 바뀌었으면 지운 세분화를 다시 만든다. 직접 보낸 세분화가 있으면 그 값을 우선한다.
    return result.reexpand() ? expandAfterCommit(principal.userId(), result.goal())
        : response(result.goal());
  }

  private Updated applyUpdate(Long userId, String goalId, UpdateGoalRequest request,
      boolean profileGiven, String profileJson) {
    if (request.goalText() == null && !profileGiven && request.todoStatus() != null) {
      GoalEntity goal = findGoal(userId, goalId);
      goal.markTodo(request.todoStatus(), now());
      return new Updated(goal, false);
    }
    String text = request.goalText() == null && profileGiven ? null
        : normalizeText(request.goalText());
    if ((text != null && !isValidText(text)) || (profileGiven && profileJson == null)) {
      // 오류 응답의 기존 우선순위는 유지하되 변경이 없는 요청은 행 잠금을 피한다.
      GoalEntity goal = findGoal(userId, goalId);
      requireNotInActiveSession(userId, goal.getId());
      throw new ApiException(text != null && !isValidText(text)
          ? ApiErrorCode.GOAL_TEXT_INVALID : ApiErrorCode.VALIDATION_ERROR);
    }
    lockUser(userId);
    GoalEntity goal = findGoal(userId, goalId);
    requireNotInActiveSession(userId, goal.getId());
    Instant now = now();
    boolean textChanged = text != null && !text.equals(goal.getGoalText());
    if (textChanged) {
      goal.updateText(text, now);
    }
    if (profileGiven) {
      goal.replaceProfile(profileJson, now);
    }
    if (request.todoStatus() != null) {
      goal.markTodo(request.todoStatus(), now);
    }
    // 문구를 다시 보냈는데 이전 세분화가 실패해 비어 있으면 이번에 다시 시도한다.
    boolean missing = text != null && goal.getProfileJson() == null;
    return new Updated(goal, (textChanged || missing) && !profileGiven);
  }

  /** 사용자가 보낸 세분화를 AI 응답과 같은 규칙으로 검증한다. 형식이 틀리면 null이다. */
  private String submittedProfile(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    GoalProfile profile = GoalProfile.fromDraft(value);
    return profile == null ? null : json.writeValueAsString(profile);
  }

  private record Updated(GoalEntity goal, boolean reexpand) {}

  /** 활성 세션이 참조하지 않는 목표만 논리 삭제한다. */
  @Transactional
  public void delete(FocusOnPrincipal principal, String goalId) {
    lockUser(principal.userId());
    GoalEntity goal = findGoal(principal.userId(), goalId);
    requireNotInActiveSession(principal.userId(), goal.getId());
    goal.delete(now());
  }

  /** DB 정밀도(마이크로초)에 맞춘 현재 시각이다. */
  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private void lockUser(Long userId) {
    users.findByIdForUpdate(userId)
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
  }

  private void requireNotInActiveSession(Long userId, Long goalId) {
    if (sessions.existsByUserIdAndGoalIdAndStatusIn(userId, goalId,
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
    return new GoalResponse(goal.getId().toString(), goal.getGoalText(),
        goal.getConfirmationSource(), goal.getTodoStatus(), profile(goal.getProfileJson()));
  }

  /** 저장된 세분화를 다시 검증해 싣는다. 깨진 값은 응답에서 빼고 목표 조회는 막지 않는다. */
  private GoalProfile profile(String profileJson) {
    if (profileJson == null) {
      return null;
    }
    try {
      return GoalProfile.fromDraft(json.readTree(profileJson));
    } catch (RuntimeException exception) {
      return null;
    }
  }
}
