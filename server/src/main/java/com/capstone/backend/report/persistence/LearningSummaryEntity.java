package com.capstone.backend.report.persistence;

import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 세션당 정리 한 건의 저장 모델이다. 자동 생성은 #29에서 연결한다. */
@Entity
@Table(name = "learning_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSummaryEntity {
  @Id
  @Column(name = "session_id")
  private Long sessionId;
  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false, length = 32)
  private Status status;
  @Column(length = 1200)
  private String overview;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "key_concepts", nullable = false, columnDefinition = "jsonb")
  private List<KeyConcept> keyConcepts;
  @Column(name = "next_start_prompt", length = 1200)
  private String nextStartPrompt;

  /** READY 이외 상태는 생성 중간 내용이나 실패 출력을 노출하지 않는다. */
  public LearningSummaryResponse response() {
    if (status == Status.READY && (!nonBlank(overview) || !nonBlank(nextStartPrompt)
        || keyConcepts == null || keyConcepts.stream().anyMatch(concept -> concept == null
            || !nonBlank(concept.name()) || !nonBlank(concept.briefExplanation())))) {
      // V20 이전의 불량 READY는 보존하되 정상 결과로 노출하지 않는다.
      throw new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
    return status == Status.READY
        ? new LearningSummaryResponse(status, overview, List.copyOf(keyConcepts), nextStartPrompt)
        : new LearningSummaryResponse(status, null, List.of(), null);
  }

  /** SQL과 같은 공백 기준으로 탭·줄바꿈·Unicode 공백과 BOM만 있는 필드를 거절한다. */
  private boolean nonBlank(String value) {
    return value != null && value.codePoints().anyMatch(character ->
        !Character.isWhitespace(character) && !Character.isSpaceChar(character)
            && character != 0xfeff);
  }
}
