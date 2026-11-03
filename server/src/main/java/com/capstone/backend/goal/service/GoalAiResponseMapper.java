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
    ClarifyStatus status;
    try {
      status = ClarifyStatus.valueOf(data.path("clarityStatus").asText(""));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid goal AI status");
    }
    String text = optionalText(data.path("interpretedGoal"));
    JsonNode questionValue = data.path("question");
    String question = questionValue.isMissingNode() || questionValue.isNull()
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
    List<String> goalCandidates = status == ClarifyStatus.NEEDS_SELECTION
        ? List.copyOf(candidates) : List.of();
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
