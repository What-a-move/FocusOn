package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.GuardStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.persistence.AnalysisRunEntity;

/** 다른 clock의 시각을 추정하지 않고 순서가 확실한 관측만 현재 판정으로 사용한다. */
final class AnalysisObservationOrder {
  private AnalysisObservationOrder() {}

  static Ordering classify(Request request, AnalysisRunEntity current, AnalysisRunEntity source) {
    if (request.observationSequence() != null) {
      return new Ordering(true, false);
    }
    if (current != null && current.getObservationSequence() != null) {
      return new Ordering(false, false);
    }
    if (source != null && request.sequence().longValueExact() <= source.getSequence()) {
      return new Ordering(false, false);
    }
    boolean ambiguous = current != null && (current.isOrderingUntrusted()
        || request.observedAt().isBefore(current.getObservedAt()));
    return new Ordering(true, ambiguous);
  }

  /** 순서를 확정할 수 없을 때 기존 보호 상태를 일반 콘텐츠가 완화하지 못하게 한다. */
  static GuardStatus retainGuard(GuardStatus guard, AnalysisRunEntity current) {
    if (guard == GuardStatus.PRIVACY_BLOCKED || current == null) {
      return guard;
    }
    if (current.getRelation() == Relation.PRIVACY_BLOCKED) {
      return GuardStatus.PRIVACY_BLOCKED;
    }
    return guard != null ? guard : current.getRelation() == Relation.EXCLUDED
        ? GuardStatus.EXCLUDED : null;
  }

  record Ordering(boolean eligible, boolean untrusted) {}
}
