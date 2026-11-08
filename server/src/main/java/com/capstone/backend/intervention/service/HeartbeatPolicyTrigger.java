package com.capstone.backend.intervention.service;

/** 유효 RUNNING heartbeat commit 뒤 전달하는 식별자 전용 내부 신호다. */
public record HeartbeatPolicyTrigger(long userId, long sessionId) {}
