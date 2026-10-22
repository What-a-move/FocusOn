# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Desktop 온보딩 단계 구현과 대시보드 진입
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop (Extension pairing UI 연동 포함)
- 우선순위: 높음
- 상태: 온보딩 4단계 UI(닉네임 → Extension 연결 → 권한·동의(동의 → 권한 확인) → 제외 사이트 `/onboarding/sites` → 완료 `/onboarding/done`)와 다크 모드를 `FocusOn-design/onboarding.html` 시안대로 구현했다. 서버·OS 호출은 `onboarding-api.ts` 연결 지점만 있고 모두 `SERVICE_UNAVAILABLE`이다. #22(PR #39) 병합 뒤 Main IPC를 연결하고, Backend API 구현 뒤 실제 계정 QA를 진행한다.

## 작업 목적

로그인 뒤 닉네임, Extension 연결, 필수 권한·동의, 분석 제외 사이트 설정을 구현하고 모든 필수 단계를 완료하면 대시보드로 이동한다. 목표·시간·개입은 온보딩에서 설정하지 않고 대시보드의 `오늘 학습` 흐름으로 분리한다.

## 선행 조건

- 인증 기반과 로그인 화면이 `dev`에 반영되어 있어야 한다 (Issue #22).
- `ELECTRON_ARCHITECTURE.md`, `IPC_CONTRACT.md`, `WINDOW_LIFECYCLE.md`의 확정 기준을 따라야 한다.
- 기획서 [`onboarding-flow-PLAN.md`](./features/onboarding-flow-PLAN.md)와 Frontend [Issue #36](https://github.com/What-a-move/FocusOn/issues/36)를 확인한다. Backend API 구현은 담당 Backend 작업에서 진행한다.
- 닉네임·pairing·consent·analysis-excluded-domains API 정본을 따른다.
- 앱 시작·복귀 때 Desktop Main이 로컬 필수 권한을 다시 확인한다. 서버 응답만으로 OS 권한 허용을 가정하지 않는다.
- 사용자가 제외 도메인을 저장하거나 빈 목록 건너뛰기를 명시적으로 선택한 뒤에만 완료 marker를 기록한다.
- 서버 API 구현 전에는 저장·온보딩 완료 성공을 임의로 처리하지 않는다.

## 예상 작업 순서

1. 로그인 뒤 `GET /api/v1/users/me/onboarding-readiness`로 `currentStep`을 조회해 중단 지점부터 여는 흐름을 연결한다.
2. 완료: 공통 온보딩 틀과 닉네임 입력 UI, 클라이언트 형식 검증을 구현했다. 닉네임 저장·중복 검증은 API 연결 뒤 이어서 구현한다.
3. Desktop 연결 안내 및 코드 자리·대기·완료·오류 표시 UI를 구현했다. 실제 코드 발급과 상태 확인 API 연결은 후속 작업이다.
4. 완료: 권한·동의 UI(동의 → 권한 확인)를 구현했다. 남은 일은 Main/Preload의 macOS 화면 기록 확인·시스템 설정 열기·카메라/알림 요청 연결과 필수 동의 API 연동이다. 이후 Main/Preload에서 macOS 화면 기록 권한 확인을 연결하고 필수 동의 API를 연동한다. 카메라 동의와 알림은 선택으로 두고 거부해도 온보딩을 막지 않는다.
5. 화면 완료: 제외 사이트 등록·빈 목록 건너뛰기 UI(`/onboarding/sites`). 남은 일은 저장 API와 완료 marker 기록 연결이다.
6. 서버가 `COMPLETED`를 반환하면 대시보드로 이동한다. 목표·시간·개입은 후속 대시보드 Issue에 남긴다.
7. 백엔드 API 병합 뒤 신규 계정, 앱 재실행, 권한 변경, 네트워크 오류, Extension 연결을 실제 계정으로 검증한다.

## 이후 작업 후보

- 학습 세션 타이머
- 화면 부유형 두더지 마스코트 피드백 및 표시 모드 설정
- 일회용 연결 코드 기반 Chrome Extension pairing
- Desktop·Extension 학습 세션 상태 동기화
- macOS 활성 앱과 Bundle Identifier 확인
- 분석 제외 앱 설정
- 화면 캡처 권한과 캡처 중지
- MediaPipe 카메라 권한 및 상태 연결

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] Electron 영역별 책임을 문서화했다.
- [ ] 정상 실행과 종료를 확인했다.
- [ ] 권한 거부 상황을 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `ELECTRON_ARCHITECTURE.md`를 확인했다.
- [ ] `IPC_CONTRACT.md`에서 이번 기능에 필요한 Channel과 Payload를 확인했다.
- [ ] `MACOS_PERMISSION_FLOW.md`와 `WINDOW_LIFECYCLE.md`의 확정 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 관련 Issue와 Branch가 있다.
