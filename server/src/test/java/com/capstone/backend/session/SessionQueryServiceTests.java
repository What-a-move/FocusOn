package com.capstone.backend.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.service.SessionQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유권 조건과 입력 경계 및 다음 페이지 커서를 확인한다. */
class SessionQueryServiceTests {
  private final LearningSessionRepository repository = mock(LearningSessionRepository.class);
  private final SessionQueryService service = new SessionQueryService(repository);
  private final FocusOnPrincipal principal = new FocusOnPrincipal(1, 2, 3, DeviceType.DESKTOP);

  @Test
  void malformedIdDoesNotQueryDatabase() {
    assertEquals(ApiErrorCode.SESSION_NOT_FOUND,
        assertThrows(ApiException.class, () -> service.detail(principal, "invalid")).code());
    verifyNoInteractions(repository);
  }

  @Test
  void missingAndNonOwnedSessionReturnSameError() {
    when(repository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
    assertEquals(ApiErrorCode.SESSION_NOT_FOUND,
        assertThrows(ApiException.class, () -> service.detail(principal, "9")).code());
  }

  @Test
  void invalidHistoryFiltersNeverReachDatabase() {
    for (String[] range : List.of(new String[] {"2026-11-10", "2026-11-01"},
        new String[] {"bad", "2026-11-01"})) {
      assertThrows(ApiException.class,
          () -> service.history(principal, range[0], range[1], null, null, null));
    }
    for (int limit : new int[] {0, -1, 101}) {
      assertThrows(ApiException.class,
          () -> service.history(principal, "2026-11-01", "2026-11-09", null, null, limit));
    }
    assertThrows(ApiException.class,
        () -> service.history(principal, "2026-11-01", "2026-11-09", "bad", null, 20));
    assertThrows(ApiException.class,
        () -> service.history(principal, "2026-11-01", "2026-11-09", null, "0", 20));
    verifyNoInteractions(repository);
  }

  @Test
  void extensionCannotReadDesktopHistory() {
    var extension = new FocusOnPrincipal(1, 2, 3, DeviceType.CHROME_EXTENSION);
    assertEquals(ApiErrorCode.SESSION_QUERY_FORBIDDEN,
        assertThrows(ApiException.class, () -> service.history(extension,
            "2026-11-01", "2026-11-09", null, null, null)).code());
    verifyNoInteractions(repository);
  }

  @Test
  void historyUsesExtraRowOnlyToDetermineNextCursor() {
    var first = mock(LearningSessionEntity.class);
    var second = mock(LearningSessionEntity.class);
    when(first.getId()).thenReturn(12L);
    when(repository.findHistory(anyLong(), any(), any(), anyLong(), any()))
        .thenReturn(List.of(first, second));
    var result = service.history(principal, "2026-11-01", "2026-11-09", null, null, 1);
    assertEquals(1, result.items().size());
    assertEquals("12", result.nextCursor());
  }
}
