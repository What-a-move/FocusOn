# Desktop 인증 경쟁 수정 독립 리뷰

- 2026-10-27 / Issue #74 / 비교 기준 origin/dev65278a9
- 구현 대화를 전달하지 않은 별도 리뷰자가 diff·기존 로그인 PLAN·계약·컨벤션·회귀를 검토했다.
- 범위: terminal오류, OAuth/vault 순서, restore/refresh중복, logout/refresh순서, 계정 전환·이전 요청, Keychain실패.

## 지적과 처리

1. P2 — 자동 refresh의 Keychain write 실패 시 forget만 수행하고 UI가SIGNED_IN인 회귀.
   - 현재 인증의 read/write 실패를 OFFLINE으로 표시하고 재연결/재로그인 경로를 유지하도록 수정했다.
   - read복구는SIGNED_IN, 이미회전된token의write실패 뒤옛token재사용은TOKEN_REUSE_DETECTED/SIGNED_OUT/저장소삭제로 실제서버정책에 맞춘 회귀를 추가했다.
2. 복구 첫vault읽기가 큐 밖에 있던 추가 경쟁도 확인했다.
   - 읽기부터 공유 refresh작업 안으로 이동했고 지연된null읽기의 새 로그인훼손 회귀를 추가했다.
3. terminal 오류3종의 초기/재시도 경계를 매트릭스로 보강했다.

## 재검토 결과

구현 지적 해결, 남은 actionable 구현 결함 없음. 별도 리뷰자의 auth관련44개 테스트 통과를 확인했고, 이후 테스트의write실패 복구 기대를 strictrotation에 맞춰 수정했다. 로컬 전체130개 재검증은 QA문서에 기록한다.

독립 리뷰는 mock API/vault와 소스 기준이다. 실제 Google·Keychain·서버왕복을 리뷰자가 검증했다고 표시하지 않는다. 구현 작업자의 실제HTTP/PG/Keychain 검증은 QA문서에서 별도 관리한다.
