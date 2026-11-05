package com.capstone.backend.auth.security;

import com.capstone.backend.auth.service.AuthRateLimiter;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiErrorResponse;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.common.api.SafeApiLog;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 인증 API는 세션 쿠키 대신 Bearer JWT와 DB 상태로 인증한다. */
@Configuration
public class ApiSecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
      RequestAuthenticationService authentication, AuthRateLimiter limits, ObjectMapper json)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/google/exchange",
                "/api/v1/auth/refresh", "/api/v1/extension-pairings").permitAll()
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> writeError(
                request, response, json, new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED)))
            .accessDeniedHandler((request, response, exception) -> writeError(
                request, response, json, new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED))))
        .addFilterBefore(new AuthenticationFilter(authentication, limits, json),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static void writeError(HttpServletRequest request, HttpServletResponse response,
      ObjectMapper json, ApiException exception) throws IOException {
    request.setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, exception.code());
    request.setAttribute(RequestIdFilter.ERROR_STAGE_ATTRIBUTE, SafeApiLog.Stage.SECURITY);
    response.setStatus(exception.code().status().value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    if (exception.retryAfterSeconds() != null) {
      response.setHeader("Retry-After", exception.retryAfterSeconds().toString());
    }
    json.writeValue(response.getOutputStream(), ApiErrorResponse.of(exception.code(),
        exception.retryAfterSeconds(), RequestIdFilter.from(request), exception.details()));
  }

  // Security chain 안에만 등록해 Servlet 자동 등록으로 두 번 실행되지 않게 한다.
  private static final class AuthenticationFilter extends OncePerRequestFilter {
    private final RequestAuthenticationService authentication;
    private final AuthRateLimiter limits;
    private final ObjectMapper json;

    private AuthenticationFilter(RequestAuthenticationService authentication,
        AuthRateLimiter limits, ObjectMapper json) {
      this.authentication = authentication;
      this.limits = limits;
      this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      return !path.startsWith("/api/v1/")
          || ("POST".equals(request.getMethod())
              && "/api/v1/auth/google/exchange".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
      try {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && "/api/v1/auth/refresh".equals(path)) {
          // JSON 변환·@Valid보다 먼저 집계한다. refresh의 Bearer는 인증 수단으로 쓰지 않는다.
          limits.checkRefreshIp(request.getRemoteAddr());
        } else if ("POST".equals(request.getMethod())
            && "/api/v1/extension-pairings".equals(path)) {
          limits.checkPairingIp(request.getRemoteAddr());
        } else {
          authenticateBearer(request);
        }
      } catch (ApiException exception) {
        SecurityContextHolder.clearContext();
        writeError(request, response, json, exception);
        return;
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
        writeError(request, response, json, new ApiException(ApiErrorCode.SERVICE_UNAVAILABLE));
        return;
      }
      try {
        chain.doFilter(request, response);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    /** 일반 인증 API만 Bearer·소유권·기기·사용자별 한도를 검사한다. */
    private void authenticateBearer(HttpServletRequest request) {
      String header = request.getHeader("Authorization");
      if (header == null || !header.startsWith("Bearer ") || header.length() > 8192
          || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) {
        throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
      }
      FocusOnPrincipal principal = authentication.authenticate(header.substring(7));
      if (request.getHeader("X-Device-Id") != null) {
        DeviceHeaderValidator.require(request.getHeader("X-Device-Id"), principal);
      }
      limits.check("api:" + principal.userId() + ":" + principal.deviceId(),
          60, Duration.ofMinutes(1));
      var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
    }
  }
}
