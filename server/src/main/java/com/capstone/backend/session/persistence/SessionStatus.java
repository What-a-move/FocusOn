package com.capstone.backend.session.persistence;

/** 서버가 저장하는 학습 세션 상태다. */
public enum SessionStatus {
  RUNNING, PAUSED, AUTO_PAUSED, AWAITING_END_CONFIRMATION, ENDING, ENDED, ABANDONED
}
