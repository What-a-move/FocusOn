package com.capstone.backend.session.service;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.privacy.SensitiveText;
import com.capstone.backend.session.dto.RetrospectiveDtos.Rating;
import com.capstone.backend.session.dto.RetrospectiveDtos.ReasonCode;
import com.capstone.backend.session.dto.RetrospectiveDtos.RetrospectiveRequest;
import com.capstone.backend.session.dto.RetrospectiveDtos.RetrospectiveResponse;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionRetrospectiveEntity;
import com.capstone.backend.session.persistence.SessionRetrospectiveRepository;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 끝난 세션의 한 줄 회고를 저장한다(#318). 세션당 하나이며 다시 보내면 덮어쓴다. 메모는 사용자가 쓴
 * 글이라 길이·민감정보를 검사하고, 원문은 로그·오류 응답에 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SessionRetrospectiveService {
  static final int MAX_NOTE = 100;
  private final LearningSessionRepository sessions;
  private final SessionRetrospectiveRepository retrospectives;
  private final ObjectMapper json;

  /** 소유한 끝난 세션(ENDED·ABANDONED)에만 저장한다. */
  @Transactional
  public RetrospectiveResponse save(FocusOnPrincipal principal, String deviceId, String sessionId,
      RetrospectiveRequest request) {
    DeviceHeaderValidator.require(deviceId, principal);
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    List<ReasonCode> reasons = request.reasonCodes() == null ? List.of() : request.reasonCodes();
    String note = request.note() == null || request.note().isBlank() ? null
        : request.note().strip();
    validate(request.rating(), reasons, note);
    long id = parseId(sessionId);
    // 세션 행을 잠가 같은 세션의 동시 저장이 기본키 충돌로 실패하지 않게 한다.
    var session = sessions.findOwnedForUpdate(id, principal.userId())
        .orElseThrow(() -> new ApiException(ApiErrorCode.SESSION_NOT_FOUND));
    if (session.getStatus() != SessionStatus.ENDED
        && session.getStatus() != SessionStatus.ABANDONED) {
      throw new ApiException(ApiErrorCode.SESSION_STATE_INVALID);
    }
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    String reasonJson = json.writeValueAsString(reasons);
    var saved = retrospectives.findById(id)
        .map(row -> {
          row.replace(request.rating().name(), reasonJson, note, now);
          return row;
        })
        .orElseGet(() -> retrospectives.save(SessionRetrospectiveEntity.create(id,
            request.rating().name(), reasonJson, note, now)));
    return response(saved);
  }

  private void validate(Rating rating, List<ReasonCode> reasons, String note) {
    if (rating == null || reasons.stream().anyMatch(java.util.Objects::isNull)
        || new HashSet<>(reasons).size() != reasons.size()) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    if (rating != Rating.BAD && (!reasons.isEmpty() || note != null)) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    if (note != null && note.codePointCount(0, note.length()) > MAX_NOTE) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    if (SensitiveText.contains(note)) {
      // 어떤 값이 걸렸는지는 응답·로그에 넣지 않고 필드만 알려 준다.
      throw ApiException.withDetails(ApiErrorCode.VALIDATION_ERROR,
          Map.of("fieldErrors", Map.of("note", "SENSITIVE")));
    }
  }

  private static long parseId(String raw) {
    try {
      long id = Long.parseLong(raw);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
  }

  private RetrospectiveResponse response(SessionRetrospectiveEntity row) {
    List<ReasonCode> reasons = json.readValue(row.getReasonCodesJson(),
        new TypeReference<List<ReasonCode>>() {});
    return new RetrospectiveResponse(row.getSessionId().toString(),
        Rating.valueOf(row.getRating()), reasons, row.getNote(), row.getUpdatedAt());
  }
}
