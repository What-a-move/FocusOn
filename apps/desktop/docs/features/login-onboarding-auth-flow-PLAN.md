# Desktop 기능 기획서

> 로그인 구현 전에 범위와 의존성을 확인하기 위한 계획서다. OAuth는 macOS 기본 시스템 브라우저와 임시 loopback callback(`http://127.0.0.1:<임시 포트>`)을 사용한다.

## 기본 정보

- 기능명: 로그인·온보딩 인증 흐름
- 기능 ID: #22
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: 허재원
- 작성일: 2026-10-17
- 우선순위: MVP - 핵심
- 관련 Issue: [#22 로그인·온보딩 인증 흐름 구현](https://github.com/What-a-move/FocusOn/issues/22)
- 예정 Branch: `feat/22-auth-onboarding-flow`
- 관련 Notion·Figma:
  - [로그인 → 학습 목표 설정](https://app.notion.com/p/3de0b6e5e64c80b6b067f5547e7dbcfd)
  - [인증 · 토큰 · 디바이스 정책](https://app.notion.com/p/3de0b6e5e64c80c89c6cfe0b703fd964)
  - [POST /api/v1/auth/google/exchange](https://app.notion.com/p/00f3391cae454614bf51bffbe995f0dc)
  - [POST /api/v1/auth/refresh](https://app.notion.com/p/bacabe41fdc543f9a2575fc05e2e1f81)
  - [GET /api/v1/auth/me](https://app.notion.com/p/2ad306d88bfe42c094c900ceb3e7aff4)

## 기능 목적

- 사용자의 문제: Desktop 앱을 실행해도 로그인 여부를 안전하게 확인하거나 로그인 만료 뒤 복구할 흐름이 아직 없다.
- Desktop에서 제공할 가치: Google 로그인으로 FocusOn 인증을 시작하고, 인증·온보딩 상태에 맞는 화면으로 안내한다.

## 사용자 흐름

1. 앱 시작 시 기존 인증 정보를 확인한다. 유효한 인증이 있으면 현재 사용자와 온보딩 상태를 조회한다.
2. 로그아웃 상태면 로그인 화면과 `Google로 계속하기` 버튼을 보여준다.
3. 사용자가 버튼을 누르면 Desktop Main이 OAuth 흐름을 시작하고 `state`, PKCE `codeVerifier`를 관리한다.
4. 인증 코드를 받으면 `POST /api/v1/auth/google/exchange`로 교환한다.
5. `accessToken`은 메모리에 두고, Desktop `refreshToken`은 macOS Keychain에 저장한다.
6. 로그인 성공 후 신규·미완료 사용자는 마지막 미완료 온보딩 단계로 이동하고, 완료 사용자는 홈으로 이동한다.
7. 온보딩은 닉네임, Extension 연결, 필수 권한·동의, 분석 제외 사이트 단계를 순서대로 거친다. 제외 사이트는 목록을 비워서 건너뛸 수 있지만, 해당 단계를 거쳐야 온보딩이 완료된다.

## Desktop 상세 동작

- 진입 조건: 앱이 실행됐고 Renderer와 Main 간 안전한 IPC 통로가 준비됐다.
- 정상 동작: 현재 인증 복구 → 필요하면 Google 인증 → FocusOn 토큰 교환 → 사용자·온보딩 상태에 따른 라우팅.
- 종료 조건: 로그인 화면 또는 사용자에게 맞는 다음 온보딩·홈 화면이 표시된다.
- 인증 UI 상태: `SIGNED_OUT`, `AUTHENTICATING`, `SIGNED_IN`, `LINK_REQUIRED`, `EXPIRED`, `OFFLINE`을 사용한다. 이 값은 Notion의 온보딩 단계 상태와 합치지 않는다.
- 기존 인증 정보가 없거나 refresh가 만료·폐기된 경우 `AUTH_RELOGIN_REQUIRED`로 인증 정보를 정리하고 로그인 화면으로 이동한다. 새 오류 코드는 만들지 않는다.
- `AUTH_ACCESS_TOKEN_EXPIRED`는 refresh single-flight 1회, 원 요청 재시도 1회로 처리한다.
- 네트워크 오류는 로그아웃으로 오인하지 않고 `OFFLINE` 안내와 재시도 동작을 제공한다.

## macOS 권한 및 시스템 기능

- 필요한 권한: Google OAuth callback 연동과 macOS Keychain 사용은 Electron Main에서 담당한다. 화면·알림 권한과 필수 동의는 로그인 이후 온보딩 단계에서 처리한다.
- 권한 요청 시점: 로그인 버튼 클릭 때 화면·카메라 권한을 요청하지 않는다.
- 권한 허용 시: 해당 온보딩 단계의 완료 상태를 갱신한다.
- 권한 거부 시: 계정과 인증 상태는 유지하고 권한 안내 화면으로 이동한다.
- 활성 앱 또는 Bundle Identifier 처리: 로그인 범위에 해당 없음.

## 화면 및 사용자 경험

- 화면 위치: Desktop 첫 실행 및 재인증 시 Main 화면.
- 주요 컴포넌트: FocusOn 브랜드 영역, Google 로그인 버튼, 진행 표시, 오류 안내·재시도.
- 알림 문구: Google 인증 진행 중에는 중복 클릭을 막고 진행 상태를 표시한다.
- 로딩·빈 상태·오류 상태: OAuth 진행, 취소, 네트워크 오류, 재로그인 필요, 기기 제한을 구분한다. 서버 오류 문구가 아닌 오류 `code`를 기준으로 분기한다.

## 데이터 및 IPC/API

- Renderer 입력: 사용자의 로그인 시작 의사, OAuth 취소 후 재시도, 오류 안내 확인.
- Main 또는 Preload 전달값: OAuth callback과 토큰 저장 명령에 필요한 최소 값만 전달한다. Token 원문은 Renderer에 전달하지 않는다.
- Server·AI 요청 여부: `POST /api/v1/auth/google/exchange`, `POST /api/v1/auth/refresh`, `GET /api/v1/auth/me`를 사용한다. AI 요청은 없다.
- 저장 데이터: Access Token은 메모리, Desktop Refresh Token은 macOS Keychain. `deviceId` 저장 정책은 인증 정본과 구현 결정을 따른다.
- 분석 제외 대상: 로그인 단계에서는 화면·카메라 콘텐츠를 수집하지 않는다.

```json
{
  "authorizationCode": "<one-time-code>",
  "codeVerifier": "<pkce-verifier>",
  "state": "<oauth-state>",
  "device": {
    "installationId": "<desktop-installation-id>",
    "deviceName": "<desktop-name>",
    "platform": "MACOS"
  }
}
```

## 선행 확인 및 의존성

- Notion의 로그인 기능 문서는 현재 `REVIEW` 상태다. API 항목은 `READY`지만 서버 API 구현 완료를 뜻하지 않는다.
- 현재 Desktop에는 로그인 화면과 인증 로직이 없다. Main·Preload 실행 기반, 정적 화면 Loading, Window 생명주기는 구현됐다.
- OAuth 시작·callback 방식은 macOS 기본 시스템 브라우저와 임시 loopback listener(`http://127.0.0.1:<임시 포트>`)로 확정했다. Desktop Main이 listener와 `state` 검증을 담당하고, 응답을 받은 뒤 listener를 닫는다. 앱 내부 BrowserView/WebView와 커스텀 URI scheme은 사용하지 않는다. Google Cloud OAuth 클라이언트와 개발 환경에는 이 데스크톱 loopback 흐름에 맞는 값을 등록한다.
- Backend는 `POST /api/v1/auth/google/exchange`, refresh, auth/me 구현 일정을 인증 담당자와 맞춘다. CI 추가만으로 로그인 API가 구현된 것은 아니다.
- Google OAuth callback과 인증 IPC 경계는 `apps/desktop/docs/DECISION_RECORD.md` 결정 009 및 `IPC_CONTRACT.md`에 확정했다. IPC 코드와 공유 Type을 구현했으며 실제 OAuth·Backend·Keychain 처리는 아직 구현하지 않았다.
- 현재 `dev` 최신 커밋은 `427ff19`이며, 이 계획서는 그 커밋에서 분기했다.

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| OAuth 사용자가 취소 | 로그인을 취소했어. 원하면 다시 시도해줘. | `SIGNED_OUT` 유지 |
| 네트워크 오류 | 서버에 연결할 수 없어. 연결을 확인하고 다시 시도해줘. | `OFFLINE`; 인증 정보를 임의로 지우지 않음 |
| `AUTH_RELOGIN_REQUIRED` | 다시 로그인해줘. | 기존 인증 정보를 정리하고 로그인 화면 표시 |
| Access Token 만료 | 별도 오류 화면 없이 복구 | refresh single-flight 1회 후 원 요청 1회 재시도 |
| Desktop 기기 한도 초과 | 연결된 기기를 확인해줘. | 서버가 반환한 해제 가능 기기 목록 표시 |
| 필수 권한 거부 | 필요한 권한을 설정에서 허용해줘. | 인증은 유지하고 권한 안내로 이동 |

## 보안·개인정보

- 카메라 원본 저장 여부: 로그인 중 사용하지 않고 저장하지 않는다.
- 화면 원본 저장 여부: 로그인 중 사용하지 않고 저장하지 않는다.
- 메모리에서 데이터가 유지되는 시간: Access Token은 메모리에서만 유지하고 만료·로그아웃 시 제거한다.
- Server로 전송하는 최소 데이터: OAuth authorization code, PKCE verifier, state, Desktop 설치 ID·기기 정보.
- Token, authorization code, PKCE verifier, state를 로그·URL·스크린샷·Renderer에 노출하지 않는다.

## 완료 조건

- [x] OAuth 시작·callback·Redirect URI 방식을 결정 기록에 확정했다.
- [x] Main·Preload 인증 IPC 경계와 공유 Type을 구현했다. 실행·Window 기반도 완료했다.
- [ ] macOS Keychain에 Desktop Refresh Token을 안전하게 저장한다.
- [ ] Google code 교환과 로그인 후 사용자·온보딩 상태 라우팅을 구현했다.
- [ ] Access Token 만료, refresh 실패, 네트워크 단절, OAuth 취소를 계약에 맞게 처리했다.
- [ ] 로그인·로그아웃·기기 제한 흐름에서 Token이 Renderer·로그·URL에 노출되지 않는다.
- [ ] 온보딩 미완료 사용자를 마지막 미완료 단계로 안내하고, 제외 사이트 단계를 빈 목록 건너뛰기로 완료할 수 있다.
- [ ] 자동화 테스트와 주요 수동 QA 시나리오를 확인했다.
- [ ] `CONTEXT.md`, `NEXT_TASK.md`, 결과 리포트를 갱신했다.
