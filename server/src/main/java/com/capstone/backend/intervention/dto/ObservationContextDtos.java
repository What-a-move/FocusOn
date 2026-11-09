package com.capstone.backend.intervention.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

/** 원문이나 앱 식별자를 포함하지 않는 전경 제어 계약이다. */
public final class ObservationContextDtos {
  private ObservationContextDtos() {}

  /** Extension 기기는 서버의 현재 연결에서 결정한다. */
  public enum Observer { DESKTOP, EXTENSION, NONE }

  /** nonce와 순번은 Desktop에서 영속화한다. */
  public record Put(@NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
      + "[89ab][0-9a-f]{3}-[0-9a-f]{12}") String clientContextId,
      @NotNull @Min(1) @Digits(integer = 16, fraction = 0)
      @DecimalMax("9007199254740991") BigDecimal foregroundSequence,
      @NotNull Observer observer) {}

  /** 남은 시간은 서버 계산이며 기기 시계와 expiresAt을 비교하지 않는다. */
  public record Item(String observationContextId, Observer observer, String observerDeviceId,
      long foregroundSequence, Instant createdAt, long leaseRemainingMs) {}
}
