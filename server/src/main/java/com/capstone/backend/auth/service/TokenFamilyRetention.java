package com.capstone.backend.auth.service;

import com.capstone.backend.auth.persistence.RefreshTokenRecordRepository;
import com.capstone.backend.auth.persistence.TokenFamilyEntity;
import com.capstone.backend.auth.persistence.TokenFamilyRepository;
import com.capstone.backend.auth.persistence.TokenFamilyRepository.RetentionCandidate;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.common.api.SafeApiLog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 재사용 검증에 필요한 기간을 보존하고 만료된 인증 이력을 정리한다. */
@Component
@RequiredArgsConstructor
public class TokenFamilyRetention {
  private static final Duration RETENTION = Duration.ofDays(30);
  private static final PageRequest PAGE = PageRequest.of(0, 100);

  private final Clock clock;
  private final TokenFamilyRepository families;
  private final RefreshTokenRecordRepository tokens;
  private final UserRepository users;
  private final TransactionTemplate transactions;
  private long afterId;

  /** 보관 기한이 지난 패밀리를 주기적으로 정리한다. */
  @Scheduled(fixedDelayString = "${focuson.auth.retention-delay-ms:300000}",
      initialDelayString = "${focuson.auth.retention-initial-delay-ms:60000}")
  public void cleanup() {
    purgeDue(clock.instant());
  }

  /** 공유 스케줄러를 오래 점유하지 않도록 보관 기한이 끝난 후보를 한 페이지씩 정리한다. */
  public synchronized void purgeDue(Instant now) {
    Instant cutoff = now.minus(RETENTION);
    List<RetentionCandidate> batch;
    try {
      batch = families.findRetentionCandidates(afterId, cutoff, PAGE);
    } catch (RuntimeException exception) {
      SafeApiLog.tokenRetentionFailure(exception);
      return;
    }
    for (var candidate : batch) {
      try {
        transactions.executeWithoutResult(status -> purge(candidate, cutoff));
      } catch (RuntimeException exception) {
        SafeApiLog.tokenRetentionFailure(exception);
      }
    }
    afterId = batch.size() == PAGE.getPageSize() ? batch.getLast().getFamilyId() : 0;
  }

  private void purge(RetentionCandidate candidate, Instant cutoff) {
    if (users.findByIdForUpdateSkipLocked(candidate.getUserId()).isEmpty()) {
      return;
    }
    var family = families.findByIdForUpdate(candidate.getFamilyId()).orElse(null);
    if (family == null || retentionStartedAt(family).isAfter(cutoff)) {
      return;
    }
    tokens.clearParentLinksByFamily(candidate.getFamilyId());
    tokens.deleteRecordsByFamily(candidate.getFamilyId());
    families.delete(family);
  }

  private static Instant retentionStartedAt(TokenFamilyEntity family) {
    return family.getStatus() == TokenFamilyEntity.Status.REVOKED && family.getRevokedAt() != null
        && family.getRevokedAt().isBefore(family.getExpiresAt())
        ? family.getRevokedAt() : family.getExpiresAt();
  }
}
