package com.capstone.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** 공개 분석 계약. 원문을 포함하는 객체는 기본 toString으로 출력하지 않는다. */
public final class AnalysisDtos {
  private AnalysisDtos() {}

  /** MVP 화면 분석 입력 출처. viewport OCR과 카메라 신호는 별도 범위다. */
  public enum ContentSource { APP_META, TAB_META, CHROME_DOM, DESKTOP_APP_OCR }

  /** 공개 관련성 또는 사전 보호 상태다. */
  public enum Relation { RELATED, UNRELATED, UNCERTAIN, EXCLUDED, PRIVACY_BLOCKED }

  /**
   * RELATED 판정이 할 일과 얼마나 맞는지(DR-030). MATCHED는 할 일과 맞음, FAR는 공부지만 할 일과
   * 멂, NOT_ENOUGH는 비교할 근거가 부족함이다. RELATED가 아니면 값이 없다.
   */
  public enum MatchStatus { MATCHED, FAR, NOT_ENOUGH }

  /** AI 실행 여부와 처리 결과를 관련성과 분리한다. */
  public enum AnalysisStatus { COMPLETED, NEEDS_CONFIRMATION, SKIPPED, ERROR }

  /** AI를 호출하기 전 클라이언트가 확인한 차단 상태다. */
  public enum GuardStatus {
    EXCLUDED, PRIVACY_BLOCKED, MESSENGER;

    /** Guard status is negotiated by name. Enum ordinals must not mark a run as protected. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static GuardStatus fromJson(JsonNode value) {
      if (value == null || value.isNull()) {
        return null;
      }
      if (!value.isString()) {
        throw new IllegalArgumentException("guardStatus must be a string");
      }
      return valueOf(value.asString());
    }
  }

  /** 판정 원인을 원문 없이 전달한다. */
  public enum ReasonCode {
    SEMANTIC_GOAL_MATCH, SEMANTIC_GOAL_MISMATCH, INSUFFICIENT_CONTEXT,
    AMBIGUOUS_CONTEXT, ANALYSIS_UNAVAILABLE
  }

  /** 저장하지 않는 일회성 화면 메타데이터다. */
  public record Metadata(@Size(max = 256) String appName,
      @Size(max = 800) String windowTitle, @Size(max = 800) String pageTitle,
      @Size(max = 253) String urlHost, String messengerId,
      @JsonAnySetter @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(hidden = true)
      Map<String, Object> unknownFields) {
    /** 원문 보관 없이 미정의 키가 있었는지만 기억한다. */
    public Metadata {
      unknownFields = unknownFieldMarker(unknownFields);
    }

    /** 기존 화면 메타데이터에는 보호된 메신저 ID가 없다. */
    public Metadata(String appName, String windowTitle, String pageTitle, String urlHost) {
      this(appName, windowTitle, pageTitle, urlHost, null);
    }

    /** 서버에서 메타데이터를 직접 구성할 때 사용한다. */
    public Metadata(String appName, String windowTitle, String pageTitle, String urlHost,
        String messengerId) {
      this(appName, windowTitle, pageTitle, urlHost, messengerId, null);
    }

    @Override
    public String toString() {
      return "Metadata[redacted]";
    }
  }

  /** 저장·로그·재시도 큐에 남기지 않는 추출 텍스트다. */
  public record ContentPayload(@NotNull @Size(max = 2000) String text) {
    @Override
    public String toString() {
      return "ContentPayload[redacted]";
    }
  }

  /** 클라이언트 목표나 정책 버전은 받지 않고 서버 snapshot을 사용한다. */
  public record Request(
      @NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
          + "[89ab][0-9a-f]{3}-[0-9a-f]{12}") String clientRunId,
      @NotNull ContentSource contentSource, @NotNull Instant observedAt,
      @NotNull @Min(1) @Digits(integer = 19, fraction = 0)
      @DecimalMax("9223372036854775807") BigDecimal sequence, GuardStatus guardStatus,
      @Valid Metadata metadata, @Valid ContentPayload contentPayload,
      @Min(1) @Digits(integer = 16, fraction = 0)
      @DecimalMax("9007199254740991") BigDecimal observationSequence,
      @Pattern(regexp = "[1-9][0-9]{0,18}") String observationContextId,
      @JsonAnySetter @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Schema(hidden = true)
      Map<String, Object> unknownFields) {
    /** 원문 보관 없이 미정의 키가 있었는지만 기억한다. */
    public Request {
      unknownFields = unknownFieldMarker(unknownFields);
    }

    /** 기존 source별 번호 요청은 새 선택 필드 없이 계속 받을 수 있다. */
    public Request(String clientRunId, ContentSource contentSource, Instant observedAt,
        BigDecimal sequence, GuardStatus guardStatus, Metadata metadata,
        ContentPayload contentPayload) {
      this(clientRunId, contentSource, observedAt, sequence, guardStatus, metadata,
          contentPayload, null, null);
    }

    /** 관측 순번만 제공하는 기존 내부 호출도 호환한다. */
    public Request(String clientRunId, ContentSource contentSource, Instant observedAt,
        BigDecimal sequence, GuardStatus guardStatus, Metadata metadata,
        ContentPayload contentPayload, BigDecimal observationSequence) {
      this(clientRunId, contentSource, observedAt, sequence, guardStatus, metadata,
          contentPayload, observationSequence, null);
    }

    /** 기존 전경 참조를 제공하는 호출도 호환한다. */
    public Request(String clientRunId, ContentSource contentSource, Instant observedAt,
        BigDecimal sequence, GuardStatus guardStatus, Metadata metadata,
        ContentPayload contentPayload, BigDecimal observationSequence,
        String observationContextId) {
      this(clientRunId, contentSource, observedAt, sequence, guardStatus, metadata,
          contentPayload, observationSequence, observationContextId, null);
    }

    @Override
    public String toString() {
      return "AnalysisRequest[redacted]";
    }

    /** 검증된 공개 UUID를 DB 값으로 변환한다. */
    public UUID runUuid() {
      return UUID.fromString(clientRunId);
    }
  }

  private static Map<String, Object> unknownFieldMarker(Map<String, Object> fields) {
    return fields == null || fields.isEmpty() ? Map.of() : Map.of("present", true);
  }

  /** UNRELATED 판정에만 붙는 딴짓 종류다(재설계 B). */
  public enum OffTaskCategory { VIDEO, SNS, COMMUNITY, SHOPPING, GAME, MESSENGER, NEWS, OTHER }

  /** 클라이언트가 사용하는 공개 분석 결과다. offTaskCategory는 UNRELATED일 때만 값이 있다. */
  public record Response(String analysisRunId, AnalysisStatus analysisStatus, Relation relation,
      Double confidence, ReasonCode reasonCode, Instant observedAt, String policyVersion,
      MatchStatus matchStatus, String matchedGoalId, long goalVersion,
      OffTaskCategory offTaskCategory) {}
}
