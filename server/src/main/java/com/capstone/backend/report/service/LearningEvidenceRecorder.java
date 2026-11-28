package com.capstone.backend.report.service;

import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.report.persistence.LearningReviewEvidenceEntity;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면 분석 완료 재검증을 통과한 개념 라벨을 학습 정리 근거로 남긴다. 호출자가 사용자 잠금을 쥔
 * transaction 안에서 부르므로 같은 세션의 동시 저장은 순서대로 처리된다.
 */
@Component
@RequiredArgsConstructor
public class LearningEvidenceRecorder {
  private static final int MAX_LABEL = 120;
  private final LearningReviewEvidenceRepository evidences;
  private final LearningSummaryRepository summaries;
  private final Clock clock;

  /** 세션에서 처음 본 개념이면 저장하고, 대소문자·공백만 다른 같은 개념은 건너뛴다. */
  public boolean record(long sessionId, String conceptLabel, String evidenceType,
      Instant observedAt, String contentSource, Instant now) {
    var evidence = newEvidence(sessionId, conceptLabel, evidenceType, observedAt, contentSource,
        now);
    if (evidence == null) {
      return false;
    }
    evidences.save(evidence);
    return true;
  }

  /** 종료 후에는 Worker가 같은 행을 점유하기 전까지만 최초 입력에 근거를 추가한다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordLate(long sessionId, String conceptLabel, String evidenceType,
      Instant observedAt, String contentSource, Instant runningSince) {
    var summary = summaries.findForUpdate(sessionId).orElse(null);
    if (summary == null || summary.getStatus() != Status.QUEUED || summary.getAttemptCount() != 0
        || runningSince == null || !runningSince.equals(summary.getInputRunningSince())
        || summary.getInputCutoffAt() == null) {
      return;
    }
    Instant cutoff = summary.getInputCutoffAt();
    Instant now = clock.instant();
    if (now.isAfter(cutoff)) {
      return;
    }
    var evidence = newEvidence(sessionId, conceptLabel, evidenceType, observedAt, contentSource,
        now);
    if (evidence == null) {
      return;
    }
    evidences.saveAndFlush(evidence);
    if (clock.instant().isAfter(cutoff)) {
      // 마감 전에 시작했어도 저장이 지연된 근거는 Worker에 공개하지 않는다.
      evidences.delete(evidence);
    }
  }

  private LearningReviewEvidenceEntity newEvidence(long sessionId, String conceptLabel,
      String evidenceType, Instant observedAt, String contentSource, Instant now) {
    if (conceptLabel == null || evidenceType == null || observedAt == null
        || contentSource == null) {
      return null;
    }
    final String label = conceptLabel.strip().replaceAll("\\s+", " ");
    if (label.isEmpty() || label.length() > MAX_LABEL) {
      return null;
    }
    String hash = dedupeHash(evidenceType, label);
    if (evidences.existsBySessionIdAndDedupeHash(sessionId, hash)) {
      return null;
    }
    return LearningReviewEvidenceEntity.of(sessionId, label, evidenceType, observedAt,
        contentSource, hash, now);
  }

  static String dedupeHash(String evidenceType, String label) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(
          (evidenceType + "\n" + label.toLowerCase(Locale.ROOT))
              .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
