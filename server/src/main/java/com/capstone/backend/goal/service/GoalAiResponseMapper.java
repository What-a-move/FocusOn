package com.capstone.backend.goal.service;

import com.capstone.backend.goal.dto.GoalDtos.ClarifyGoalResponse;
import com.capstone.backend.goal.dto.GoalDtos.ClarifyStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 내부 AI 결과를 공개 명세의 상태와 문자열 필드로 변환한다. */
@Component
public class GoalAiResponseMapper {
  /** 검증되지 않은 AI 응답은 성공으로 노출하지 않는다. */
  public ClarifyGoalResponse map(JsonNode response, int turn, String sessionId) {
    if (response == null || !response.path("data").isObject()) {
      throw new IllegalArgumentException("Invalid goal AI response");
    }
    JsonNode data = response.path("data");
    String aiStatus = data.path("clarityStatus").asText("");
    // AI는 아직 NEEDS_SELECTION을 줄 수 있다. 공개 응답은 CLEAR + goalCandidates로 바꾸고
    // 고르라는 질문은 싣지 않는다(#315). 사용자는 후보 중 하나를 바로 저장할 수 있다.
    boolean selection = "NEEDS_SELECTION".equals(aiStatus);
    ClarifyStatus status;
    try {
      status = selection ? ClarifyStatus.CLEAR : ClarifyStatus.valueOf(aiStatus);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid goal AI status");
    }
    final String text = optionalText(data.path("interpretedGoal"));
    JsonNode questionValue = data.path("question");
    final String question = selection || questionValue.isMissingNode() || questionValue.isNull()
        ? null : optionalText(questionValue.path("text"));
    JsonNode goals = data.path("recommendedGoals");
    if (!goals.isMissingNode() && !goals.isArray()) {
      throw new IllegalArgumentException("Invalid goal AI candidates");
    }
    List<String> candidates = new ArrayList<>();
    if (goals.isArray()) {
      for (JsonNode goal : goals) {
        String title = optionalText(goal.path("title"));
        if (title == null || candidates.size() >= 3) {
          throw new IllegalArgumentException("Invalid goal AI candidate");
        }
        candidates.add(title);
      }
    }
    List<String> suggestions = status == ClarifyStatus.NEEDS_SUGGESTION
        ? List.copyOf(candidates) : List.of();
    List<String> goalCandidates = selection ? List.copyOf(candidates) : List.of();
    if (selection && goalCandidates.size() < 2) {
      throw new IllegalArgumentException("Invalid goal AI candidates");
    }
    return new ClarifyGoalResponse(status, text, question, suggestions, goalCandidates,
        turn, sessionId);
  }

  private String optionalText(JsonNode value) {
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("Invalid goal AI text");
    }
    return value.asText();
  }
}
