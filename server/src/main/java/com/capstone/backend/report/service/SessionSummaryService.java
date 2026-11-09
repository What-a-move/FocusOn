package com.capstone.backend.report.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.dto.SessionSummaryResponse;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionExtensionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최종 상태의 저장된 결과만 읽어 분석 실패와 무관하게 학습 시간을 제공한다. */
@Service
@RequiredArgsConstructor
public class SessionSummaryService {
  private final LearningSessionRepository sessions;
  private final SessionExtensionRepository extensions;

  /** 종료된 세션은 추가 명령이 불가능하므로 누적 시간을 현재 시각으로 추정하지 않는다. */
  @Transactional(readOnly = true)
  public SessionSummaryResponse get(FocusOnPrincipal principal, String sessionId) {
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
    var session = sessions.findByIdAndUserId(id, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    if (session.getStatus() != SessionStatus.ENDED
        && session.getStatus() != SessionStatus.ABANDONED) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    var totals = extensions.summarize(id);
    return new SessionSummaryResponse(session.getId().toString(), session.getGoalTextSnapshot(),
        session.getTargetDurationMs(), session.getAccumulatedActiveMs(), session.getStatus(),
        session.getStartedAt(), session.getEndedAt(), totals.getExtensionCount(),
        totals.getExtendedDurationMs());
  }
}
