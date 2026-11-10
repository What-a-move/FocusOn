package com.capstone.backend.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.service.SessionHeartbeatCleanup;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SessionHeartbeatCleanupTests {
  @Test
  void continuesAfterCandidateTransactionFails() {
    var sessions = mock(LearningSessionRepository.class);
    var users = mock(UserRepository.class);
    var transactions = mock(TransactionTemplate.class);
    var session = mock(LearningSessionEntity.class);
    var user = mock(UserEntity.class);
    when(sessions.findExpiredCandidates(any(), anyLong(), any()))
        .thenReturn(List.of(1L, 2L), List.of());
    when(sessions.findById(2L)).thenReturn(Optional.of(session));
    when(session.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(20L);
    when(users.findByIdForUpdateSkipLocked(20L)).thenReturn(Optional.of(user));
    doThrow(new IllegalStateException("synthetic"))
        .doAnswer(invocation -> {
          Consumer<TransactionStatus> action = invocation.getArgument(0);
          action.accept(mock(TransactionStatus.class));
          return null;
        }).when(transactions).executeWithoutResult(any());

    new SessionHeartbeatCleanup(sessions, users, transactions).cleanup();

    verify(sessions).abandonIfExpired(org.mockito.ArgumentMatchers.eq(2L), any(), any());
  }
}
