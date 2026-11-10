package com.capstone.backend.report.dto;

import java.util.List;

/** 학습 정리의 생성 상태는 세션 상태와 별개다. */
public record LearningSummaryResponse(Status learningSummaryStatus, String overview,
    List<KeyConcept> keyConcepts, String nextStartPrompt) {
  /** 생성자 #29가 저장할 공개 상태다. 조회 API는 상태를 추정하지 않는다. */
  public enum Status { QUEUED, GENERATING, READY, INSUFFICIENT_DATA, FAILED }

  /** 세션 근거에 기반한 개념 한 개다. 원문·URL을 담지 않는다. */
  public record KeyConcept(String name, String briefExplanation, String sessionEvidence) {}
}
