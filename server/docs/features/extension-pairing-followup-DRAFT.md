# 온보딩 연동 후속 Backend 이슈 초안

> 과거 검토 초안이다. 현재 상태는 문서 마지막 절을 확인한다.

## 초안 1: 온보딩 준비 상태 API

- 목적: Desktop이 저장된 닉네임·현재 Desktop에 연결된 Extension·필수 동의·권한 및 제외 단계 완료를 서버에서 조회하고 이어갈 단계로 이동한다.
- API: `GET /api/v1/users/me/onboarding-readiness`, `PUT /api/v1/users/me/onboarding-readiness`.
- GET 응답: `data.onboardingStatus`, `currentStep`과 `steps.{profileCompleted,extensionLinked,screenAnalysisReady,requiredConsentsCompleted,analysisExcludedDomainsStepCompleted}`. 첫 미완료 단계의 enum은 NICKNAME / EXTENSION / PERMISSIONS_AND_CONSENTS / EXCLUDED_SITES / COMPLETED.
- PUT은 `screenAnalysisReady`와 `analysisExcludedDomainsStepCompleted`를 선택적으로 받는다. 생략된 값은 기존 값을 유지한다. 호출 기기의 소유권·Desktop 종류는 서버에서 검증한다.
- Extension 연결 및 필수 동의는 클라이언트의 임의 완료 값이 아니라 서버 저장 상태에서 계산한다. CAMERA_ANALYSIS는 선택이다. 제외 목록이 비어 있는 것만으로 단계를 끝냈다고 추측하지 않는다.
- 저장 범위: 화면 기록 준비 상태의 Desktop별 저장, 제외 단계 완료 marker와 현재 온보딩 상태의 소유 범위·재계산 시점은 Notion 및 기존 users/user_devices 설계를 대조한 뒤 확정한다. Flyway 버전은 협업자와 충돌 해결 후 결정한다.
- 검증: 사용자/기기 격리, 최초/재접속, 제외 목록 빈 값 건너뛰기, 필수 동의 취소, Extension 연결·해제 반영, concurrent update, 응답 형식과 현재 단계, 3초 조회 한도.
- 완료 조건: Notion GET/PUT·ERD와 일치, Frontend 요청과 응답 통합 검증, 백엔드 테스트·독립 리뷰·QA. Google 실계정/앱 UI QA는 별도 결과로 기록한다.

## 초안 2: Extension 연결 해제 API

- 목적: 현재 계정의 Extension 연결과 해당 Extension의 token family를 폐기해 사용자가 같은 설치를 다른 계정에 연결할 수 있다.
- API: `DELETE /api/v1/extension-pairings/{extensionDeviceId}`, 성공 204 body 없음. Frontend는 자신의 `deviceId`를 경로로 전달한다.
- Desktop 또는 해당 Extension이 호출할 때의 구체적인 기기 소유권·반복 요청·이미 종료된 세션 처리·오류 코드는 Notion 정본 및 인증 정책을 먼저 확인한다. 타 사용자 정보 노출 없이 처리한다.
- 사용자·기기·family 잠금 순서, 현재 토큰과 refresh 폐기, 동시 연결/갱신/로그아웃 경쟁, readiness 반영을 기존 인증 구현과 함께 검토한다.
- 검증: 본인 해제·타 계정 거절, 기존 토큰·refresh 거부, 다른 Desktop/Extension 영향 없음, 반복 요청, 연결·refresh 동시 요청, 204 형식.
- 완료 조건: 정본 권한·오류 계약 확인, 자체 Backend 테스트·독립 리뷰·QA, Frontend 실제 연동 QA.

## 현재 상태

- #86 브랜치에 Extension 연결 해제와 기기 목록이 구현됐으며 PR #91에서 검토 중이다. #85 브랜치에 readiness가 구현됐으며 PR #92에서 검토 중이다. 둘 다 아직 dev 미병합이며 실제 앱 통합 QA가 남았다.
- 동의 출처는 V4, 연결은 V5이며 #85 readiness는 V6을 사용한다.
- 이 초안은 승인·이슈 생성 이전 기록이다. 최신 검증과 남은 단계는 각 PLAN·QA 및 PR을 참고한다.
