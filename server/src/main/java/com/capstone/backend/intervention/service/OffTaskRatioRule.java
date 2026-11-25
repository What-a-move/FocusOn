package com.capstone.backend.intervention.service;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional rolling-ratio rule. Both values default to zero until the product policy is set. */
@Component
public record OffTaskRatioRule(int percent, int minimumObservedSeconds) {
  static final OffTaskRatioRule DISABLED = new OffTaskRatioRule(0, 0);

  /** Accepts a configured pair or disables the ratio with two zeros. */
  public OffTaskRatioRule(
      @Value("${focuson.intervention.off-task-ratio.percent:0}") int percent,
      @Value("${focuson.intervention.off-task-ratio.minimum-observed-seconds:0}")
      int minimumObservedSeconds) {
    if (percent < 0 || percent > 100
        || minimumObservedSeconds < 0 || minimumObservedSeconds > 600
        || (percent == 0) != (minimumObservedSeconds == 0)) {
      throw new IllegalArgumentException(
          "Off-task ratio needs both a percent (1-100) and observed seconds (1-600), or two zeros");
    }
    this.percent = percent;
    this.minimumObservedSeconds = minimumObservedSeconds;
  }

  boolean enabled() {
    return percent > 0;
  }

  boolean matches(long unrelatedNanos, long observedNanos) {
    return observedNanos >= TimeUnit.SECONDS.toNanos(minimumObservedSeconds)
        && unrelatedNanos * 100 >= observedNanos * percent;
  }
}
