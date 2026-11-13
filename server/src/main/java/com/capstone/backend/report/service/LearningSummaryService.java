package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 소유권을 확인한 뒤 실제 저장된 정리 상태만 제공한다. */
@Service
@RequiredArgsConstructor
public class LearningSummaryService {
  private final LearningSessionRepository sessions;
  private final LearningSummaryRepository summaries;

  /** 현재 Desktop 사용자의 완료된 학습 정리를 페이지 단위로 반환한다. */
  @Transactional(readOnly = true)
  public List<SummaryListItem> list(FocusOnPrincipal principal, int page) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
    if (page < 0 || page > 10000) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    return summaries.findList(principal.userId(), SessionStatus.ENDED,
        LearningSummaryResponse.Status.READY, PageRequest.of(page, 20)).stream()
        .map(item -> new SummaryListItem(item.getSessionId().toString(), item.getGoalSnapshot(),
            item.getEndedAt()))
        .toList();
  }

  /** 학습 정리 상세 조회로 연결할 목록 항목이다. */
  public record SummaryListItem(String sessionId, String goalSnapshot, Instant endedAt) {}

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
}
