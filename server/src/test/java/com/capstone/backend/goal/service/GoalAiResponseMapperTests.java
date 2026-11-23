package com.capstone.backend.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.goal.dto.GoalDtos.ClarifyStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** NEEDS_SELECTION 폐기 뒤 공개 응답 변환을 검증한다(#315). */
class GoalAiResponseMapperTests {
  private final ObjectMapper json = new ObjectMapper();
  private final GoalAiResponseMapper mapper = new GoalAiResponseMapper();

  @Test
  void aiSelectionBecomesClearWithCandidatesAndNoQuestion() {
    var result = mapper.map(json.readTree("{\"data\":{\"clarityStatus\":\"NEEDS_SELECTION\","
        + "\"interpretedGoal\":null,\"question\":{\"text\":\"하나를 고르세요\"},"
        + "\"recommendedGoals\":[{\"title\":\"리액트 훅 공부\"},{\"title\":\"알고리즘 풀기\"}]}}"),
        0, "7");
    assertEquals(ClarifyStatus.CLEAR, result.clarityStatus());
    assertEquals(List.of("리액트 훅 공부", "알고리즘 풀기"), result.goalCandidates());
    assertNull(result.question());
    assertTrue(result.suggestions().isEmpty());
  }

  @Test
  void selectionNeedsTwoCandidatesAndOtherStatusesStayTheSame() {
    assertThrows(IllegalArgumentException.class, () -> mapper.map(json.readTree(
        "{\"data\":{\"clarityStatus\":\"NEEDS_SELECTION\","
            + "\"recommendedGoals\":[{\"title\":\"하나뿐\"}]}}"), 0, "7"));
    var suggestion = mapper.map(json.readTree("{\"data\":{\"clarityStatus\":\"NEEDS_SUGGESTION\","
        + "\"recommendedGoals\":[{\"title\":\"가\"},{\"title\":\"나\"}]}}"), 1, "7");
    assertEquals(ClarifyStatus.NEEDS_SUGGESTION, suggestion.clarityStatus());
    assertEquals(List.of("가", "나"), suggestion.suggestions());
    assertTrue(suggestion.goalCandidates().isEmpty());
    var clear = mapper.map(json.readTree("{\"data\":{\"clarityStatus\":\"CLEAR\","
        + "\"interpretedGoal\":\"리액트 공부\",\"recommendedGoals\":[]}}"), 0, "7");
    assertTrue(clear.goalCandidates().isEmpty());
  }

  @Test
  void storedLegacySelectionStateReadsAsClear() {
    assertEquals(ClarifyStatus.CLEAR, json.readValue("\"NEEDS_SELECTION\"", ClarifyStatus.class));
    assertEquals(ClarifyStatus.NEEDS_QUESTION,
        json.readValue("\"NEEDS_QUESTION\"", ClarifyStatus.class));
  }
}
