package com.capstone.backend.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;
import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateProfileRequest;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainRepository;
import com.capstone.backend.user.persistence.UserConsentRepository;
import com.capstone.backend.user.persistence.UserFocusSettingsRepository;
import com.capstone.backend.user.service.UserConfigurationService;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 닉네임 저장 중 발생한 DB 오류의 변환 범위를 검증한다. */
class UserConfigurationServiceTests {
  private final UserRepository users = mock(UserRepository.class);
  private final UserConfigurationService service = new UserConfigurationService(users,
      mock(UserConsentRepository.class), mock(UserFocusSettingsRepository.class),
      mock(AnalysisExcludedDomainRepository.class));
  private final FocusOnPrincipal principal = new FocusOnPrincipal(1L, 2L, 3L, DeviceType.DESKTOP);

  @Test
  void translatesOnlyNicknameConstraintViolation() {
    UserEntity user = mock(UserEntity.class);
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(users.findByNickname("nickname")).thenReturn(Optional.empty());
    DataIntegrityViolationException failure = new DataIntegrityViolationException("constraint",
        new ConstraintViolationException("constraint", new SQLException(), "uq_users_nickname"));
    when(users.saveAndFlush(any())).thenThrow(failure);

    ApiException exception = assertThrows(ApiException.class,
        () -> service.updateProfile(principal, new UpdateProfileRequest("nickname")));
    assertEquals(ApiErrorCode.PROFILE_NICKNAME_DUPLICATE, exception.code());
  }

  @Test
  void preservesUnrelatedIntegrityViolation() {
    UserEntity user = mock(UserEntity.class);
    when(users.findById(1L)).thenReturn(Optional.of(user));
    when(users.findByNickname("nickname")).thenReturn(Optional.empty());
    DataIntegrityViolationException failure = new DataIntegrityViolationException("other",
        new ConstraintViolationException("other", new SQLException(), "other_constraint"));
    when(users.saveAndFlush(any())).thenThrow(failure);

    assertSame(failure, assertThrows(DataIntegrityViolationException.class,
        () -> service.updateProfile(principal, new UpdateProfileRequest("nickname"))));
  }
}
