package com.capstone.backend.intervention.service;

import java.time.Instant;

/** 원문 없이 필수 동의 중단 시점만 전달하는 transaction 내부 신호다. */
public record RequiredConsentWithdrawal(long userId, Instant withdrawnAt) {}
