package com.capstone.backend.goal.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 목표 한 줄을 Goal AI로 세분화한다(#314). 세분화는 판정 보조 값이라 AI 미설정·장애·시간 초과·형식
 * 오류는 모두 null로 돌려 목표 저장·수정을 막지 않는다. 트랜잭션·잠금 밖에서만 호출한다.
 */
@Component
@RequiredArgsConstructor
public class GoalProfileExpander {
  private final GoalAiClient client;

  /** 실패하면 null이다. 목표 문구 외의 사용자 정보는 보내지 않는다. */
  public GoalProfile expand(String goalText) {
    try {
      JsonNode response = client.profile(Map.of("requestId", UUID.randomUUID().toString(),
          "goalText", goalText));
      JsonNode data = response.path("data");
      JsonNode draft = data.has("goalProfile") ? data.path("goalProfile")
          : data.path("goalProfileDraft");
      return GoalProfile.fromDraft(draft);
    } catch (RuntimeException exception) {
      // AI 응답 원문·예외 메시지에는 입력이 섞일 수 있어 남기지 않는다.
      return null;
    }
  }
}
