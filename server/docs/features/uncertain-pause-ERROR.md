# 개발 오류 보고서 — 판단 보류 구간 멈춤 (#296)

## 2026-11-21 기존 테스트의 "모르는 정책" 예시가 v2였음

- 증상: `OffTaskTimelineTests.unknownPolicyAndInterruptedContextCannotGenerate`가 실패했다.
- 원인: 알 수 없는 정책 버전의 예시로 `v2`를 쓰고 있었는데, 이번 작업으로 `v2`가 실제 정책이 됐다.
- 조치: 예시를 `v3`로 바꿨다. 테스트 의도(v1·v2 외 버전은 후보를 만들지 않음)는 그대로다.

## 2026-11-21 MissingEndpointHttpTests 일시 실패

- 증상: 전체 실행 중 6개가 `HTTP/1.1 header parser received no bytes`로 실패했다.
- 원인: 실제 포트로 요청하는 테스트의 연결이 비어 끊긴 실행 환경 문제로 판단했다. 이번 변경과 관련 없는 코드다.
- 조치: 단독 재실행과 `--rerun-tasks` 전체 재실행 모두 통과했다. 재발하면 별도 Issue로 본다.
