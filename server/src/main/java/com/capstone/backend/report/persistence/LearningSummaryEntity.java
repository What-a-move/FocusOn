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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 세션당 정리 한 건과 그 생성 작업의 점유·재시도 상태를 함께 저장한다. */
@Entity
@Table(name = "learning_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSummaryEntity {
  /** 종료 직후 이미 진행 중이던 분석 결과만 받기 위해 입력을 늦게 고정한다. */
  private static final Duration INPUT_GRACE = Duration.ofSeconds(30);

  @Id
  @Column(name = "session_id")
  private Long sessionId;
  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false, length = 32)
  @Getter
  private Status status;
  @Column(length = 1200)
  private String overview;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "key_concepts", nullable = false, columnDefinition = "jsonb")
  private List<KeyConcept> keyConcepts;
  @Column(name = "next_start_prompt", length = 1200)
  private String nextStartPrompt;
  /** 이 시각 이후에 저장된 근거는 정리 입력에 넣지 않는다. */
  @Column(name = "input_closes_at")
  @Getter
  private Instant inputClosesAt;
  /** QUEUED 행을 Worker가 가져갈 수 있는 가장 이른 시각이다. */
  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;
  /** GENERATING 점유가 끝나는 시각이다. 지나면 Worker가 죽은 것으로 보고 다시 가져간다. */
  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;
  /** 시작한 생성 시도 수다. 결과 저장 때 점유 번호로도 사용한다. */
  @Column(name = "attempt_count", nullable = false)
  @Getter
  private short attemptCount;

  private LearningSummaryEntity(Long sessionId, Instant endedAt) {
    this.sessionId = sessionId;
    this.status = Status.QUEUED;
    this.keyConcepts = List.of();
    this.inputClosesAt = endedAt.plus(INPUT_GRACE);
    this.nextAttemptAt = this.inputClosesAt;
  }

  /** 사용자 종료가 확정된 세션의 정리 작업을 입력 마감 시각에 실행되도록 만든다. */
  public static LearningSummaryEntity queued(Long sessionId, Instant endedAt) {
    return new LearningSummaryEntity(sessionId, endedAt);
  }

  /** 검증을 통과한 결과만 공개 상태로 저장한다. */
  public void ready(String overview, List<KeyConcept> keyConcepts, String nextStartPrompt) {
    this.status = Status.READY;
    this.overview = overview;
    this.keyConcepts = List.copyOf(keyConcepts);
    this.nextStartPrompt = nextStartPrompt;
    clearSchedule();
  }

  /** 근거가 부족하면 모델을 부르지 않고 빈 결과로 끝낸다. */
  public void insufficientData() {
    this.status = Status.INSUFFICIENT_DATA;
    clearSchedule();
  }

  /** 재시도를 모두 쓰거나 결과가 안전하지 않으면 생성 결과 없이 끝낸다. */
  public void failed() {
    this.status = Status.FAILED;
    clearSchedule();
  }

  /** 일시 실패한 작업을 다음 시도 시각까지 대기열로 되돌린다. */
  public void retryAt(Instant nextAttemptAt) {
    this.status = Status.QUEUED;
    this.nextAttemptAt = nextAttemptAt;
    this.leaseExpiresAt = null;
  }

  private void clearSchedule() {
    this.nextAttemptAt = null;
    this.leaseExpiresAt = null;
  }

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
  public static boolean nonBlank(String value) {
    return value != null && value.codePoints().anyMatch(character ->
        !Character.isWhitespace(character) && !Character.isSpaceChar(character)
            && character != 0xfeff);
  }
}
