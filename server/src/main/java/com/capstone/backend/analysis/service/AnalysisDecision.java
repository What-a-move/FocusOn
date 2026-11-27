package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.MatchStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 원문·외부 오류 메시지를 포함하지 않는 정규화된 판정이다. matchStatus는 확정 RELATED일 때만 값이
 * 있다(DR-030).
 */
public record AnalysisDecision(Relation relation, Double confidence, ReasonCode reasonCode,
    AnalysisStatus status, String modelVersion, String promptVersion, MatchStatus matchStatus,
    Long matchedGoalId, @JsonIgnore ReviewEvidence reviewEvidence) {
  /** 확정 RELATED가 아니면 matchStatus를 남기지 않는다. */
  public AnalysisDecision {
    if (relation != Relation.RELATED || status != AnalysisStatus.COMPLETED) {
      matchStatus = null;
    }
    if (matchStatus != MatchStatus.MATCHED) {
      matchedGoalId = null;
    }
    if (matchedGoalId == null || matchedGoalId <= 0) {
      reviewEvidence = null;
    }
  }

  /** 기존 호출은 공부 개념 근거 없이 판정을 만들 수 있다. */
  public AnalysisDecision(Relation relation, Double confidence, ReasonCode reasonCode,
      AnalysisStatus status, String modelVersion, String promptVersion, MatchStatus matchStatus,
      Long matchedGoalId) {
    this(relation, confidence, reasonCode, status, modelVersion, promptVersion, matchStatus,
        matchedGoalId, null);
  }

  /** 기존 AI 응답에는 상태만 있을 수 있다. */
  public AnalysisDecision(Relation relation, Double confidence, ReasonCode reasonCode,
      AnalysisStatus status, String modelVersion, String promptVersion, MatchStatus matchStatus) {
    this(relation, confidence, reasonCode, status, modelVersion, promptVersion, matchStatus, null);
  }

  /** matchStatus가 없는 판정이다. */
  public AnalysisDecision(Relation relation, Double confidence, ReasonCode reasonCode,
      AnalysisStatus status, String modelVersion, String promptVersion) {
    this(relation, confidence, reasonCode, status, modelVersion, promptVersion, null);
  }

  /** 보조 매칭만 보류할 때 본 판정은 보존한다. */
  public AnalysisDecision withoutGoalAttribution() {
    return new AnalysisDecision(relation, confidence, reasonCode, status, modelVersion,
        promptVersion, MatchStatus.NOT_ENOUGH, null);
  }

  /** AI에서 수신한 공부 개념의 내부 유형이다. */
  public enum EvidenceType { CONCEPT }

  /** 로그에 개념 라벨을 남기지 않는 내부 판정 데이터다. */
  public record ReviewEvidence(String conceptLabel, EvidenceType evidenceType) {
    @Override
    public String toString() {
      return "ReviewEvidence[redacted]";
    }
  }

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
