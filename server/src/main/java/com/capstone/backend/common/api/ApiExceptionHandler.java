package com.capstone.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Controller에서 발생한 오류를 FocusOn 공통 응답 형식으로 변환한다. */
@RestControllerAdvice(basePackages = "com.capstone.backend")
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** 도메인에서 지정한 오류와 429 대기 시간을 공통 형식으로 반환한다. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(
      ApiException exception, HttpServletRequest request) {
    String requestId = RequestIdFilter.from(request);
    ApiErrorCode code = exception.code();
    ApiErrorResponse body = ApiErrorResponse.of(
        code, exception.retryAfterSeconds(), requestId, exception.details());
    ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
    if (exception.retryAfterSeconds() != null) {
      response.header(HttpHeaders.RETRY_AFTER, exception.retryAfterSeconds().toString());
    }
    return response.body(body);
  }

  /** DTO·JSON·필수 헤더 등 요청 입력 오류를 400으로 변환한다. */
  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HandlerMethodValidationException.class,
      HttpMessageNotReadableException.class,
      ServletRequestBindingException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleValidation(
      Exception exception, HttpServletRequest request) {
    // 원문 입력이나 프레임워크 예외 메시지는 details와 로그에 포함하지 않는다.
    return error(ApiErrorCode.VALIDATION_ERROR, RequestIdFilter.from(request));
  }

  /** 예상 밖의 오류에서 내부 원문을 숨기고 안전한 500 응답을 반환한다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    String requestId = RequestIdFilter.from(request);
    // 예외 메시지와 stack trace에는 요청 본문·토큰이 포함될 수 있어 기록하지 않는다.
    LOGGER.error("Unhandled API failure requestId={} code={} status=500",
        requestId, ApiErrorCode.INTERNAL_ERROR.name());
    return error(ApiErrorCode.INTERNAL_ERROR, requestId);
  }

  private static ResponseEntity<ApiErrorResponse> error(ApiErrorCode code, String requestId) {
    return ResponseEntity.status(code.status())
        .body(ApiErrorResponse.of(code, null, requestId, Map.of()));
  }
}
