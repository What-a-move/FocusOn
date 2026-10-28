package com.capstone.backend.goal.service;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.goal.dto.GoalDtos.CreateGoalRequest;
import com.capstone.backend.goal.dto.GoalDtos.GoalResponse;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import com.capstone.backend.goal.persistence.GoalRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증된 사용자의 학습 목표를 저장하고 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {
  private final GoalRepository goals;
  private final UserRepository users;

  /** 수동 확정된 목표를 저장한다. */
  @Transactional
  public GoalResponse create(FocusOnPrincipal principal, CreateGoalRequest request) {
    String text = request.goalText() == null ? "" : request.goalText().strip();
    int length = text.codePointCount(0, text.length());
    if (length < 2 || length > 40) {
      throw new ApiException(ApiErrorCode.GOAL_TEXT_INVALID);
    }
    if (request.confirmationSource() != ConfirmationSource.MANUAL_CONFIRMED
        || request.clarificationSessionId() != null) {
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
    UserEntity user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    return response(goals.save(GoalEntity.create(user, text, request.confirmationSource(),
        Instant.now())));
  }

  /** 다른 사용자의 목표와 존재하지 않는 목표를 동일하게 처리한다. */
  public GoalResponse get(FocusOnPrincipal principal, String goalId) {
    long id;
    try {
      id = Long.parseLong(goalId);
      if (id <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.GOAL_NOT_FOUND);
    }
    return response(goals.findByIdAndUserIdAndDeletedAtIsNull(id, principal.userId())
        .filter(goal -> goal.getStatus() != GoalEntity.Status.DELETED)
        .orElseThrow(() -> new ApiException(ApiErrorCode.GOAL_NOT_FOUND)));
  }

  private GoalResponse response(GoalEntity goal) {
    return new GoalResponse(goal.getId().toString(), goal.getGoalText(),
        goal.getConfirmationSource());
  }
}
