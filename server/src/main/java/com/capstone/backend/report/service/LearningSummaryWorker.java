package com.capstone.backend.report.service;

import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.common.api.SafeApiLog.AnalysisEvent;
import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.report.persistence.LearningReviewEvidenceEntity;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 종료된 세션의 학습 정리를 비동기로 만든다. 입력 마감(종료 30초 뒤)이 지난 작업을 한 worker만
 * 점유하고, 근거가 2개 미만이면 모델을 부르지 않는다. AI 실패는 1분·5분 뒤 최대 2회 재시도한다.
 * 세션 종료·시간 기록은 이 작업의 성공 여부와 무관하다.
 */
@Component
@EnableScheduling
public class LearningSummaryWorker {
  static final Duration INPUT_WINDOW = Duration.ofSeconds(30);
  static final Duration LEASE = Duration.ofSeconds(60);
  static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
  static final int MAX_EVIDENCE = 20;
  static final int MIN_EVIDENCE = 2;
  private static final int BATCH_SIZE = 20;
  private final LearningSummaryRepository summaries;
  private final LearningReviewEvidenceRepository evidences;
  private final LearningSessionRepository sessions;
  private final LearningSummaryAiClient ai;
  private final TransactionTemplate transactions;
  private final SingleFlightRunner runner = new SingleFlightRunner("learning-summary-worker");

