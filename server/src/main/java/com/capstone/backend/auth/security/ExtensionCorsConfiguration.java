package com.capstone.backend.auth.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 설정한 Chrome 확장 origin만 제품 API 응답에 접근하도록 허용한다. */
@Configuration
public class ExtensionCorsConfiguration {
  /** wildcard 및 비확장 origin 설정은 시작 시 거절한다. 빈 설정은 전체 불허다. */
  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${focuson.cors.allowed-origins:}") String configured) {
    List<String> origins = Arrays.stream(configured.split(","))
        .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    if (origins.stream().anyMatch(value -> !value.matches("chrome-extension://[a-p]{32}"))) {
      throw new IllegalArgumentException("CORS origins must be explicit Chrome extension origins");
    }
    var config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
        "If-Match-Version", "X-Device-Id", "X-Trace-Id"));
    config.setExposedHeaders(List.of("Retry-After"));
    config.setAllowCredentials(false);
    config.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/**", config);
    return source;
  }
}
