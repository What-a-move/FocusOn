package com.capstone.backend.goal.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 사용자가 편집할 수 있는 목표 세분화다(DR-030). 원문 질문·답변이나 화면 내용은 담지 않는다.
 */
public record GoalProfile(String mainTopic, String purpose, List<String> coreTopics,
    List<String> supportingTopics, List<String> expectedActivities) {
  static final int MAX_ITEMS = 5;
  static final int MAX_LENGTH = 100;
  /** AI 계약(`GoalPurpose`)과 같은 목적 값이다. */
  static final Set<String> PURPOSES = Set.of("CONCEPT_LEARNING", "IMPLEMENTATION",
      "PROBLEM_SOLVING", "DEBUGGING", "PRACTICE", "PRESENTATION_OR_ASSIGNMENT", "OTHER");

  /** 저장 전 목록을 복사해 이후 변경을 막는다. */
  public GoalProfile {
    coreTopics = List.copyOf(coreTopics);
    supportingTopics = List.copyOf(supportingTopics);
    expectedActivities = List.copyOf(expectedActivities);
  }

  /**
   * AI의 `goalProfileDraft`를 검증한다. 형식이 어긋나면 null을 돌려 목표 다듬기와 목표 생성은
   * 그대로 진행한다(세분화는 판정 보조일 뿐 시작을 막지 않는다).
   */
  public static GoalProfile fromDraft(JsonNode draft) {
    if (draft == null || !draft.isObject()) {
      return null;
    }
    String mainTopic = text(draft.path("mainTopic"));
    String purpose = draft.path("purpose").isString() ? draft.path("purpose").asString() : null;
    List<String> core = list(draft.path("coreTopics"));
    List<String> supporting = list(draft.path("supportingTopics"));
    List<String> activities = list(draft.path("expectedActivities"));
    if (mainTopic == null || purpose == null || !PURPOSES.contains(purpose) || core == null
        || supporting == null || activities == null) {
      return null;
    }
    return new GoalProfile(mainTopic, purpose, core, supporting, activities);
  }

  private static String text(JsonNode value) {
    if (!value.isString()) {
      return null;
    }
    String text = value.asString().strip();
    return text.isEmpty() || text.length() > MAX_LENGTH ? null : text;
  }

  private static List<String> list(JsonNode values) {
    if (values.isMissingNode() || values.isNull()) {
      return List.of();
    }
    if (!values.isArray() || values.size() > MAX_ITEMS) {
      return null;
    }
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode value : values) {
      String text = text(value);
      if (text == null || !seen.add(text.toLowerCase(Locale.ROOT))) {
        return null;
      }
      result.add(text);
    }
    return result;
  }
}
