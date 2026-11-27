package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.persistence.AnalysisRunEntity;
import com.capstone.backend.analysis.service.AnalysisDecision.EvidenceType;
import com.capstone.backend.analysis.service.AnalysisDecision.ReviewEvidence;
import com.capstone.backend.analysis.service.AnalysisRunStore.Prepared;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnalysisRunStoreTests {
  private static final Instant STARTED = Instant.parse("2026-11-26T01:00:00Z");
  private final Prepared prepared = new Prepared(10, 20, 30, 0, STARTED, "goal", "v1", null,
      true, null);
  private final AnalysisRunEntity run = mock(AnalysisRunEntity.class);
  private final LearningSessionEntity session = mock(LearningSessionEntity.class);

  @BeforeEach
  void currentRunningObservation() {
    when(run.isCurrentEligible()).thenReturn(true);
    when(session.getStatus()).thenReturn(SessionStatus.RUNNING);
    when(session.getGoalVersion()).thenReturn(1L);
    when(session.getLastResumedAt()).thenReturn(STARTED);
  }

  @Test
  void evidenceSurvivesOnlyForGoalsInTheSnapshot() {
    var matched = matched(30);
    assertSame(matched, AnalysisRunStore.finalDecision(prepared, run, session, null, matched));

    var rejected = AnalysisRunStore.finalDecision(prepared, run, session, null, matched(31));
    assertEquals(Relation.RELATED, rejected.relation());
    assertEquals(MatchStatus.NOT_ENOUGH, rejected.matchStatus());
    assertNull(rejected.matchedGoalId());
    assertNull(rejected.reviewEvidence());
  }

  @Test
  void changedGoalVersionClearsEvidence() {
    when(session.getGoalVersion()).thenReturn(2L);
    assertUncertainWithoutEvidence();
  }

  @Test
  void sessionEndClearsEvidence() {
    when(session.getStatus()).thenReturn(SessionStatus.ENDED);
    assertUncertainWithoutEvidence();
  }

  @Test
  void resumedSessionClearsEvidenceFromPreviousRunningInterval() {
    when(session.getLastResumedAt()).thenReturn(STARTED.plusSeconds(30));
    assertUncertainWithoutEvidence();
  }

  @ParameterizedTest
  @EnumSource(GuardStatus.class)
  void protectionChangeClearsEvidence(GuardStatus guard) {
    var guarded = AnalysisRunStore.finalDecision(prepared, run, session, guard, matched(30));
    assertEquals(Relation.valueOf(guard.name()), guarded.relation());
    assertNull(guarded.reviewEvidence());
  }

  private void assertUncertainWithoutEvidence() {
    var decision = AnalysisRunStore.finalDecision(prepared, run, session, null, matched(30));
    assertEquals(Relation.UNCERTAIN, decision.relation());
    assertNull(decision.reviewEvidence());
  }

  private static AnalysisDecision matched(long goalId) {
    return new AnalysisDecision(Relation.RELATED, .9, ReasonCode.SEMANTIC_GOAL_MATCH,
        AnalysisStatus.COMPLETED, "model", "prompt", MatchStatus.MATCHED, goalId,
        new ReviewEvidence("React useState", EvidenceType.CONCEPT));
  }
}
