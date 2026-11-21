package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 민감값 검사가 정상 학습 문장을 버리지 않고, 실제 민감값은 계속 거르는지 확인한다. */
class LearningSummaryOutputGuardTests {
  @ParameterizedTest
  @ValueSource(strings = {
      "AbstractAnnotationConfigDispatcherServletInitializer 설정 흐름을 봤어요.",
      "token: 인증에 쓰는 값이라 만료 시간을 함께 다뤄요.",
      "password 필드는 6자 이상으로 검증했어요.",
      "100000건을 한 번에 넣을 때 배치 크기를 비교했어요.",
      "HTTP 상태 코드 401과 403의 차이를 정리했어요."})
  void ordinaryStudyTextPasses(String text) {
    assertNull(LearningSummaryOutputGuard.reject(draft(text)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "자세한 내용은 https://example.com 참고",
      "문의는 someone@example.com",
      "연락처 010-1234-5678",
      "password=hunter2abc 로 로그인",
      "api_key: \"abcd1234efgh\"",
      "키 sk-abcdefghijklmnop1234",
      "값 9f8e7d6c5b4a39281706f5e4d3c2b1a0aa",
      "OTP 123456 입력",
      "인증번호: 654321",
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig"})
  void sensitiveValuesAreRejected(String text) {
    assertEquals("OUTPUT_SENSITIVE", LearningSummaryOutputGuard.reject(draft(text)));
  }

  private static LearningSummaryAiClient.Draft draft(String overview) {
    return new LearningSummaryAiClient.Draft(overview,
        List.of(new KeyConcept("개념", "짧은 설명", null)), "다음에 이어서 보세요.", "v1");
  }
}
