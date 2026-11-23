package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.LearningSummaryListResponse;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.dto.LearningSummaryResponse.Status;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 소유권을 확인한 뒤 실제 저장된 정리 상태만 제공한다. */
@Service
@RequiredArgsConstructor
public class LearningSummaryService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;
  /** 첫 페이지 커서 기준. DB 시각 범위 안의 충분히 먼 미래다. */
  private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");
  private final LearningSessionRepository sessions;
  private final LearningSummaryRepository summaries;

  /** 없는 정리는 상태를 추측하지 않고404로 구분한다. */
  @Transactional(readOnly = true)
  public LearningSummaryResponse get(FocusOnPrincipal principal, String sessionId) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
    long id;
    try {
      id = Long.parseLong(sessionId);
      if (id <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    sessions.findByIdAndUserId(id, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    return summaries.findById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND))
        .response();
  }

  /**
   * READY 노트를 세션 종료 시각 내림차순으로 반환한다. 커서는 마지막 항목의
   * "종료 시각(epoch 마이크로초)_세션 ID"이며 클라이언트는 그대로 돌려보낸다.
   */
  @Transactional(readOnly = true)
  public LearningSummaryListResponse list(FocusOnPrincipal principal, String cursor,
      Integer requestedLimit) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    Instant endedBefore = MAX_INSTANT;
    long idBefore = Long.MAX_VALUE;
    if (cursor != null) {
      try {
        String[] parts = cursor.split("_", -1);
        if (parts.length != 2) {
          throw new IllegalArgumentException();
        }
        long micros = Long.parseLong(parts[0]);
        idBefore = Long.parseLong(parts[1]);
        if (micros < 0 || idBefore <= 0) {
          throw new IllegalArgumentException();
        }
        endedBefore = Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
      } catch (IllegalArgumentException | ArithmeticException | DateTimeException exception) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    }
    var rows = summaries.findReadyPage(principal.userId(), Status.READY, endedBefore, idBefore,
        PageRequest.of(0, limit + 1));
    boolean hasMore = rows.size() > limit;
    var page = rows.subList(0, Math.min(limit, rows.size()));
    // 불량 READY 한 건 때문에 목록 전체가 열리지 않으면 안 되므로 그 항목만 뺀다.
    // 커서는 건너뛴 행까지 포함한 마지막 조회 행 기준이라 다음 페이지가 밀리지 않는다.
    var items = page.stream().map(row -> item((LearningSessionEntity) row[0],
        (LearningSummaryEntity) row[1])).filter(java.util.Objects::nonNull).toList();
    String next = null;
    if (hasMore) {
      var last = (LearningSessionEntity) page.getLast()[0];
      next = ChronoUnit.MICROS.between(Instant.EPOCH, last.getEndedAt()) + "_" + last.getId();
    }
    return new LearningSummaryListResponse(items, next);
  }

  private LearningSummaryListResponse.Item item(LearningSessionEntity session,
      LearningSummaryEntity summary) {
    // 단건 조회와 같은 READY 검증을 거쳐 불량 저장값을 노출하지 않는다. 단건은 503이지만
    // 목록은 해당 항목만 빼고 내용 없이 세션 ID만 기록한다.
    LearningSummaryResponse ready;
    try {
      ready = summary.response();
    } catch (ApiException invalid) {
      com.capstone.backend.common.api.SafeApiLog.learningSummaryHidden(session.getId());
      return null;
    }
    return new LearningSummaryListResponse.Item(session.getId().toString(),
        session.getGoalTextSnapshot(), session.getStudyDate(), session.getStartedAt(),
        session.getEndedAt(), session.getAccumulatedActiveMs(), ready.overview(),
        ready.keyConcepts());
  }
}
