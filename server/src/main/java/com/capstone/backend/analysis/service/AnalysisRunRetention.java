package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.persistence.AnalysisRunRepository;
import com.capstone.backend.analysis.persistence.AnalysisRunRepository.RetentionCandidate;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.common.api.SafeApiLog;
import com.capstone.backend.intervention.persistence.InterventionEventRepository;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 종료된 세션에서 보관 기한이 지난 화면 분석 이력과 학습 정리 근거를 정리한다. */
@Component
@RequiredArgsConstructor
public class AnalysisRunRetention {
  private static final Duration RETENTION = Duration.ofDays(90);
  private static final PageRequest PAGE = PageRequest.of(0, 500);
  private static final List<SessionStatus> TERMINAL =
      List.of(SessionStatus.ENDED, SessionStatus.ABANDONED);

  private final Clock clock;
  private final AnalysisRunRepository runs;
  private final UserRepository users;
  private final LearningSessionRepository sessions;
  private final InterventionEventRepository events;
  private final TransactionTemplate transactions;
  private final LearningReviewEvidenceRepository evidences;
  private long afterId;

  /** 보관 기한이 지난 분석 실행 기록을 주기적으로 정리한다. */
  @Scheduled(fixedDelayString = "${focuson.analysis.retention-delay-ms:300000}",
      initialDelayString = "${focuson.analysis.retention-initial-delay-ms:60000}")
  public void cleanup() {
    purgeDue(clock.instant());
  }

  /** 후보 한 페이지를 세션별로 나눠 잠금과 삭제를 수행한다. */
  public synchronized void purgeDue(Instant now) {
    Instant cutoff = cutoff(now);
    try {
      // 학습 정리 근거도 분석 실행과 같은 종료 후 90일 기준으로 지운다.
      transactions.executeWithoutResult(status -> evidences.deleteExpired(TERMINAL, cutoff));
    } catch (RuntimeException exception) {
      SafeApiLog.analysisRetentionFailure(exception);
    }
    List<RetentionCandidate> batch;
    try {
      batch = runs.findRetentionCandidates(afterId, TERMINAL, cutoff, PAGE);
    } catch (RuntimeException exception) {
      SafeApiLog.analysisRetentionFailure(exception);
      return;
    }
    var bySession = batch.stream().collect(Collectors.groupingBy(
        RetentionCandidate::getSessionId, LinkedHashMap::new, Collectors.toList()));
    for (var candidates : bySession.values()) {
      try {
        transactions.executeWithoutResult(status -> purgeSession(candidates, cutoff));
      } catch (RuntimeException exception) {
        SafeApiLog.analysisRetentionFailure(exception);
      }
    }
    afterId = batch.size() == PAGE.getPageSize() ? batch.getLast().getRunId() : 0;
  }

  private void purgeSession(List<RetentionCandidate> candidates, Instant cutoff) {
    var first = candidates.getFirst();
    if (users.findByIdForUpdateSkipLocked(first.getUserId()).isEmpty()) {
      return;
    }
    var session = sessions.findOwnedForUpdate(first.getSessionId(), first.getUserId())
        .orElse(null);
    if (session == null || !expired(session, cutoff)) {
      return;
    }
    var runIds = candidates.stream().map(RetentionCandidate::getRunId).toList();
    events.clearExpiredAnalysisLinks(session.getId(), runIds);
    runs.deleteRetainedRuns(session.getId(), runIds);
  }

  static Instant cutoff(Instant now) {
    return now.minus(RETENTION);
  }

  /** 조회와 정리에서 먼저 종료 상태를 확인하고 동일한 시각 경계를 적용한다. */
  static boolean expired(LearningSessionEntity session, Instant cutoff) {
    return TERMINAL.contains(session.getStatus()) && session.getEndedAt() != null
        && !session.getEndedAt().isAfter(cutoff);
  }
}
