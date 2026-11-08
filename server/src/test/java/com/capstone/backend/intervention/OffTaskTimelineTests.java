package com.capstone.backend.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.intervention.service.OffTaskTimeline;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OffTaskTimelineTests {
  private static final Instant START = Instant.parse("2026-11-09T00:00:00Z");

  @ParameterizedTest
  @EnumSource(InterventionLevel.class)
  void strictContinuousBoundaries(InterventionLevel level) {
    long threshold = switch (level) {
      case GENTLE -> 180;
      case NORMAL -> 60;
      case ACTIVE -> 30;
    };
    var timeline = unrelated(empty(), 1, 0);
    assertFalse(timeline.evaluate("v1", level, START.plusSeconds(threshold), null).candidate());
    assertTrue(timeline.evaluate("v1", level,
        START.plusSeconds(threshold).plusMillis(1), null).candidate());
  }

  @Test
  void regularReanalysisPreservesOneEpisodeAndExcludesPendingTime() {
    var timeline = unrelated(empty(), 1, 0);
    for (long run = 2; run <= 10; run++) {
      long requestAt = (run - 1) * 25;
      timeline = timeline.pending(run, START.plusSeconds(requestAt));
      assertFalse(timeline.evaluate("v1", InterventionLevel.GENTLE,
          START.plusSeconds(requestAt + 1), null).candidate());
      assertEquals(1L, timeline.snapshot().openEpisode());
      timeline = timeline.complete(run, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR,
          START.plusSeconds(requestAt + 2));
    }
    var result = timeline.evaluate("v1", InterventionLevel.GENTLE, START.plusSeconds(230), null);
    assertEquals(Duration.ofSeconds(212), result.continuous());
    assertTrue(result.candidate());
    assertEquals(1, result.repeatedIntervals());
    assertEquals(1L, timeline.snapshot().openEpisode());
  }

  @ParameterizedTest
  @EnumSource(value = Relation.class, names = {"UNCERTAIN", "RELATED", "EXCLUDED",
      "PRIVACY_BLOCKED"})
  void completedNonUnrelatedBreaksInsteadOfRejoining(Relation relation) {
    var first = unrelated(empty(), 1, 0).pending(2, START.plusSeconds(25));
    var completed = first.complete(2, relation, ContentSource.DESKTOP_APP_OCR,
        START.plusSeconds(27));
    var next = unrelated(completed, 3, 30);
    assertEquals(2L, next.snapshot().openEpisode());
    assertEquals(Duration.ofSeconds(20), next.evaluate("v1", InterventionLevel.NORMAL,
        START.plusSeconds(50), null).continuous());
    assertFalse(next.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(50), null)
        .candidate());
  }

  @Test
  void lateCompletionAndDuplicatePendingDoNotResumeWrongRun() {
    var state = unrelated(empty(), 1, 0).pending(2, START.plusSeconds(20))
        .pending(3, START.plusSeconds(25));
    assertEquals(state.snapshot(), state.complete(2, Relation.UNRELATED,
        ContentSource.DESKTOP_APP_OCR, START.plusSeconds(30)).snapshot());
    assertEquals(state.snapshot(), state.pending(3, START.plusSeconds(40)).snapshot());
    assertFalse(state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(100), null)
        .candidate());
  }

  @ParameterizedTest
  @EnumSource(value = ContentSource.class, names = {"APP_META", "TAB_META"})
  void metadataNeverAccumulatesOffTask(ContentSource source) {
    var state = empty().pending(1, START).complete(1, Relation.UNRELATED, source, START);
    assertFalse(state.evaluate("v1", InterventionLevel.ACTIVE, START.plusSeconds(300), null)
        .candidate());
  }

  @Test
  void rollingClipsTimeAndCountsOpenEpisodeOnlyOnce() {
    var state = unrelated(empty(), 1, 0).pending(2, START.plusSeconds(40))
        .complete(2, Relation.RELATED, ContentSource.DESKTOP_APP_OCR, START.plusSeconds(40));
    state = unrelated(state, 3, 50);
    assertTrue(state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(81), null)
        .candidate());
    assertEquals(2, state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(81), null)
        .repeatedIntervals());
    // 610에서 첫 구간은 창 안에 정확히 30초만 남으므로 엄격한 초과 기준에는 들지 않는다.
    assertEquals(1, state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(610), null)
        .repeatedIntervals());
  }

  @Test
  void heartbeatCannotReissueOldCandidateWhenCooldownEnds() {
    var state = unrelated(empty(), 1, 0);
    Instant cooldown = START.plusSeconds(300);
    assertFalse(state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(301), cooldown)
        .candidate());
    state = unrelated(state, 2, 302);
    assertTrue(state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(303), cooldown)
        .candidate());
  }

  @Test
  void unknownPolicyAndInterruptedContextCannotGenerate() {
    var state = unrelated(empty(), 1, 0);
    assertFalse(state.evaluate("v2", InterventionLevel.NORMAL, START.plusSeconds(300), null)
        .candidate());
    state = state.interrupt(START.plusSeconds(20));
    assertFalse(state.evaluate("v1", InterventionLevel.NORMAL, START.plusSeconds(300), null)
        .candidate());
  }

  @Test
  void snapshotRestorePreservesPendingAndServerClockRollbackDoesNotAddTime() {
    var frozen = unrelated(empty(), 1, 0).pending(2, START.plusSeconds(20));
    var restored = new OffTaskTimeline(frozen.snapshot());
    restored = restored.complete(2, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR,
        START.plusSeconds(10));
    assertEquals(Duration.ofSeconds(20), restored.evaluate("v1", InterventionLevel.NORMAL,
        START.plusSeconds(15), null).continuous());
  }

  private static OffTaskTimeline empty() {
    return OffTaskTimeline.empty(START, "synthetic-context", START);
  }

  private static OffTaskTimeline unrelated(OffTaskTimeline timeline, long run, long seconds) {
    return timeline.pending(run, START.plusSeconds(seconds))
        .complete(run, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR,
            START.plusSeconds(seconds));
  }
}
