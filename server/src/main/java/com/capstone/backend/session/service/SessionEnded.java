package com.capstone.backend.session.service;

import java.time.Instant;

/** 사용자 종료(ENDED)가 확정된 뒤 다른 도메인에 알리는 식별자 전용 내부 신호다. */
public record SessionEnded(long sessionId, Instant endedAt) {}
