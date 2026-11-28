package com.capstone.backend.intervention.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessengerTimelineTests {
  @Test
  void countsUnregisteredTimeAndExcludesLearningTimeFromBothSidesOfTheRatio() {
    var start = Instant.parse("2026-11-28T00:00:00Z");
    var firstUnregistered = start.plusSeconds(20);
    var learning = start.plusSeconds(40);
    var nextUnregistered = start.plusSeconds(100);
    var at = start.plusSeconds(115);
    var timeline = OffTaskTimeline.empty(start, "1", start, 1)
        .pending(1, start).completeEffective(1, Relation.RELATED, start, start, "v2", true)
        .pending(2, firstUnregistered).completeEffective(2, Relation.UNRELATED,
            firstUnregistered, firstUnregistered, "v2", true)
        .pending(3, learning).interrupt(learning)
        .pending(4, nextUnregistered).completeEffective(4, Relation.UNRELATED,
            nextUnregistered, nextUnregistered, "v2", true);

    // The ratio is (20 + 15) / (20 + 20 + 15). The 60-second learning interval is omitted.
    var evaluation = timeline.evaluate("v2", InterventionLevel.GENTLE, at, null,
        new OffTaskRatioRule(63, 55));
    assertEquals(Duration.ofSeconds(15), evaluation.continuous());
    assertTrue(evaluation.candidate());
    assertFalse(timeline.evaluate("v2", InterventionLevel.GENTLE, at, null,
        new OffTaskRatioRule(64, 55)).candidate());
    assertFalse(timeline.evaluate("v2", InterventionLevel.GENTLE, at, null,
        new OffTaskRatioRule(63, 56)).candidate());
  }
}
