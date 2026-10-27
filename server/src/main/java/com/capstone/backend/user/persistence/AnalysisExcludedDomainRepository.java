package com.capstone.backend.user.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분석 제외 host를 사용자 단위로 교체한다. */
public interface AnalysisExcludedDomainRepository
    extends JpaRepository<AnalysisExcludedDomainEntity, Long> {
  /** 사용자의 host 목록을 정렬해 조회한다. */
  List<AnalysisExcludedDomainEntity> findAllByUserIdOrderByHost(Long userId);

  /** 사용자의 기존 host 목록을 모두 삭제한다. */
  void deleteByUserId(Long userId);
}
