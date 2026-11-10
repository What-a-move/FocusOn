package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.LearningSummaryResponse;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 소유권을 확인한 뒤 실제 저장된 정리 상태만 제공한다. */
@Service
@RequiredArgsConstructor
public class LearningSummaryService {
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
}
