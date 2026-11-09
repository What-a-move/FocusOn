package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 조회에서는 타이머나 상태를 갱신하지 않고 서버가 확정한 값만 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private final LearningSessionRepository sessions;

  /** 세션 실행 기기가 달라도 같은 계정의 인증된 기기는 상세를 조회할 수 있다. */
  public Detail detail(FocusOnPrincipal principal, String id) {
    long numericId;
    try {
      numericId = Long.parseLong(id);
      if (numericId <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
    return toDetail(sessions.findByIdAndUserId(numericId, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND)));
  }

  /** 날짜는 생성 때 고정한 studyDate를 사용해 시간대 변경으로 기록이 이동하지 않는다. */
  public History history(FocusOnPrincipal principal, String from, String to,
      String timeZone, String cursor, Integer requestedLimit) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
    LocalDate first;
    LocalDate last;
    long before;
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    try {
      first = LocalDate.parse(from);
      last = LocalDate.parse(to);
      if (timeZone != null) {
        ZoneId.of(timeZone);
      }
      before = cursor == null ? Long.MAX_VALUE : Long.parseLong(cursor);
      if (first.isAfter(last) || before <= 0 || limit < 1 || limit > MAX_LIMIT) {
        throw new IllegalArgumentException();
      }
    } catch (DateTimeException | IllegalArgumentException | NullPointerException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    var rows = sessions.findHistory(principal.userId(), first, last, before,
        PageRequest.of(0, limit + 1));
    boolean hasMore = rows.size() > limit;
    var page = rows.subList(0, Math.min(limit, rows.size()));
    String next = hasMore ? page.getLast().getId().toString() : null;
    return new History(page.stream().map(this::toDetail).toList(), next);
  }

  private Detail toDetail(LearningSessionEntity session) {
    return new Detail(session.getId().toString(), session.getStatus(), session.getVersion(),
        session.getGoalTextSnapshot(), session.getTargetDurationMs(),
        session.getAccumulatedActiveMs(), session.getInterventionPolicyVersion());
  }

  /** 상세 및 목록의 공통 세션 스냅샷이다. */
  public record Detail(String sessionId, SessionStatus status, long version,
      String goalTextSnapshot, long targetDurationMs, long accumulatedActiveMs,
      String interventionPolicyVersion) {}

  /** 다음 커서가 null이면 마지막 페이지다. */
  public record History(List<Detail> items, String nextCursor) {}
}
