package com.capstone.backend.user.service;

import com.capstone.backend.auth.persistence.UserEntity;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.user.dto.UserConfigurationDtos.ConsentResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ConsentsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ExcludedDomainsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.FocusSettingsResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.MeResponse;
import com.capstone.backend.user.dto.UserConfigurationDtos.ReplaceExcludedDomainsRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateConsentRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateFocusSettingsRequest;
import com.capstone.backend.user.dto.UserConfigurationDtos.UpdateProfileRequest;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainEntity;
import com.capstone.backend.user.persistence.AnalysisExcludedDomainRepository;
import com.capstone.backend.user.persistence.UserConsentEntity;
import com.capstone.backend.user.persistence.UserConsentRepository;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity;
import com.capstone.backend.user.persistence.UserFocusSettingsRepository;
import java.net.IDN;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 principal의 사용자 설정만 조회·변경한다. */
@Service
@Transactional(readOnly = true)
public class UserConfigurationService {
  private static final String CURRENT_CONSENT_VERSION = "2026-09-17";
  private final UserRepository users;
  private final UserConsentRepository consents;
  private final UserFocusSettingsRepository focusSettings;
  private final AnalysisExcludedDomainRepository excludedDomains;
  private final Clock clock;

  /** 사용자 설정 저장소를 연결한다. */
  @Autowired
  public UserConfigurationService(UserRepository users, UserConsentRepository consents,
      UserFocusSettingsRepository focusSettings, AnalysisExcludedDomainRepository excludedDomains) {
    this(users, consents, focusSettings, excludedDomains, Clock.systemUTC());
  }

  UserConfigurationService(UserRepository users, UserConsentRepository consents,
      UserFocusSettingsRepository focusSettings, AnalysisExcludedDomainRepository excludedDomains,
      Clock clock) {
    this.users = users;
    this.consents = consents;
    this.focusSettings = focusSettings;
    this.excludedDomains = excludedDomains;
    this.clock = clock;
  }

  /** 현재 인증 사용자의 최소 공개 정보를 반환한다. */
  public MeResponse me(FocusOnPrincipal principal) {
    UserEntity user = currentUser(principal);
    return meResponse(user);
  }

  /** 현재 사용자 닉네임을 변경한다. */
  @Transactional
  public MeResponse updateProfile(FocusOnPrincipal principal, UpdateProfileRequest request) {
    UserEntity user = currentUser(principal);
    String nickname = request.nickname().trim();
    if (nickname.isBlank()) {
      throw new ApiException(ApiErrorCode.PROFILE_NICKNAME_INVALID);
    }
    boolean duplicate = users.findByNickname(nickname)
        .filter(other -> !other.getId().equals(user.getId())).isPresent();
    if (duplicate) {
      throw new ApiException(ApiErrorCode.PROFILE_NICKNAME_DUPLICATE);
    }
    user.setNickname(nickname);
    return meResponse(user);
  }

  /** 현재 사용자의 동의 상태를 반환한다. */
  public ConsentsResponse consents(FocusOnPrincipal principal) {
    List<ConsentResponse> responses = consents.findAllByUserIdOrderByConsentType(principal.userId())
        .stream().map(item -> new ConsentResponse(item.getConsentType(), item.isGranted(),
            item.getConsentVersion(), item.getGrantedAt(), item.getWithdrawnAt())).toList();
    return new ConsentsResponse(CURRENT_CONSENT_VERSION, responses);
  }

  /** 현재 사용자 동의 한 항목을 현재 문서 버전으로 저장한다. */
  @Transactional
  public ConsentResponse updateConsent(FocusOnPrincipal principal,
      UserConsentEntity.ConsentType type,
      UpdateConsentRequest request) {
    if (!CURRENT_CONSENT_VERSION.equals(request.consentVersion())) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    UserEntity user = currentUser(principal);
    Instant now = clock.instant();
    UserConsentEntity consent = consents.findByUserIdAndConsentType(user.getId(), type)
        .orElseGet(() -> UserConsentEntity.create(user, type, request.isGranted(),
            request.consentVersion(), now));
    if (consent.getId() != null) {
      consent.apply(request.isGranted(), request.consentVersion(), now);
    }
    UserConsentEntity saved = consents.save(consent);
    return new ConsentResponse(saved.getConsentType(), saved.isGranted(), saved.getConsentVersion(),
        saved.getGrantedAt(), saved.getWithdrawnAt());
  }

