package com.capstone.backend.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 세션 끝 회고 API 요청·응답이다(#318). */
public final class RetrospectiveDtos {
  private RetrospectiveDtos() {}

  /** 한 줄 회고 평가다. */
  public enum Rating { GOOD, OKAY, BAD }

  /** 「별로였어」 사유다. SELF_는 사용자 사정, APP_은 앱 문제다. */
  public enum ReasonCode {
    SELF_TIRED, SELF_NOISE, SELF_TOO_MANY_GOALS,
    APP_FALSE_ALERT, APP_TOO_FREQUENT, APP_LATE_ALERT, APP_BLUR_ANNOYING
  }

  /** 회고 저장 요청이다. 사유·메모는 BAD일 때만 받는다. */
  @Schema(example = "{\"rating\":\"BAD\",\"reasonCodes\":[\"APP_FALSE_ALERT\",\"SELF_TIRED\"],"
      + "\"note\":\"알림이 너무 자주 떴어요\"}")
  public record RetrospectiveRequest(@NotNull Rating rating, List<ReasonCode> reasonCodes,
      @Schema(description = "0~100자, BAD일 때만 (선택)") String note) {}

  /** 저장된 회고다. */
  public record RetrospectiveResponse(String sessionId, Rating rating,
      List<ReasonCode> reasonCodes, String note, Instant updatedAt) {}
}
