# 온보딩 준비 상태 API 기획

- 상태: 사용자 후속 초안 승인. Notion 요청·응답과 Desktop별 저장 범위를 확인했다.
- 우선순위: MVP 핵심. 관련 #85, #38, #58, #36.
- Backend 범위: `GET`/`PUT /api/v1/users/me/onboarding-readiness`; 인증된 Desktop 소유권 확인.
- GET: `data.onboardingStatus`, `currentStep`, `steps`의 `profileCompleted`, `extensionLinked`, `screenAnalysisReady`, `requiredConsentsCompleted`, `analysisExcludedDomainsStepCompleted`.
- PUT: 선택적 `screenAnalysisReady`, `analysisExcludedDomainsStepCompleted`; 생략 필드는 기존 값 유지.
- 닉네임, Extension, 필수 동의는 서버 상태로 계산한다. 빈 제외 목록을 단계 미완료로 추정하지 않는다. 두 marker는 Notion 정본에 따라 현재 Desktop별로 저장한다.
- 알림 권한은 서버에 저장하지 않는다. 미인증 및 타 기기는 거부한다.
- 모델과 마이그레이션은 최신 dev와 정본 확인 후 결정. 민감 화면 내용·토큰을 저장하지 않는다.
- 검증: 사용자/기기 격리, 최초·재진입, 빈 목록, 동의 철회, 연결·해제, 동시 변경, Desktop 응답 계약과 PostgreSQL migration.
- Client 화면은 수정하지 않는다. 독립 리뷰·빌드·QA 및 프론트 연동 조건을 기록한다.
