# 개발 오류 보고서 — 모든 목표 세분화 (#314)

## 1. 목 다시 설정 시 기존 응답 함수가 null 인자로 불림

- 증상: 테스트에서 `when(ai.profile(any()))`로 응답을 바꾸자 앞서 설정한 응답 함수가 null 인자로 실행돼 NPE가 났다.
- 원인: `when(...)` 형식은 설정 중에 실제 메서드를 한 번 호출한다.
- 조치: 테스트 안의 재설정은 `doAnswer`/`doThrow` 형식으로 바꿨다. 제품 코드 문제 아님.

## 2. 세분화 AI 실패가 로그 없이 묻히고, 본문 지연이 요청을 오래 붙잡음

- 발견: PR 전 결함 점검(2026-11-23).
- 증상: 세분화 실패를 예외로 잡아 null로 바꾸면서 로그를 남기지 않았다(잡은 예외 WARN 규칙 위반). AI로 traceId도 넘기지 않았다. AI가 헤더만 보내고 본문을 멈추면 요청 timeout(헤더까지만 적용)이 듣지 않아 `POST /goals`가 계속 기다렸다.
- 조치: `SafeApiLog.goalProfileEvent`로 `ai.call.start`/`ok`/`fail`(component `goal-profile`, 고정 오류 코드만)을 남긴다. Goal AI 요청에 `X-Trace-Id`를 붙인다. 요청 전체에 `timeout + 1초` 마감을 두고 넘기면 취소 후 TIMEOUT으로 처리한다(다듬기 요청에도 같이 적용).
- 검증: `GoalAiClientTests.profileUsesItsOwnInternalPath`(traceId 전달), `stalledProfileBodyEndsWithinDeadline`(약 9초에 TIMEOUT).
