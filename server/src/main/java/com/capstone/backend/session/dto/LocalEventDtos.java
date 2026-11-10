package com.capstone.backend.session.dto;

import com.capstone.backend.session.persistence.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 로컬 권한 이벤트의 코드와 시각만 받고 원문은 보관하지 않는다. */
public final class LocalEventDtos {
  private LocalEventDtos() {}

  /** 현재 Desktop과 Extension이 보내는 권한 유실 코드다. */
  public enum Code { LOCAL_SCREEN_PERMISSION_REVOKED, LOCAL_EXTENSION_HOST_PERMISSION_MISSING }

  /** occurredAt은 기기 시각이며 서버의 생존 시각으로 사용하지 않는다. */
  public record Request(@NotNull @Schema(example = "LOCAL_SCREEN_PERMISSION_REVOKED")
      Code localEventCode, @NotNull @Schema(example = "2026-11-09T01:00:00Z")
      Instant occurredAt) {}

  /** 서버가 현재 확정한 세션 상태다. */
  public record Response(SessionStatus status, long version, long accumulatedActiveMs,
      String pauseReason) {}
}
