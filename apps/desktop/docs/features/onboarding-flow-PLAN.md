# Desktop·Extension 온보딩 기획서

## 기본 정보

- 기능명: Desktop·Extension 온보딩 및 이어하기
- 기능 ID: onboarding-flow
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop, Chrome Extension
- 작성자: 허재원
- 작성일: 2026-10-21
- 우선순위: MVP - 핵심
- 관련 Issue: [#36 Frontend 온보딩](https://github.com/What-a-move/FocusOn/issues/36)
- 예정 Branch: `feat/36-desktop-onboarding`
- 관련 Notion·Figma: [로그인 → 학습 목표 설정](https://app.notion.com/p/3de0b6e5e64c80b6b067f5547e7dbcfd), FocusOn API 명세서의 onboarding-readiness 계약

## 기능 목적

- 사용자의 문제: 로그인 뒤 필수 프로필·기기 연결·권한·동의·개인정보 설정을 완료했는지 알기 어렵고, 앱을 다시 열면 마지막 단계부터 이어갈 수 없다.
- Desktop에서 제공할 가치: 필수 준비 단계를 순서대로 안내하고 저장된 서버 상태를 기준으로 안전하게 이어간다.
- Extension에서 제공할 가치: Desktop이 발급한 일회용 코드를 입력하고 연결 완료 여부를 확인한다.

## 범위

포함:
- Desktop 닉네임 입력·검증·저장 화면
- Desktop의 Extension 연결 코드 발급·대기·완료 상태 표시
- Extension Popup의 코드 입력·형식 검증·연결 결과 표시
- Desktop 필수 권한 확인과 필수·선택 동의 화면
- 분석 제외 사이트 입력·저장과 빈 목록 건너뛰기 화면
- 앱 재실행 시 준비 상태를 조회해 미완료 단계부터 복구
- 필수 단계 완료 후 Desktop 대시보드로 이동
- API 오류·네트워크 단절·권한 거부 상태 안내

제외:
- 학습 목표 입력, AI 명확성 확인, 시간·개입·알림 방식 설정. 이 흐름은 온보딩 완료 후 대시보드 `오늘 학습`에서 별도 구현한다.
- 분석 제외 도메인 설정의 온보딩 이후 편집 화면
- Extension의 현재 탭 자동 추적과 세션 동기화
- Backend API 구현 자체. 이 기획서와 Issue는 Frontend 범위로 유지하고 API 구현은 Backend 담당 범위로 둔다.

## 사용자 흐름

1. 로그인 완료 후 Desktop이 로컬 필수 권한을 Main에서 확인하고 `GET /api/v1/users/me/onboarding-readiness`를 조회한다.
2. 응답의 첫 미완료 `currentStep`에 맞춰 닉네임 → Extension → 필수 권한·동의 → 분석 제외 사이트 순으로 화면을 연다.
3. 닉네임 저장, Extension pairing, 필수 권한·동의 저장, 제외 도메인 저장 또는 빈 목록 건너뛰기를 완료한다.
4. Desktop은 현재 로컬 권한과 제외 단계 완료 표시를 `PUT /api/v1/users/me/onboarding-readiness`로 기록하고 상태 조회를 갱신한다.
5. `currentStep: COMPLETED`이면 대시보드로 이동한다. 목표·시간 설정은 온보딩에 추가하지 않는다.
6. 앱 종료·재실행 또는 중단 뒤에도 서버가 반환한 첫 미완료 단계에서 이어간다. 실행·복귀 때 OS 권한은 다시 확인한다.

## Desktop 상세 동작

- 진입 조건: 인증 세션이 유효하고 onboarding-readiness 조회가 성공해야 한다.
- 정상 동작: `currentStep`에 맞는 단계만 표시하고 각 저장 성공이 확인된 뒤 다음으로 이동한다.
- 종료 조건: 서버가 `currentStep: COMPLETED`를 반환하면 대시보드로 이동한다.
- 단계 순서: `NICKNAME → EXTENSION → PERMISSIONS_AND_CONSENTS → EXCLUDED_SITES → COMPLETED`.
- 권한: 화면 기록과 알림은 필수다(결정 018). 화면 기록은 Main에서 확인하고, 알림은 시험 알림 뒤 사용자 확인으로 준비됨을 정한다. 카메라 동의만 선택이며 온보딩 완료를 막지 않는다.
- 동의: 필수 동의는 항목별로 저장·조회한다. 카메라 선택 동의는 별도로 기록한다.
- 제외 사이트: 저장과 빈 목록 건너뛰기는 모두 완료로 기록한다. 사용자의 명시 행동 전에는 완료 표시를 보내지 않는다.
- 상태 변화: `AUTHENTICATED → ONBOARDING_STEP → COMPLETED → DASHBOARD`.

## 화면 및 사용자 경험

- 화면 위치: Desktop Renderer의 인증 후 온보딩 경로, Chrome Extension Popup
- 주요 컴포넌트: 단계 표시, 입력·검증 폼, 권한 설명·다시 확인·설정 열기, 동의 항목, 도메인 목록 편집, 연결 코드·만료 상태, 로딩·오류·재시도 상태
- 연결 흐름: Desktop 코드 발급·대기 → Extension 코드 입력·연결 → Desktop 연결 완료 반영
- `onboarding.html`의 Chrome 확장 프로그램 미리보기 박스는 화면 확인용 목업이므로 Desktop 온보딩에 표시하지 않는다. 실제 코드 입력 화면은 Extension Popup에 둔다.
- 권한·동의 UI는 `/onboarding/permissions`에서 **동의(1/2) → 권한 확인(2/2)** 두 내부 화면으로 나눈다(2026-10-21 시안 확정). 동의 화면은 필수 3개·`모두 동의하기`·`자세히` 상세 팝업·선택 카메라 분석이다. 권한 화면의 필수는 화면 기록과 알림이다(2026-10-23 알림 필수로 변경, 결정 018). 화면 기록은 `설정 열기` → `다시 확인` → 허용/아직 꺼짐, 알림은 `허용하기`(시험 알림) → `보였어요`/`안 보였어요` → 필요 시 `설정 열기`·`다시 보내기`. 카메라 권한 줄은 카메라 동의 시에만 보이며 선택이다.
- 분석 제외 사이트는 `/onboarding/sites`다. host만 정규화해 최대 50개, 목록이 비면 버튼이 `건너뛰기`가 되며 저장 성공 시 완료 화면(`/onboarding/done`)을 거쳐 `홈으로 가기`로 대시보드(`/`)에 들어간다. 완료 화면의 닉네임·요약은 서버가 확정한 값만 보여 주고, 불러오지 못하면 요약 없이 완료 안내만 둔다.
- 다크 모드는 macOS 설정(`prefers-color-scheme`)을 따르며 `fo-base.css` 다크 토큰과 같은 값을 쓴다.
- 서버·OS 호출은 `features/onboarding/onboarding-api.ts` → `window.focusOn`(Main IPC)을 거친다(2026-10-22 연결, `IPC_CONTRACT.md` 온보딩 Channel, 결정 017). Backend origin이 없거나 응답이 실패하면 실제 오류 상태(재시도 안내)를 보여준다. 성공을 꾸미지 않는다.
- **Backend 합의안 (2026-10-22 제안, 2026-10-24 확정):**
  1. 닉네임 중복 확인 전용 API는 만들지 않는다. `PUT /users/me/profile`의 409 `PROFILE_NICKNAME_DUPLICATE`로 판단한다(확인과 저장 사이 경합 없음).
  2. `POST /extension-pairings` 201 `data`: `accessToken`, `refreshToken`, `accessTokenExpiresAt`, `device: { deviceId, deviceType }`. 틀린 코드는 400 `LINK_006_CODE_MISMATCH` + `details.remainingAttempts`, 3번째 실패는 410 `LINK_004_ATTEMPTS_EXCEEDED`.
  3. 동의 버전은 `GET /users/me/consents`의 `currentVersion`을 그대로 저장 요청에 쓴다(없으면 `2026-09-17`).
  4. Extension 연결 완료는 코드 화면에서 3초마다 `GET onboarding-readiness`의 `extensionLinked`로 확인한다(최대 5분).
  5. `GET /users/me/consents` 응답은 `{ "data": { "currentVersion": "...", "consents": [{ consentType, isGranted, consentVersion, grantedAt, withdrawnAt }] } }`.
- 오류 상태: 중복 닉네임, 잘못된/만료/사용된 pairing code, 3회 시도 초과, 권한 거부, API 오류 및 네트워크 단절을 계약에 맞게 보여준다.
- 접근성: 키보드 이동, 입력 라벨, 오류 메시지 연결, Focus 표시, 상태의 텍스트 표현을 제공한다.

## 데이터 및 IPC/API

- Renderer에는 토큰이나 OS 원시 권한 객체를 노출하지 않는다.
- OAuth Token과 인증 API 요청은 기존 Desktop Main 경계를 따른다. 온보딩 UI는 허용된 Preload API만 호출한다.
- `GET /api/v1/users/me/onboarding-readiness` 응답의 `currentStep` 및 `steps`를 재개 기준으로 사용한다.
- 앱 시작·복귀 때 Desktop Main이 로컬 권한을 재확인하고, `PUT /api/v1/users/me/onboarding-readiness`로 `screenAnalysisReady`와 `analysisExcludedDomainsStepCompleted`만 갱신한다.
- Extension 연결 여부는 pairing 상태, 필수 동의는 consent 기록, 프로필은 저장된 nickname을 서버가 판정한다. 클라이언트가 이 상태를 임의로 완료 처리하지 않는다.
- Desktop과 Extension은 독립 token family를 사용하며 Refresh Token을 공유하지 않는다.
- API 계약에 맞는 UI 경계와 오류·재시도 상태를 구현한다. Backend API가 준비되기 전에는 응답 성공을 가짜로 만들지 않고 실제 계정 왕복은 API 준비 뒤 확인한다.

예시 응답:

```json
{
  "data": {
    "onboardingStatus": "INCOMPLETE",
    "currentStep": "PERMISSIONS_AND_CONSENTS",
    "steps": {
      "profileCompleted": true,
      "extensionLinked": true,
      "screenAnalysisReady": false,
      "requiredConsentsCompleted": false,
      "analysisExcludedDomainsStepCompleted": false
    }
  }
}
```

## Backend 의존성

아래 API 구현은 Backend 담당 범위다. 이 Frontend Issue에서는 Backend 파일이나 Backend 구현 문서를 수정하지 않는다.

- `GET /api/v1/users/me/onboarding-readiness` 구현: 단계별 완료 상태와 이어갈 `currentStep` 반환
- `PUT /api/v1/users/me/onboarding-readiness` 입력은 현재 Desktop의 필수 화면 분석 권한과 사용자의 제외 단계 완료 marker로 한정
- pairing 및 consent 원본 상태로 Extension 연결·필수 동의를 판정
- 빈 제외 사이트 목록과 명시적 건너뛰기 완료 상태를 구분
- 카메라 동의와 알림 권한은 선택 상태로 유지
- `currentStep` 계산 순서와 `COMPLETED` 전환을 API 테스트로 검증

## 완료 조건

- [ ] 닉네임 단계에서 형식 오류·중복·저장 실패를 구분한다.
- [ ] Desktop 코드 발급/대기/완료와 Extension 코드 연결의 정상·오류 상태가 계약대로 이어진다.
- [ ] 필수 권한 거부 시 안내·설정 이동·재확인을 제공한다.
- [ ] 필수 동의는 모두 확인·저장되어야 다음 단계로 간다. 카메라·알림 거부는 완료를 막지 않는다.
- [ ] 제외 도메인 저장과 빈 목록 건너뛰기 모두 재개 상태에 완료로 남고, 미방문 빈 목록은 미완료다.
- [ ] 앱 종료·재실행 후 서버가 반환한 첫 미완료 단계로 이어간다. OS 권한은 복귀 시 다시 확인한다.
- [ ] 온보딩 완료 후 대시보드로 이동하며 목표·시간·개입 설정은 대시보드 영역에 남는다.
- [ ] 정상·권한 거부·API 오류·네트워크 복귀·재실행 흐름을 실제 API 준비 뒤 검증한다.
- [ ] `CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md`와 결과 REPORT를 갱신한다.
