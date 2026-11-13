package com.capstone.backend.report.dto;

import com.capstone.backend.session.persistence.SessionStatus;
import java.time.Instant;

/** 서버가 확정한 종료 결과와 실제 연장 이력이다. 미집계 선택 필드는 생략한다. */
public record SessionSummaryResponse(String sessionId, String goalSnapshot,
    long targetDurationMs, long accumulatedActiveMs, SessionStatus status,
    Instant startedAt, Instant endedAt, long extensionCount, long extendedDurationMs,
    long interventionCount) {}
