package com.capstone.backend.analysis.persistence;

import com.capstone.backend.analysis.dto.AnalysisDtos.AnalysisStatus;
import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.ReasonCode;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import com.capstone.backend.analysis.dto.AnalysisDtos.Response;
import com.capstone.backend.analysis.service.AnalysisDecision;
import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원문 없이 출처·관측 시각·정규화된 결과만 저장하는 분석 이력이다. */
@Entity
@Table(name = "analysis_runs", uniqueConstraints = @UniqueConstraint(
    name = "uq_analysis_client_run", columnNames = {"session_id", "client_run_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRunEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private LearningSessionEntity session;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private UserDeviceEntity device;

  @Column(name = "client_run_id", nullable = false)
  private UUID clientRunId;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_source", nullable = false, length = 32)
  private ContentSource contentSource;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false)
  private long sequence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Relation relation;

  @Column(name = "relation_confidence", precision = 4, scale = 3)
  private BigDecimal relationConfidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "analysis_status", nullable = false, length = 32)
  private AnalysisStatus analysisStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", length = 80)
  private ReasonCode reasonCode;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Column(name = "model_version", length = 64)
  private String modelVersion;

  @Column(name = "prompt_version", length = 64)
  private String promptVersion;

  @Column(name = "policy_version", nullable = false, length = 64)
  private String policyVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** AI 호출 전 안전한 결과로 예약해 프로세스 중단도 확정 판정으로 남지 않게 한다. */
  public static AnalysisRunEntity reserve(LearningSessionEntity session, UserDeviceEntity device,
      Request request, Instant now) {
    var run = new AnalysisRunEntity();
    run.session = session;
    run.device = device;
    run.clientRunId = request.runUuid();
    run.contentSource = request.contentSource();
    run.observedAt = request.observedAt();
    run.receivedAt = now;
    run.sequence = request.sequence().longValueExact();
    run.policyVersion = session.getInterventionPolicyVersion();
    run.createdAt = now;
    run.complete(AnalysisDecision.unavailable());
    return run;
  }

  /** 해당 실행의 결과만 변경하며 최신 관측 포인터를 덮어쓰지 않는다. */
  public void complete(AnalysisDecision decision) {
    relation = decision.relation();
    relationConfidence = decision.confidence() == null ? null
        : BigDecimal.valueOf(decision.confidence()).setScale(3, java.math.RoundingMode.HALF_UP);
    reasonCode = decision.reasonCode();
    analysisStatus = decision.status();
    modelVersion = decision.modelVersion();
    promptVersion = decision.promptVersion();
    errorCode = reasonCode == ReasonCode.ANALYSIS_UNAVAILABLE
        ? "ANALYSIS_SERVICE_UNAVAILABLE" : null;
  }

  /** 저장한 값으로 응답을 만들어 DB와 응답의 정밀도를 일치시킨다. */
  public Response response() {
    return new Response(id.toString(), analysisStatus, relation,
        relationConfidence == null ? null : relationConfidence.doubleValue(), reasonCode,
        observedAt, policyVersion);
  }

  /** metadata만 근거인 무관 판정은 후속 Policy Engine의 이탈 시간에 포함하지 않는다. */
  public Relation effectiveRelation() {
    return relation == Relation.UNRELATED
        && (contentSource == ContentSource.APP_META || contentSource == ContentSource.TAB_META)
        ? Relation.UNCERTAIN : relation;
  }
}
