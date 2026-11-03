package com.capstone.backend.session.persistence;

import java.util.List;

/** 서버가 저장하는 학습 세션 상태다. */
public enum SessionStatus {
  RUNNING, PAUSED, AUTO_PAUSED, AWAITING_END_CONFIRMATION, ENDING, ENDED, ABANDONED;

  /** 사용자별 활성 세션 제한에 포함되는 상태다. */
  public static List<SessionStatus> activeStatuses() {
    return List.of(RUNNING, PAUSED, AUTO_PAUSED, AWAITING_END_CONFIRMATION, ENDING);
  }
}
