# Extension 연결 해제 API 기획

- 상태: 사용자 후속 초안 승인. Notion 권한·204 응답 확인. 상세 기기 조회 복구 경로 검토 필요.
- 우선순위: MVP 핵심. 관련 #86, #58, #36.
- `DELETE /api/v1/extension-pairings/{extensionDeviceId}`: 인증된 기존 소유 계정의 Desktop 또는 해당 Extension에서 호출, 성공 204 본문 없음. 정확한 권한은 정본 확인 후 결정한다.
- 대상 기기 소유권과 기기 종류를 검증하고, 가족 및 refresh 폐기와 기기 상태 변경을 하나의 transaction으로 처리한다. 사용자 및 family 잠금 순서는 기존 인증 구현과 통일한다.
- 재연결, refresh, logout 동시 처리·반복 호출·타 계정 접근·readiness 갱신을 검증한다.
- 토큰이 없는 경우 기존 Google 계정 인증 후 본인 기기를 찾아 해제할 수 있는지 기기 조회 및 삭제 구현과 Frontend 상태를 확인한다. 두 인증 수단 모두 없으면 소유권 증명 없는 강제 해제를 허용하지 않는다.
- 프론트 화면은 수정하지 않는다. 독립 리뷰·빌드·QA 및 연동 조건을 기록한다.
