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

  @Test
  void observationReceivedBeforeCooldownCannotQualifyByCompletingAfterward() {
    Instant at = Instant.parse("2026-11-09T00:00:00Z");
    var timeline = OffTaskTimeline.empty(at, "context", at).pending(1, at)
        .complete(1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, at);
    timeline = timeline.pending(2, at.plusSeconds(290))
        .complete(2, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR,
            at.plusSeconds(290), at.plusSeconds(301));
    assertFalse(timeline.evaluate("v1", InterventionLevel.NORMAL, at.plusSeconds(400),
        at.plusSeconds(300)).candidate());
    timeline = timeline.pending(3, at.plusSeconds(401))
        .complete(3, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR,
            at.plusSeconds(401), at.plusSeconds(402));
    assertTrue(timeline.evaluate("v1", InterventionLevel.NORMAL, at.plusSeconds(403),
        at.plusSeconds(300)).candidate());
  }


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
    // v1·v2 외의 정책 버전은 알림 후보를 만들지 않는다.
    assertFalse(state.evaluate("v3", InterventionLevel.NORMAL, START.plusSeconds(300), null)
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

  /** v2: 딴짓 40초 → 판단 보류 30초 → 딴짓 25초면 연속 딴짓은 보류를 뺀 65초로 이어진다. */
  @ParameterizedTest
  @EnumSource(value = ContentSource.class, names = {"DESKTOP_APP_OCR", "APP_META"})
  void v2UncertainPausesEpisodeWithoutCountingPausedTime(ContentSource uncertainSource) {
    var timeline = v2(empty(), 1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 0);
    // APP_META의 무관 판정은 UNCERTAIN으로 낮춰지므로 같은 멈춤 규칙을 탄다.
    Relation pause = uncertainSource == ContentSource.APP_META ? Relation.UNRELATED
        : Relation.UNCERTAIN;
    timeline = v2(timeline, 2, pause, uncertainSource, 40);
    assertEquals(1L, timeline.snapshot().openEpisode());
    timeline = v2(timeline, 3, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 70);
    assertEquals(1L, timeline.snapshot().openEpisode());
    var at = START.plusSeconds(95);
    assertEquals(Duration.ofSeconds(65),
        timeline.evaluate("v2", InterventionLevel.NORMAL, at, null).continuous());
    assertTrue(timeline.evaluate("v2", InterventionLevel.NORMAL, at, null).candidate());
  }

  /** v2: 분석 장애(UNCERTAIN)도 구간을 끊지 않는다. 보류 중에는 시간이 늘지 않는다. */
  @Test
  void v2AnalysisFailureDoesNotAccumulateWhilePaused() {
    var timeline = v2(empty(), 1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 0);
    timeline = v2(timeline, 2, Relation.UNCERTAIN, ContentSource.DESKTOP_APP_OCR, 50);
    assertEquals(Duration.ofSeconds(50), timeline.evaluate("v2", InterventionLevel.NORMAL,
        START.plusSeconds(500), null).continuous());
    assertFalse(timeline.evaluate("v2", InterventionLevel.NORMAL, START.plusSeconds(500), null)
        .candidate());
  }

  /** v2: 보류 중에는 기준을 넘긴 시간이 있어도 새 알림을 만들지 않고, 다음 무관 판정에서 다시 본다. */
  @Test
  void v2HoldBlocksCandidatesUntilNextUnrelated() {
    var timeline = v2(empty(), 1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 0);
    timeline = v2(timeline, 2, Relation.UNCERTAIN, ContentSource.DESKTOP_APP_OCR, 70);
    assertTrue(timeline.onHold());
    assertFalse(timeline.evaluate("v2", InterventionLevel.NORMAL, START.plusSeconds(300), null)
        .candidate());
    timeline = v2(timeline, 3, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 300);
    assertFalse(timeline.onHold());
    assertTrue(timeline.evaluate("v2", InterventionLevel.NORMAL, START.plusSeconds(301), null)
        .candidate());
  }

  /** v2에서도 관련 판정은 구간을 끊고, v1은 판단 보류에서 구간을 끊는 기존 동작을 유지한다. */
  @Test
  void relatedStillEndsEpisodeAndV1KeepsLegacyBreak() {
    var v2 = v2(empty(), 1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 0);
    v2 = v2(v2, 2, Relation.RELATED, ContentSource.DESKTOP_APP_OCR, 40);
    assertEquals(null, v2.snapshot().openEpisode());
    var v1 = unrelated(empty(), 1, 0).pending(2, START.plusSeconds(40))
        .complete(2, Relation.UNCERTAIN, ContentSource.DESKTOP_APP_OCR, START.plusSeconds(40));
    assertEquals(null, v1.snapshot().openEpisode());
  }

  private static OffTaskTimeline v2(OffTaskTimeline timeline, long run, Relation relation,
      ContentSource source, long seconds) {
    Instant at = START.plusSeconds(seconds);
    return timeline.pending(run, at).complete(run, relation, source, at, at, "v2");
  }

  /** 근거 판정은 구간을 이어 준 마지막 무관 판정이며, 판단 보류로 멈춰도 바뀌지 않는다. */
  @Test
  void offTaskRunTracksLastUnrelatedRunAcrossPauseAndClearsOnRelated() {
    var timeline = v2(empty(), 1, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 0);
    timeline = v2(timeline, 2, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 20);
    assertEquals(2L, timeline.snapshot().offTaskRun());
    timeline = v2(timeline, 3, Relation.UNCERTAIN, ContentSource.DESKTOP_APP_OCR, 40);
    assertEquals(3L, timeline.snapshot().latestRun());
    assertEquals(2L, timeline.snapshot().offTaskRun());
    timeline = v2(timeline, 4, Relation.RELATED, ContentSource.DESKTOP_APP_OCR, 60);
    assertEquals(null, timeline.snapshot().offTaskRun());
  }

  /** 근거 필드가 없던 이전 snapshot도 그대로 읽힌다. */
  @Test
  void legacySnapshotWithoutOffTaskRunStillLoads() {
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    var node = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(
        unrelated(empty(), 1, 0).snapshot());
    node.remove("offTaskRun");
    var restored = mapper.treeToValue(node, OffTaskTimeline.Snapshot.class);
    assertEquals(null, restored.offTaskRun());
    assertEquals(1L, restored.openEpisode());
  }

  /** 보통 강도: 0분·4분·8분에 시작한 40초 넘는 이탈 3회면 근거(3회, 첫 이탈부터 9분)를 낸다. */
  @Test
  void breakEvidenceNeedsThreeQualifyingEpisodesWithinFiveMinuteGaps() {
    var timeline = episode(empty(), 1, 0, 40);
    timeline = episode(timeline, 3, 240, 40);
    timeline = v2(timeline, 5, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 480);
    var at = START.plusSeconds(480 + 31);
    var evidence = timeline.breakEvidence("v2", InterventionLevel.NORMAL, at, null).orElseThrow();
    assertEquals(3, evidence.repeatCount());
    assertEquals(9, evidence.windowMinutes());
    // 지금 이탈이 반복 기준(30초)을 넘기 전에는 아직 제안하지 않는다.
    assertTrue(timeline.breakEvidence("v2", InterventionLevel.NORMAL,
        START.plusSeconds(480 + 30), null).isEmpty());
    // 알림 간격 중이면 제안하지 않는다.
    assertTrue(timeline.breakEvidence("v2", InterventionLevel.NORMAL, at,
        at.plusSeconds(60)).isEmpty());
  }

  /** 판단 보류로 멈춘 동안에는 반복 조건을 채웠어도 휴식 제안 근거를 내지 않는다. */
  @Test
  void breakEvidenceIsWithheldWhileOnHold() {
    var timeline = episode(episode(empty(), 1, 0, 40), 3, 240, 40);
    timeline = v2(timeline, 5, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 480);
    timeline = v2(timeline, 6, Relation.UNCERTAIN, ContentSource.DESKTOP_APP_OCR, 520);
    assertTrue(timeline.onHold());
    assertTrue(timeline.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(600),
        null).isEmpty());
  }

  @Test
  void breakEvidenceIgnoresLongGapsShortEpisodesAndClosedEpisode() {
    // 5분 넘게 벌어지면 사슬이 끊긴다.
    var gap = episode(episode(empty(), 1, 0, 40), 3, 400, 40);
    gap = v2(gap, 5, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 640);
    assertTrue(gap.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(700), null)
        .isEmpty());
    // 반복 기준 이하의 짧은 이탈은 세지 않는다.
    var shortOnes = episode(episode(empty(), 1, 0, 20), 3, 120, 20);
    shortOnes = v2(shortOnes, 5, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 240);
    assertTrue(shortOnes.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(300),
        null).isEmpty());
    // 지금 딴짓 중이 아니면 제안하지 않는다.
    var closed = episode(episode(episode(empty(), 1, 0, 40), 3, 120, 40), 5, 240, 40);
    assertTrue(closed.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(300), null)
        .isEmpty());
  }

  /** 지난 이탈 3회가 이어져 있어도 지금 이탈이 반복 기준을 넘기 전에는 제안하지 않는다. */
  @Test
  void breakEvidenceWaitsUntilCurrentEpisodeQualifies() {
    var timeline = episode(episode(episode(empty(), 1, 0, 40), 3, 120, 40), 5, 240, 40);
    timeline = v2(timeline, 7, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, 400);
    assertTrue(timeline.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(401),
        null).isEmpty());
    assertTrue(timeline.breakEvidence("v2", InterventionLevel.NORMAL, START.plusSeconds(430),
        null).isEmpty());
    var evidence = timeline.breakEvidence("v2", InterventionLevel.NORMAL,
        START.plusSeconds(431), null).orElseThrow();
    assertEquals(4, evidence.repeatCount());
  }

  private static OffTaskTimeline episode(OffTaskTimeline timeline, long run, long start,
      long seconds) {
    var opened = v2(timeline, run, Relation.UNRELATED, ContentSource.DESKTOP_APP_OCR, start);
    return v2(opened, run + 1, Relation.RELATED, ContentSource.DESKTOP_APP_OCR, start + seconds);
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
