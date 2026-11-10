package com.capstone.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 과거 저장된 불량 READY를 정상 학습 노트로 반환하지 않는다. */
class LearningSummaryValidationTests {
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t", "\n", "\r\n", "\u00a0", "\ufeff", "\u2007"})
  void rejectsBlankRequiredFieldsWithoutChangingStoredStatus(String blank) {
    for (String field : List.of("overview", "nextStartPrompt", "name", "briefExplanation")) {
      var entity = BeanUtils.instantiateClass(LearningSummaryEntity.class);
      ReflectionTestUtils.setField(entity, "status", Status.READY);
      ReflectionTestUtils.setField(entity, "overview", "학습한 개념");
      ReflectionTestUtils.setField(entity, "nextStartPrompt", "다시 살펴보세요.");
      ReflectionTestUtils.setField(entity, "keyConcepts", List.of(new KeyConcept(
          field.equals("name") ? blank : "트랜잭션",
          field.equals("briefExplanation") ? blank : "원자적 변경", null)));
      if (field.equals("overview") || field.equals("nextStartPrompt")) {
        ReflectionTestUtils.setField(entity, field, blank);
      }
      assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE,
          assertThrows(ApiException.class, entity::response).code());
      assertEquals(Status.READY, ReflectionTestUtils.getField(entity, "status"));
    }
  }
}
