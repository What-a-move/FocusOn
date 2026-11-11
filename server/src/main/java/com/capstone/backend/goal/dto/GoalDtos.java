package com.capstone.backend.goal.dto;

import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

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

  /** 목표 문구 수정 요청이다. */
  @Schema(example = "{\"goalText\":\"Spring Security 인증 복습\"}")
  public record UpdateGoalRequest(
      @Schema(example = "Spring Security 인증 복습") String goalText) {}

  /** 사용자가 이전에 받은 질문과 답변이다. */
  public record ClarificationAnswer(@NotBlank String question, @NotBlank String answer) {}

  /** 목표 명확화 대화의 현재 입력이다. */
  @Schema(example = "{\"goalText\":\"Spring Security 공부\",\"conversationTurn\":0,"
      + "\"previousAnswers\":[]}")
  public record ClarifyGoalRequest(@NotBlank String goalText,
      @NotNull Integer conversationTurn, String clarificationSessionId,
      @NotNull @Size(max = 2) List<@NotNull @Valid ClarificationAnswer> previousAnswers) {}

  /** 사용자에게 표시할 공개 명확성 판단이다. */
  public enum ClarifyStatus {
    CLEAR, NEEDS_SELECTION, NEEDS_SUGGESTION, NEEDS_QUESTION, UNRECOGNIZED_TERM, INVALID
  }

  /** 목표 명확화 응답과 서버가 관리하는 대화 ID다. */
  public record ClarifyGoalResponse(ClarifyStatus clarityStatus, String normalizedGoalText,
      String question, List<String> suggestions, List<String> goalCandidates,
      int conversationTurn, String clarificationSessionId) {}

  /** 저장하거나 조회한 목표의 공개 정보다. */
  public record GoalResponse(String goalId, String goalText,
      ConfirmationSource confirmationSource) {}
}
