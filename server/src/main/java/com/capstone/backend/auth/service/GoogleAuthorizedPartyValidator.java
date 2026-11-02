package com.capstone.backend.auth.service;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Google ID token의 authorized presenter를 Desktop Client ID로 제한한다.
 * FocusOn Access JWT나 DB 인증에는 사용하지 않는 Google 전용 검증기다.
 */
final class GoogleAuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {
  private final String desktopClientId;

  GoogleAuthorizedPartyValidator(String desktopClientId) {
    this.desktopClientId = desktopClientId;
  }

  /** 단일 aud는 azp 생략을 허용하고, 다중 aud는 일치하는 azp를 요구한다. */
  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    var audiences = token.getAudience();
    if (!token.hasClaim("azp") && audiences != null && audiences.size() == 1) {
      return OAuth2TokenValidatorResult.success();
    }
    // 숫자·배열이 문자열로 변환되어 통과하지 않도록 원본 타입을 검사한다.
    Object authorizedParty = token.getClaims().get("azp");
    if (authorizedParty instanceof String value && !desktopClientId.isBlank()
        && desktopClientId.equals(value)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }
}
