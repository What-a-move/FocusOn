package com.capstone.backend.analysis.persistence;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 원본 이력과 현재 판정용 관측 순서를 분리한다. */
public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, Long> {
  /** clientRunId는 session 안에서 유일하다. */
  boolean existsBySessionIdAndClientRunId(Long sessionId, UUID clientRunId);

  /** 번호 모드가 최우선이며 기존 요청은 번호 모드나 순서 불명 상태를 되돌리지 못한다. */
  default Optional<AnalysisRunEntity> findCurrent(Long sessionId, Long deviceId) {
    var first = PageRequest.of(0, 1);
    var numbered = findNumbered(sessionId, deviceId, first).stream().findFirst();
    if (numbered.isPresent()) {
      return numbered;
    }
    var uncertain = findUntrusted(sessionId, deviceId, first).stream().findFirst();
    return uncertain.isPresent() ? uncertain
        : findLegacy(sessionId, deviceId, first).stream().findFirst();
  }

  /** source와 기기 시각에 의존하지 않는 기기 전체 관측 번호다. */
  @Query("select r from AnalysisRunEntity r where r.session.id = :sessionId "
      + "and r.device.id = :deviceId and r.currentEligible = true "
      + "and r.observationSequence is not null order by r.observationSequence desc, r.id desc")
  List<AnalysisRunEntity> findNumbered(@Param("sessionId") Long sessionId,
      @Param("deviceId") Long deviceId, Pageable page);

  /** 순서 불명 모드에서는 최근 접수한 안전 결과가 과거 미래 시각보다 우선한다. */
  @Query("select r from AnalysisRunEntity r where r.session.id = :sessionId "
      + "and r.device.id = :deviceId and r.currentEligible = true "
      + "and r.orderingUntrusted = true order by r.id desc")
  List<AnalysisRunEntity> findUntrusted(@Param("sessionId") Long sessionId,
      @Param("deviceId") Long deviceId, Pageable page);

  /** 번호 없는 기존 형식은 명백한 과거 이력을 제외하고 관측 시각으로 정렬한다. */
  @Query("select r from AnalysisRunEntity r where r.session.id = :sessionId "
      + "and r.device.id = :deviceId and r.currentEligible = true "
      + "order by r.observedAt desc, r.id desc")
  List<AnalysisRunEntity> findLegacy(@Param("sessionId") Long sessionId,
      @Param("deviceId") Long deviceId, Pageable page);

  /** source별 오래된 sequence는 시계 보정으로 해석하지 않는다. */
  Optional<AnalysisRunEntity>
      findFirstBySessionIdAndDeviceIdAndContentSourceOrderBySequenceDescIdDesc(
          Long sessionId, Long deviceId, ContentSource source);

  /** 같은 기기의 전역 번호 중복을 DB 제약 전에 일관된409로 반환한다. */
  boolean existsBySessionIdAndDeviceIdAndObservationSequence(
      Long sessionId, Long deviceId, Long observationSequence);
}
