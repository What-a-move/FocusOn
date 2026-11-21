package com.capstone.backend.report.dto;

import com.capstone.backend.report.dto.LearningSummaryResponse.KeyConcept;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 학습 노트 목록이다. READY 정리만 담고 원문·URL·카메라 정보는 담지 않는다. */
public record LearningSummaryListResponse(List<Item> items, String nextCursor) {
  /** 회차 한 개의 노트 요약이다. ID는 string이다. */
  public record Item(String sessionId, String goalSnapshot, LocalDate studyDate,
      Instant startedAt, Instant endedAt, long accumulatedActiveMs, String overview,
      List<KeyConcept> keyConcepts) {}
}
