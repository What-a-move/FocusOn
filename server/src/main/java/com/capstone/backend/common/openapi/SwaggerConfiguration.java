package com.capstone.backend.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** local 개발 프로필에서만 문서 경로를 연다. 제품 API 인증 체인과 분리한다. */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class SwaggerConfiguration {
  @Bean
  @Order(0)
  SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
            "/v3/api-docs/**", "/v3/api-docs.yaml")
        .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/**").permitAll()
            .anyRequest().denyAll())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .build();
  }

  @Bean
  OpenAPI focusOnOpenApi() {
    return new OpenAPI()
        .info(new Info().title("FocusOn 개발용 API").version("v1")
            .description("현재 구현된 API의 실행 문서입니다. 계약 정본은 FocusOn API 명세서입니다. "
                + "Authorize에는 FocusOn Access Token만 입력합니다. Google code는 PKCE 도구로 준비합니다."))
        .components(new Components().addSecuritySchemes("focusOnBearer", new SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
            .description("Bearer 접두사 없이 FocusOn accessToken 값만 입력합니다.")))
        .addSecurityItem(new SecurityRequirement().addList("focusOnBearer"));
  }
}
