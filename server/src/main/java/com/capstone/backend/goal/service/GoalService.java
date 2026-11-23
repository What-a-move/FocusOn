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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  /** 확정 방식과 명확화 이력을 검증하고 목표를 저장한다. */
  @Transactional
  public GoalResponse create(FocusOnPrincipal principal, CreateGoalRequest request) {
    String text = validatedText(request.goalText());
    UserEntity user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    Instant now = Instant.now();
    GoalClarificationSession clarification = findClarification(principal.userId(), request, now);
    GoalEntity goal = GoalEntity.create(user, text, request.confirmationSource(), now);
    if (clarification != null) {
      if (request.confirmationSource() == ConfirmationSource.AI_CONFIRMED) {
        var result = json.readTree(clarification.getStateJson()).path("response");
        if (!"CLEAR".equals(result.path("clarityStatus").asText())
            || !text.equals(result.path("normalizedGoalText").asText())) {
          throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
        }
      }
      goal.linkClarification(clarification);
      if (request.confirmationSource() == ConfirmationSource.AI_CONFIRMED) {
        // 확정 응답과 같은 대화 상태에 보관한 세분화만 목표에 붙인다.
        var profile = json.readTree(clarification.getStateJson()).path("profile");
        if (profile.isObject()) {
          goal.attachProfile(json.writeValueAsString(profile));
        }
      }
      clarification.confirm(now);
    }
    return response(goals.save(goal));
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
    if (request.goalText() == null && request.todoStatus() != null) {
      GoalEntity goal = findGoal(principal.userId(), goalId);
      goal.markTodo(request.todoStatus(), Instant.now());
      return response(goal);
    }
    String text = normalizeText(request.goalText());
    if (!isValidText(text)) {
      // 오류 응답의 기존 우선순위는 유지하되 변경이 없는 요청은 행 잠금을 피한다.
      GoalEntity goal = findGoal(principal.userId(), goalId);
      requireNotInActiveSession(principal.userId(), goal.getId());
      throw new ApiException(ApiErrorCode.GOAL_TEXT_INVALID);
    }
    lockUser(principal.userId());
    GoalEntity goal = findGoal(principal.userId(), goalId);
    requireNotInActiveSession(principal.userId(), goal.getId());
    Instant now = Instant.now();
    goal.updateText(text, now);
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
        goal.getConfirmationSource(), goal.getTodoStatus());
  }
}
