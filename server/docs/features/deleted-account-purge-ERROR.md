# 개발 오류 보고서 — 삭제 계정 학습 기록 정리 (#299)

## 2026-11-21 H2 테스트 스키마의 기본값 없음

- 증상: 테스트에서 `learning_summaries`에 행을 넣을 때 `attempt_count`에 NULL이 들어가 실패했다.
- 원인: H2 테스트 스키마는 엔티티로 만들어져 마이그레이션의 DEFAULT가 없다.
- 조치: 테스트 삽입문에 `key_concepts`, `attempt_count`를 명시했다. 운영 코드 변경 없음.
