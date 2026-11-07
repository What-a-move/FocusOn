# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: `onboarding-flow-screen-permission-stuck`
- 기능명: 온보딩 권한 확인(화면 기록)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-07
- 작성자: jae1
- 심각도: 높음 (온보딩을 끝낼 수 없음)
- 상태: 조사 중 (수정 반영, 실제 앱 재확인 전)
- 관련 Issue·PR: `feat/desktop-onboarding-polish`

## 오류 제목

- 시스템 설정에서 화면 기록을 켜도 온보딩 권한 확인에서 다음으로 넘어가지 못함

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: pnpm 11.19.0
- 화면 기록 권한: 시스템 설정에서 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. 온보딩 권한 확인에서 화면 기록 `설정 열기`를 누른다.
2. 시스템 설정 › 개인정보 보호 및 보안 › 화면 기록에서 FocusOn(개발 실행은 Electron)을 켠다.
3. 앱으로 돌아와 `다시 확인`을 누른다.

## 기대 결과

- 화면 기록이 `허용됨`으로 바뀌고 다음으로 넘어갈 수 있다.

## 실제 결과

- 계속 `아직 꺼져 있어요`로 보이고 `다음`이 막혀 있다.

## 오류 메시지

```text
없음 (systemPreferences.getMediaAccessStatus('screen')이 계속 granted가 아님)
```

## 원인 및 해결

- 추정 원인: macOS는 실행 중인 앱의 화면 기록 허용 상태를 앱을 다시 켜기 전까지 갱신하지 않는다. 그래서 설정에서 켠 뒤 `다시 확인`으로 다시 물어도 꺼짐으로 답한다. 이전 문구(`켰다면 FocusOn을 다시 열어 주세요`)는 있었지만 다시 여는 버튼이 없었고, 다시 열면 동의 체크가 사라져 처음부터 해야 했다.
- 실제 원인: 확인 전 (수정한 앱에서 설정을 켠 뒤 `다시 열기`로 넘어가는지 확인하면 기록한다)
- 해결 방법:
  - 설정을 연 뒤에는 `설정 열기`·`다시 열기` 두 버튼을 보여 준다. `다시 열기`는 먼저 상태를 다시 묻고, 이미 허용이면 그대로 넘어가며, 아니면 앱을 껐다 켠다(`app:relaunch`).
  - 다시 켜면 서버 진행 단계대로 권한 화면으로 돌아오고, 이 기기에 잠깐 기억해 둔 동의 체크를 되살려 권한 확인 화면을 바로 연다. 저장에 성공하거나 뒤로 가면 지운다.
- 변경 파일: `electron/main/permission-ipc.cts`, `electron/shared/permission-contract.cts`, `electron/preload/index.cts`, `features/onboarding/PermissionsStep.tsx`, `features/onboarding/onboarding-api.ts`, `docs/IPC_CONTRACT.md`

## 확인 결과

- [ ] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다.
- [ ] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (Electron 단위 테스트 161개 통과, 화면 상태 캡처 확인)

## 재발 방지 및 남은 작업

- 설치 앱은 ad-hoc 서명이라 새로 빌드할 때마다 macOS가 다른 앱으로 보고 화면 기록을 다시 켜야 할 수 있다. Developer ID 서명으로 해결된다.
