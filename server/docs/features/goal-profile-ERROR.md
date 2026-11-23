# 개발 오류 보고서 — 목표 세분화 저장 (#298)

## 2026-11-21 테스트 컴파일·Checkstyle

- 증상: 테스트가 `ConfirmationSource`를 `GoalDtos`에서 찾다 컴파일에 실패했고, 이어 import 순서로 Checkstyle이 실패했다.
- 조치: `GoalEntity.ConfirmationSource`로 고치고 import 순서를 맞췄다.
