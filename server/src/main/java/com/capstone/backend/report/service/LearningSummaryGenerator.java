package com.capstone.backend.report.service;

import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.common.api.SafeApiLog.AnalysisEvent;
import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.report.service.LearningSummaryAiClient.Evidence;
import com.capstone.backend.report.service.LearningSummaryAiClient.Input;
import com.capstone.backend.report.service.LearningSummaryAiClient.LearningSummaryAiException;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 학습 정리 작업 하나를 점유해 끝까지 처리한다. AI 호출은 트랜잭션 밖에서 하고, 결과는 점유한
 * 시도 번호가 그대로일 때만 저장한다. 점유가 만료돼 다른 Worker가 가져갔다면 늦은 결과는 버린다.
 */
@Service
public class LearningSummaryGenerator {
  /** 시작 시도 1회 + 재시도 2회(이슈 #288). */
  static final short MAX_ATTEMPTS = 3;
  /** 20초 AI 제한 시간보다 충분히 길게 잡아 정상 처리 중인 작업을 빼앗지 않는다. */
  static final Duration LEASE = Duration.ofSeconds(60);
  /** 실패한 시도 번호(1, 2) 뒤의 대기 시간이다. */
  private static final List<Duration> RETRY_DELAYS =
      List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
  /** 서로 다른 근거가 이보다 적으면 모델을 부르지 않는다. */
  static final int MIN_EVIDENCES = 2;

  private final LearningSummaryRepository summaries;
  private final LearningReviewEvidenceRepository evidences;
  private final LearningSessionRepository sessions;
  private final LearningSummaryAiClient ai;
  private final TransactionTemplate transactions;

  /** 트랜잭션 경계를 단계별로 나누기 위해 TransactionTemplate을 직접 만든다. */
  public LearningSummaryGenerator(LearningSummaryRepository summaries,
      LearningReviewEvidenceRepository evidences, LearningSessionRepository sessions,
      LearningSummaryAiClient ai, PlatformTransactionManager transactionManager) {
    this.summaries = summaries;
    this.evidences = evidences;
    this.sessions = sessions;
    this.ai = ai;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /** 점유에 실패하면(다른 Worker가 먼저 가져감) 아무것도 하지 않고 false를 반환한다. */
  public boolean process(long sessionId) {
    Instant now = Instant.now();
    Short attempt = transactions.execute(status ->
        summaries.claim(sessionId, now, now.plus(LEASE), MAX_ATTEMPTS) == 1
            ? summaries.findById(sessionId).map(LearningSummaryEntity::getAttemptCount)
                .orElse(null)
            : null);
    if (attempt == null) {
      return false;
    }
    Input input = transactions.execute(status -> fixedInput(sessionId));
    if (input == null || input.evidences().size() < MIN_EVIDENCES) {
      finish(sessionId, attempt, LearningSummaryEntity::insufficientData);
      return true;
    }
    generate(sessionId, attempt, input);
    return true;
  }

  /** 입력 마감 전에 받은 근거만 고정한다. 마감 뒤 도착한 근거는 이 정리에 쓰지 않는다. */
  private Input fixedInput(long sessionId) {
    var summary = summaries.findById(sessionId).orElseThrow();
    var session = sessions.findById(sessionId).orElseThrow();
    var fixed = evidences
        .findTop20BySessionIdAndCreatedAtLessThanEqualOrderByObservedAtAscIdAsc(
            sessionId, summary.getInputClosesAt())
        .stream().map(evidence -> new Evidence(evidence.getId(), evidence.getConcept(),
            evidence.getObservedAt().toString())).toList();
    return new Input(sessionId, session.getGoalTextSnapshot(), fixed);
  }

  private void generate(long sessionId, short attempt, Input input) {
    String traceId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    MDC.put("traceId", traceId);
    try {
      SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_START, started);
      JsonNode output;
      try {
        output = ai.generate(input, traceId);
      } catch (LearningSummaryAiException exception) {
        SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_FAIL, started);
        Instant retryAt = exception.retryable() && attempt < MAX_ATTEMPTS
            ? Instant.now().plus(RETRY_DELAYS.get(attempt - 1)) : null;
        finish(sessionId, attempt, retryAt == null ? LearningSummaryEntity::failed
            : summary -> summary.retryAt(retryAt));
        return;
      }
      SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_OK, started);
      var result = LearningSummaryOutputValidator.validate(output);
      if (result.isEmpty()) {
        // 형식·길이·민감값 위반은 같은 근거를 다시 보내지 않고 결과 전체를 버린다.
        SafeApiLog.learningSummaryEvent(AnalysisEvent.STORE_FAIL, started);
        finish(sessionId, attempt, LearningSummaryEntity::failed);
        return;
      }
      var valid = result.get();
      boolean stored = finish(sessionId, attempt, summary -> summary.ready(valid.overview(),
          valid.keyConcepts(), valid.nextStartPrompt()));
      // 점유를 빼앗긴 늦은 결과는 저장되지 않으므로 실패로 기록한다.
      SafeApiLog.learningSummaryEvent(stored ? AnalysisEvent.STORE_OK : AnalysisEvent.STORE_FAIL,
          started);
    } finally {
      MDC.remove("traceId");
    }
  }

  /** 행을 잠근 뒤 아직 이 시도가 점유 중일 때만 결과를 반영하고, 반영 여부를 반환한다. */
  private boolean finish(long sessionId, short attempt, Consumer<LearningSummaryEntity> change) {
    return Boolean.TRUE.equals(transactions.execute(status -> summaries.findForUpdate(sessionId)
        .filter(summary -> summary.getStatus() == Status.GENERATING
            && summary.getAttemptCount() == attempt)
        .map(summary -> {
          change.accept(summary);
          return true;
        }).orElse(false)));
  }
}
