package com.capstone.backend.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.report.service.SessionSummaryService;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.session.persistence.SessionExtensionRepository;
import org.junit.jupiter.api.Test;

/** Extension은 Desktop의 결과 요약을 읽기 전에 거절한다. */
class SessionSummaryServiceTests {
  @Test
  void extensionIsRejectedBeforeReadingData() {
    var sessions = mock(LearningSessionRepository.class);
    var extensions = mock(SessionExtensionRepository.class);
    var service = new SessionSummaryService(sessions, extensions);
    var principal = new FocusOnPrincipal(1, 2, 3, DeviceType.CHROME_EXTENSION);
    assertEquals(ApiErrorCode.SESSION_QUERY_FORBIDDEN,
        assertThrows(ApiException.class, () -> service.get(principal, "1")).code());
    verifyNoInteractions(sessions, extensions);
  }
}
