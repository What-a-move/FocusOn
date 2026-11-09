package com.capstone.backend.session.dto;

import com.capstone.backend.session.persistence.SessionStatus;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 세션 요청과 응답이다. */
public final class SessionDtos {
  private SessionDtos() {}

  /** 지원하는 세션 알림 채널이다. */
  public enum NotificationChannel { MOLE_UI, MACOS_NOTIFICATION, SOUND }

  /** 세션에 고정할 집중 설정이다. */
  public record SessionOptions(
      @NotNull @Schema(example = "NORMAL") InterventionLevel interventionLevel,
      @NotEmpty @Schema(example = "[\"MOLE_UI\", \"MACOS_NOTIFICATION\"]")
      List<@NotNull NotificationChannel> notificationChannels,
      @NotNull @Schema(example = "false",
          description = "카메라 분석 사용 여부. 서버는 저장된 카메라 동의를 확인합니다.")
      Boolean cameraAnalysisEnabled) {}

  /** Desktop에서 확인한 화면 분석 준비 상태다. */
  public record StartReadiness(
      @NotNull @Schema(example = "true", description = "Desktop이 확인한 화면 기록 준비 상태")
      Boolean screenAnalysisReady) {}

  /** 세션 시작 요청이다. */
  public record StartRequest(@NotBlank @Schema(example = "12") String goalId,
      @NotNull @Digits(integer = 8, fraction = 0)
      @Schema(example = "1800000", description = "5~300분, 1분 단위의 밀리초")
      BigDecimal targetDurationMs,
      @Schema(example = "Asia/Seoul") String timeZone,
      @NotNull @Valid SessionOptions sessionOptions,
      @NotNull @Valid StartReadiness startReadiness) {}

  /** 세션 생성 결과다. */
  public record StartResponse(String sessionId, SessionStatus status, long version,
      long targetDurationMs, long accumulatedActiveMs, Instant startedAt,
      String interventionPolicyVersion) {}

  /** 사용자의 활성 세션 정보다. */
  public record ActiveResponse(String sessionId, SessionStatus status, long version,
      String goalTextSnapshot, long targetDurationMs, long accumulatedActiveMs) {}

  /** 일시정지의 사용자 및 자동 복구 사유다. */
  public enum PauseReason { USER, REQUIRED_PERMISSION, EXTENSION_UNLINKED, APP_RESTART, SYSTEM }

  /** Desktop이 측정한 누적 시간으로 세션 일시정지를 요청한다. */
  public record PauseRequest(@NotNull @Schema(example = "USER") PauseReason pauseReason,
      @NotNull @PositiveOrZero @Schema(example = "1500000") Long accumulatedActiveMs) {}

  /** 일시정지 후 서버가 확정한 상태다. */
  public record PauseResponse(SessionStatus status, long version, long accumulatedActiveMs) {}

  /** 화면 분석 준비 상태와 함께 세션 재개를 요청한다. */
  public record ResumeRequest(@NotNull @Valid StartReadiness readiness) {}

  /** 재개된 세션과 서버가 기록한 재개 시각이다. */
  public record ResumeResponse(SessionStatus status, long version, Instant lastResumedAt) {}

  /** 추가 학습 시간이다. */
  public record ExtendRequest(@NotNull @Digits(integer = 8, fraction = 0)
      @Schema(example = "600000", description = "추가 시간: 5~300분, 1분 단위 밀리초")
      BigDecimal additionalDurationMs) {}

  /** 연장 후 확정된 상태와 목표 시간이다. */
  public record ExtendResponse(SessionStatus status, long version, long targetDurationMs,
      long accumulatedActiveMs, Instant lastResumedAt) {}

  /** Desktop이 관측한 시각과 monotonic 누적 시간이다. */
  public record HeartbeatRequest(@NotNull @Schema(example = "2026-11-07T12:00:00Z")
      Instant clientObservedAt, @NotNull @Schema(example = "10000")
      Long accumulatedActiveMs) {}

  /** 마지막으로 서버가 수신하고 확정한 신호다. */
  public record HeartbeatResponse(SessionStatus status, Instant lastHeartbeatAt,
      long accumulatedActiveMs, String pauseReason) {}
}