  /** 저장소와 AI 내부 호출, 짧은 transaction을 연결한다. */
  public LearningSummaryWorker(LearningSummaryRepository summaries,
      LearningReviewEvidenceRepository evidences, LearningSessionRepository sessions,
      LearningSummaryAiClient ai, PlatformTransactionManager transactionManager) {
    this.summaries = summaries;
    this.evidences = evidences;
    this.sessions = sessions;
    this.ai = ai;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /**
   * 세션 종료 transaction 안에서 호출해 정리를 QUEUED로 등록한다. 입력 마감은 종료 30초 뒤다.
   * 세션 행을 잠근 종료 명령에서만 부르므로 같은 세션의 중복 등록은 기존 행 확인으로 막는다.
   */
  public void enqueue(long sessionId, Instant endedAt) {
    if (!summaries.existsById(sessionId)) {
      summaries.save(LearningSummaryEntity.queued(sessionId, endedAt.plus(INPUT_WINDOW),
          endedAt));
    }
  }

  /**
   * 주기 실행 진입점이다. AI 호출(건당 최대 약 23초)이 세션 정리와 함께 쓰는 기본 스케줄러
   * 스레드를 붙잡지 않도록 전용 스레드에 넘기고 바로 돌아온다. 앞선 실행이 남아 있으면 건너뛴다.
   */
  @Scheduled(fixedDelayString = "${focuson.learning-summary.worker-delay-ms:5000}",
      initialDelayString = "${focuson.learning-summary.worker-initial-delay-ms:15000}")
  public void run() {
    runner.submit(() -> {
      try {
        processDue(Instant.now());
      } catch (RuntimeException exception) {
        SafeApiLog.learningSummaryEvent(AnalysisEvent.STORE_FAIL, System.nanoTime(),
            exception.getClass().getSimpleName());
      }
    });
  }

  /** 종료 시 새 실행을 막고 진행 중인 작업을 잠시 기다린다. */
  @PreDestroy
  void shutdown() {
    runner.close();
  }

  /** now 기준으로 처리할 수 있는 작업을 한 번 훑는다. 테스트는 시각을 직접 넘긴다. */
  public void processDue(Instant now) {
    transactions.executeWithoutResult(status ->
        summaries.failExhausted(Status.GENERATING, Status.FAILED, now));
    var due = summaries.findDue(Status.QUEUED, Status.GENERATING, now,
        PageRequest.of(0, BATCH_SIZE));
    for (Long id : due) {
      try {
        process(id, now);
      } catch (RuntimeException exception) {
        SafeApiLog.learningSummaryEvent(AnalysisEvent.STORE_FAIL, System.nanoTime(),
            exception.getClass().getSimpleName());
      }
    }
  }

  private void process(long id, Instant now) {
    Integer claimed = transactions.execute(status -> summaries.claim(id, Status.QUEUED,
        Status.GENERATING, now, now.plus(LEASE)));
    if (claimed == null || claimed == 0) {
      return;
    }
    var job = transactions.execute(status -> {
      var summary = summaries.findById(id).orElseThrow();
      var session = sessions.findById(id).orElseThrow();
      Instant cutoff = summary.getInputCutoffAt() == null ? now : summary.getInputCutoffAt();
      List<LearningReviewEvidenceEntity> input = evidences.findInput(id, cutoff,
          PageRequest.of(0, MAX_EVIDENCE));
      return new Job(summary.getAttemptCount(), session.getGoalTextSnapshot(), input);
    });
    long distinct = job.evidences().stream().map(LearningReviewEvidenceEntity::getDedupeHash)
        .distinct().count();
    if (distinct < MIN_EVIDENCE) {
      // 근거가 부족하면 모델을 부르지 않고 지어내지 않는다.
      transactions.execute(status -> summaries.completeWithoutContent(id, job.attempt(),
          Status.GENERATING, Status.INSUFFICIENT_DATA, null, null, (int) distinct, now));
      return;
    }
    String traceId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    MDC.put("traceId", traceId);
    try {
      SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_START, started, null);
      var request = job.evidences().stream().map(evidence -> new LearningSummaryAiClient.Evidence(
          evidence.getId().toString(), evidence.getConceptLabel(), evidence.getEvidenceType(),
          evidence.getObservedAt().toString())).toList();
      LearningSummaryAiClient.Draft draft;
      try {
        draft = ai.generate(id + "-" + job.attempt(), id, job.goalSnapshot(), request, traceId);
      } catch (LearningSummaryAiClient.AiFailure failure) {
        SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_FAIL, started, failure.code());
        retryOrFail(id, job.attempt(), failure.code(), (int) distinct, now);
        return;
      }
      String rejected = LearningSummaryOutputGuard.reject(draft);
      if (rejected != null) {
        SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_FAIL, started, rejected);
        retryOrFail(id, job.attempt(), rejected, (int) distinct, now);
        return;
      }
      SafeApiLog.learningSummaryEvent(AnalysisEvent.AI_OK, started, null);
      Boolean stored = transactions.execute(status -> summaries.findForUpdate(id)
          .filter(summary -> summary.getStatus() == Status.GENERATING
              && summary.getAttemptCount() == job.attempt())
          .map(summary -> {
            summary.markReady(draft.overview(), draft.keyConcepts(), draft.nextStartPrompt(),
                draft.modelVersion(), (int) distinct, Instant.now());
            return true;
          }).orElse(false));
      // 다른 시도가 이미 끝냈으면 늦은 결과로 덮어쓰지 않는다.
      SafeApiLog.learningSummaryEvent(Boolean.TRUE.equals(stored) ? AnalysisEvent.STORE_OK
          : AnalysisEvent.STORE_FAIL, started, Boolean.TRUE.equals(stored) ? null : "STALE");
    } finally {
      MDC.remove("traceId");
    }
  }

  private void retryOrFail(long id, short attempt, String code, int evidenceCount, Instant now) {
    boolean last = attempt > RETRY_DELAYS.size();
    Instant retryAt = last ? null : now.plus(RETRY_DELAYS.get(attempt - 1));
    transactions.execute(status -> summaries.completeWithoutContent(id, attempt,
        Status.GENERATING, last ? Status.FAILED : Status.QUEUED, retryAt, code, evidenceCount,
        now));
  }

  private record Job(short attempt, String goalSnapshot,
      List<LearningReviewEvidenceEntity> evidences) {}
}