  /** 현재 사용자 집중 설정을 반환하며 없으면 명세 기본값을 사용한다. */
  public FocusSettingsResponse focusSettings(FocusOnPrincipal principal) {
    return focusSettings.findById(principal.userId()).map(this::toFocusResponse)
        .orElseGet(() -> new FocusSettingsResponse(UserFocusSettingsEntity.InterventionLevel.NORMAL,
            List.of("MOLE_UI"), true, false));
  }

  /** 현재 사용자의 집중 설정 전체를 저장한다. */
  @Transactional
  public FocusSettingsResponse updateFocusSettings(FocusOnPrincipal principal,
      UpdateFocusSettingsRequest request) {
    List<String> channels = normalizedChannels(request.notificationChannels());
    UserEntity user = currentUser(principal);
    String serialized = String.join(",", channels);
    Instant now = clock.instant();
    UserFocusSettingsEntity settings = focusSettings.findById(user.getId()).orElseGet(() ->
        UserFocusSettingsEntity.create(user.getId(), request.interventionLevel(), serialized,
            request.breakSuggestionEnabled(), request.cameraAnalysisEnabled(), now));
    if (settings.getUserId() != null) {
      settings.apply(request.interventionLevel(), serialized, request.breakSuggestionEnabled(),
          request.cameraAnalysisEnabled(), now);
    }
    return toFocusResponse(focusSettings.save(settings));
  }

  /** 현재 사용자의 분석 제외 host를 반환한다. */
  public ExcludedDomainsResponse excludedDomains(FocusOnPrincipal principal) {
    return new ExcludedDomainsResponse(excludedDomains
        .findAllByUserIdOrderByHost(principal.userId())
        .stream().map(AnalysisExcludedDomainEntity::getHost).toList());
  }

  /** 현재 사용자의 분석 제외 host 전체를 원자적으로 교체한다. */
  @Transactional
  public ExcludedDomainsResponse replaceExcludedDomains(FocusOnPrincipal principal,
      ReplaceExcludedDomainsRequest request) {
    Set<String> hosts = new LinkedHashSet<>();
    for (String domain : request.domains()) {
      hosts.add(normalizeHost(domain));
    }
    if (hosts.size() > 50) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    UserEntity user = users.findByIdForUpdate(principal.userId()).orElseThrow(() ->
        new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
    excludedDomains.deleteByUserId(user.getId());
    excludedDomains.flush();
    Instant now = clock.instant();
    excludedDomains.saveAll(hosts.stream()
        .map(host -> AnalysisExcludedDomainEntity.create(user, host, now)).toList());
    return new ExcludedDomainsResponse(hosts.stream().sorted().toList());
  }

  private UserEntity currentUser(FocusOnPrincipal principal) {
    return users.findById(principal.userId()).orElseThrow(() ->
        new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED));
  }

  private static MeResponse meResponse(UserEntity user) {
    return new MeResponse(Long.toString(user.getId()), user.getNickname(),
        user.getOnboardingStatus());
  }

  private FocusSettingsResponse toFocusResponse(UserFocusSettingsEntity settings) {
    return new FocusSettingsResponse(settings.getInterventionLevel(),
        List.of(settings.getNotificationChannels().split(",")), settings.isBreakSuggestionEnabled(),
        settings.isCameraAnalysisEnabled());
  }

  private static List<String> normalizedChannels(List<String> channels) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String channel : channels) {
      String value = channel.trim();
      if (!Set.of("MOLE_UI", "MACOS_NOTIFICATION", "SOUND").contains(value)) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
      if (!normalized.add(value)) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
    }
    return List.copyOf(normalized);
  }

  private static String normalizeHost(String input) {
    String value = input.trim().toLowerCase(Locale.ROOT);
    if (value.endsWith(".")) {
      value = value.substring(0, value.length() - 1);
    }
    if (value.contains(":") || value.contains("/") || value.contains("?") || value.contains("#")) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
    try {
      String host = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      boolean invalidHost = host.isBlank() || host.length() > 253
          || !host.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
              + "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*");
      if (invalidHost) {
        throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
      }
      return host;
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
    }
  }
}
