# 공통 API 기반 개발 중 오류 보고서

- 관련 Issue: [#30](https://github.com/What-a-move/FocusOn/issues/30)
- 발생일: 2026-10-19
- 상태: 해결
- 심각도: 낮음

## 현상

첫 `./gradlew compileJava checkstyleMain --no-daemon` 실행에서 새 공개 Java 타입과 메서드의 Javadoc 누락으로 Checkstyle이 실패했다. API 요청이나 DB는 아직 없으므로 실행 중 응답 오류는 아니었다.

## 원인과 조치

공개 타입·메서드에 프로젝트 Checkstyle 규칙이 요구하는 설명이 빠져 있었다. 누락된 Javadoc을 추가하고 Servlet 요청 바인딩 예외를 공통 입력 오류 처리 범위에 포함했다. 마지막 수정에서 429 예외 생성 조건도 생성자 내부로 옮겨 Java 생성자 호출 순서를 지켰다.

## 확인

- `./gradlew compileJava checkstyleMain --no-daemon`: 성공
- HTTP 동작과 DB 동작: 이번 작업에서 실행 검증하지 않음

## 재발 방지

공개 API의 Javadoc과 `Retry-After` 양수 조건을 코드 작성 시 함께 확인한다.
