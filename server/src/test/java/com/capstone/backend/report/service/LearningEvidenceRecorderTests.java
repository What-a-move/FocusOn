package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.capstone.backend.report.persistence.LearningReviewEvidenceEntity;
import com.capstone.backend.report.persistence.LearningReviewEvidenceRepository;
import com.capstone.backend.report.persistence.LearningSummaryEntity;
import com.capstone.backend.report.persistence.LearningSummaryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LearningEvidenceRecorderTests {
  private static final long SESSION_ID = 1L;
  private static final Instant RUNNING_SINCE = Instant.parse("2026-11-29T00:00:00Z");
  private static final Instant ENDED_AT = RUNNING_SINCE.plusSeconds(120);
  private static final Instant CUTOFF = ENDED_AT.plusSeconds(30);

  private final LearningSummaryRepository summaries = mock(LearningSummaryRepository.class);
  private final LearningReviewEvidenceRepository evidences =
      mock(LearningReviewEvidenceRepository.class);
  private final Clock clock = mock(Clock.class);
  private final LearningEvidenceRecorder recorder =
      new LearningEvidenceRecorder(evidences, summaries, clock);

  @Test
  void acceptsEvidenceSavedAtCutoff() {
    summary(RUNNING_SINCE);
    when(clock.instant()).thenReturn(CUTOFF);

    recordLate();

    var captured = ArgumentCaptor.forClass(LearningReviewEvidenceEntity.class);
    verify(evidences).saveAndFlush(captured.capture());
    assertEquals(CUTOFF, captured.getValue().getCreatedAt());
    verify(evidences, never()).delete(any());
  }

  @Test
  void removesUncommittedEvidenceWhenWritingCrossesCutoff() {
    summary(RUNNING_SINCE);
    when(clock.instant()).thenReturn(CUTOFF.minusMillis(1), CUTOFF.plusMillis(1));

    recordLate();

    var captured = ArgumentCaptor.forClass(LearningReviewEvidenceEntity.class);
    verify(evidences).saveAndFlush(captured.capture());
    verify(evidences).delete(captured.getValue());
  }

  @Test
  void skipsExistingQueueRowsWithoutAnEndingRunSnapshot() {
    summary(null);

    recordLate();

    verifyNoInteractions(evidences);
  }

  private void summary(Instant runningSince) {
    when(summaries.findForUpdate(SESSION_ID)).thenReturn(Optional.of(
        LearningSummaryEntity.queued(SESSION_ID, CUTOFF, ENDED_AT, runningSince)));
  }

  private void recordLate() {
    recorder.recordLate(SESSION_ID, "Spring transactions", "CONCEPT",
        RUNNING_SINCE.plusSeconds(50), "CHROME_DOM", RUNNING_SINCE);
  }
}
