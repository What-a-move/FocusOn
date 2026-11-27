package com.capstone.backend.report.service;

import com.capstone.backend.report.persistence.LearningReviewEvidenceEntity;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 화면 분석 완료 재검증을 통과한 개념 라벨을 학습 정리 근거로 남긴다. 호출자가 사용자 잠금을 쥔
 * transaction 안에서 부르므로 같은 세션의 동시 저장은 순서대로 처리된다.
 */
@Component
@RequiredArgsConstructor
public class LearningEvidenceRecorder {
  private static final int MAX_LABEL = 120;
  private final LearningReviewEvidenceRepository evidences;

  /** 세션에서 처음 본 개념이면 저장하고, 대소문자·공백만 다른 같은 개념은 건너뛴다. */
  public boolean record(long sessionId, String conceptLabel, String evidenceType,
      Instant observedAt, String contentSource, Instant now) {
    if (conceptLabel == null || evidenceType == null || observedAt == null
        || contentSource == null) {
      return false;
    }
    final String label = conceptLabel.strip().replaceAll("\\s+", " ");
    if (label.isEmpty() || label.length() > MAX_LABEL) {
      return false;
    }
    String hash = dedupeHash(evidenceType, label);
    if (evidences.existsBySessionIdAndDedupeHash(sessionId, hash)) {
      return false;
    }
    evidences.save(LearningReviewEvidenceEntity.of(sessionId, label, evidenceType, observedAt,
        contentSource, hash, now));
    return true;
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
