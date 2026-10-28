package com.capstone.backend.goal.dto;

import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 목표 API 요청과 응답이다. */
public final class GoalDtos {
  private GoalDtos() {}

  /** 목표 저장 요청이다. */
  @Schema(example = "{\"goalText\":\"Spring Security 인증 구현\","
      + "\"confirmationSource\":\"MANUAL_CONFIRMED\"}")
  public record CreateGoalRequest(
      @Schema(example = "Spring Security 인증 구현") String goalText,
      @NotNull ConfirmationSource confirmationSource,
      @Schema(description = "AI 명확화 대화 ID (선택)") String clarificationSessionId) {}

  /** 저장하거나 조회한 목표의 공개 정보다. */
  public record GoalResponse(String goalId, String goalText,
      ConfirmationSource confirmationSource) {}
}
