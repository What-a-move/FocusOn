package com.capstone.backend.common.api;

/** 본문이 있는 성공 응답을 정본 형식인 {@code {"data": ...}}로 감싼다. */
public record ApiResponse<T>(T data) {
  /** 응답 본문에 담을 데이터를 감싼다. */
  public static <T> ApiResponse<T> of(T data) {
    return new ApiResponse<>(data);
  }
}
