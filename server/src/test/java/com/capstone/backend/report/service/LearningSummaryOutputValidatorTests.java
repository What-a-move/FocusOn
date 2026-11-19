package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/** 저장 직전 Schema·길이·민감값 검사가 결과 전체를 버리는지 확인한다. */
class LearningSummaryOutputValidatorTests {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void acceptsContractShapeAndKeepsValuesUnchanged() {
    var result = LearningSummaryOutputValidator.validate(JSON.readTree("""
        {"overview":"트랜잭션 격리 수준 자료를 살펴봤습니다.",
         "keyConcepts":[{"name":"격리 수준","briefExplanation":"동시 실행 트랜잭션의 가시성 규칙",
           "sessionEvidence":"관련 문서 열람"},{"name":"MVCC","briefExplanation":"버전별 읽기"}],
         "nextStartPrompt":"격리 수준 예시를 다시 살펴보세요.",
         "modelVersion":"m-1","promptVersion":"p-1"}
        """)).orElseThrow();
    assertEquals("트랜잭션 격리 수준 자료를 살펴봤습니다.", result.overview());
    assertEquals(2, result.keyConcepts().size());
    assertEquals("관련 문서 열람", result.keyConcepts().get(0).sessionEvidence());
    assertNull(result.keyConcepts().get(1).sessionEvidence());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"overview\":\"o\",\"keyConcepts\":[],\"nextStartPrompt\":\"n\"}",
      "{\"overview\":\"o\",\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\"}]}",
      "{\"overview\":\" \",\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\"}],"
          + "\"nextStartPrompt\":\"n\"}",
      "{\"overview\":\"o\",\"keyConcepts\":[{\"name\":\"\\u00a0\",\"briefExplanation\":\"b\"}],"
          + "\"nextStartPrompt\":\"n\"}",
      "{\"overview\":\"o\",\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\","
          + "\"extra\":\"x\"}],\"nextStartPrompt\":\"n\"}",
      "{\"overview\":\"o\",\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\"}],"
          + "\"nextStartPrompt\":\"n\",\"reviewQuestions\":[\"q\"]}",
      "{\"overview\":1,\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\"}],"
          + "\"nextStartPrompt\":\"n\"}",
      "{\"overview\":\"o\",\"keyConcepts\":[{\"name\":\"a\",\"briefExplanation\":\"b\","
          + "\"sessionEvidence\":3}],\"nextStartPrompt\":\"n\"}",
      "[]"
  })
  void rejectsShapeOutsideContract(String body) {
    assertTrue(LearningSummaryOutputValidator.validate(JSON.readTree(body)).isEmpty());
  }

  @Test
  void allowsFiveConceptsAndRejectsSix() {
    assertTrue(LearningSummaryOutputValidator.validate(JSON.readTree(concepts(5))).isPresent());
    assertTrue(LearningSummaryOutputValidator.validate(JSON.readTree(concepts(6))).isEmpty());
  }

  @Test
  void countsTotalLengthByCharactersUpToTwelveHundred() {
    // overview + nextStartPrompt + name + briefExplanation = 1,200자 경계다(한글 1자 = 1).
    String fits = body("가".repeat(1197), "다", "개", "설");
    String exceeds = body("가".repeat(1198), "다", "개", "설");
    assertTrue(LearningSummaryOutputValidator.validate(JSON.readTree(fits)).isPresent());
    assertTrue(LearningSummaryOutputValidator.validate(JSON.readTree(exceeds)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://docs.spring.io/guide 참고", "www.example.org 문서", "github.com/repo 확인",
      "작성자 qa.user@example.com", "연락처 010-1234-5678", "+82 10 1234 5678",
      "토큰 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9", "sk-proj-abcdefghijklmnop1234",
      "AKIAABCDEFGHIJKLMNOP", "ghp_abcdefghijklmnopqrstuvwxyz", "api_key: value",
      "password=hunter2", "인증 코드 482913", "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8"
  })
  void detectsSensitiveValues(String value) {
    assertTrue(LearningSummaryOutputValidator.sensitive(value));
    assertTrue(LearningSummaryOutputValidator.validate(
        JSON.readTree(body("요약", "다음", "개념", value))).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "HTTP 404 응답 처리", "2026년 학습 계획", "Node.js 이벤트 루프", "O(n log n) 정렬",
      "트랜잭션 격리 수준", "버전 3.14 변경점"
  })
  void keepsOrdinaryLearningText(String value) {
    assertFalse(LearningSummaryOutputValidator.sensitive(value));
  }

  private static String concepts(int count) {
    StringBuilder items = new StringBuilder();
    for (int index = 0; index < count; index++) {
      items.append(index == 0 ? "" : ",")
          .append("{\"name\":\"개념").append(index).append("\",\"briefExplanation\":\"설명\"}");
    }
    return "{\"overview\":\"요약\",\"keyConcepts\":[" + items + "],\"nextStartPrompt\":\"다음\"}";
  }

  private static String body(String overview, String next, String name, String explanation) {
    return JSON.writeValueAsString(java.util.Map.of("overview", overview, "nextStartPrompt", next,
        "keyConcepts", java.util.List.of(java.util.Map.of("name", name,
            "briefExplanation", explanation))));
  }
}
