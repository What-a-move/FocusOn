package com.capstone.backend.report.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 생성자가 저장한 세션별 단일 정리를 읽는다. */
public interface LearningSummaryRepository extends JpaRepository<LearningSummaryEntity, Long> {}
