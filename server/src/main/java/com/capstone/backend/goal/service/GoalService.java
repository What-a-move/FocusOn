package com.capstone.backend.goal.service;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.goal.dto.GoalDtos.CreateGoalRequest;
import com.capstone.backend.goal.dto.GoalDtos.GoalResponse;
import com.capstone.backend.goal.dto.GoalDtos.UpdateGoalRequest;
import com.capstone.backend.goal.persistence.GoalClarificationRepository;
import com.capstone.backend.goal.persistence.GoalClarificationSession;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 인증된 사용자의 학습 목표를 저장하고 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {
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

  /** 본인 목표를 활성 세션과 충돌하지 않도록 수정한다. */
  @Transactional
  public GoalResponse update(FocusOnPrincipal principal, String goalId, UpdateGoalRequest request) {
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
    goal.updateText(text, Instant.now());
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
        goal.getConfirmationSource());
  }
}
