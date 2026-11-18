package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.dto.AnalysisDtos.Response;
import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.DeviceHeaderValidator;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.SafeApiLog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 준비/완료 transaction 사이에서만 AI를 호출한다. */
@Service
@RequiredArgsConstructor
public class AnalysisService {
  private final AnalysisRunStore store;
  private final AnalysisAiClient ai;

  /** 인증된 Desktop에서 본인 세션의 분석 결과를 조회한다. */
  public List<Response> history(FocusOnPrincipal principal, String sessionId) {
    if (principal.deviceType() != DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.SESSION_QUERY_FORBIDDEN);
    }
    return store.history(principal.userId(), parseId(sessionId));
  }

  /** 인증된 화면 입력을 받아 원문 없는 결과를 반환한다. */
  public Response analyze(FocusOnPrincipal principal, String deviceId, String sessionId,
      Request request) {
    DeviceHeaderValidator.require(deviceId, principal);
    AnalysisPrivacy.validate(request);
    long start = System.nanoTime();
    try {
      var prepared = store.prepare(principal, parseId(sessionId), request);
      SafeApiLog.analysisEvent(prepared.guard() == null ? SafeApiLog.AnalysisEvent.GATE_PASS
          : SafeApiLog.AnalysisEvent.GATE_BLOCK, start);
      Response result = prepared.initialResponse();
      if (prepared.guard() == null) {
        var decision = prepared.latest() ? ai.analyze(prepared, request, principal.userId())
            : AnalysisDecision.unavailable();
        result = store.complete(principal, prepared, request, decision);
      }
      SafeApiLog.analysisStoreResult(start, result.relation(), result.confidence(),
          result.reasonCode());
      return result;
    } catch (org.springframework.dao.DataAccessException exception) {
      SafeApiLog.analysisEvent(SafeApiLog.AnalysisEvent.STORE_FAIL, start);
      throw exception;
    }
  }

  /** 잘못된 식별자도 존재·소유 여부를 노출하지 않는 동일한 세션404로 응답한다. */
  private static long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.SESSION_NOT_FOUND);
    }
  }
}
