package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;

/** 원문·외부 오류 메시지를 포함하지 않는 정규화된 판정이다. */
public record AnalysisDecision(Relation relation, Double confidence, ReasonCode reasonCode,
    AnalysisStatus status, String modelVersion, String promptVersion) {
  /** AI 미설정·장애·잘못된 응답은 경고 근거가 될 수 없다. */
  public static AnalysisDecision unavailable() {
    return new AnalysisDecision(Relation.UNCERTAIN, null, ReasonCode.ANALYSIS_UNAVAILABLE,
        AnalysisStatus.ERROR, null, null);
  }

  /** 사전 보호 결과는 AI 관련성 판정과 분리한다. */
  public static AnalysisDecision guarded(GuardStatus guard) {
    return new AnalysisDecision(Relation.valueOf(guard.name()), null, null,
        AnalysisStatus.SKIPPED, null, null);
  }
}
