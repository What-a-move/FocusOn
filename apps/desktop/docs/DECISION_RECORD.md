# Desktop 결정 기록

> Desktop 설계·기술·권한 처리에 관한 합의를 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - Desktop 실행 환경

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

macOS 전용 Desktop 앱은 Electron + Next.js + React + TypeScript 기반으로 개발한다.

### 결정 이유

Next.js 정적 빌드 기반의 React 화면 기술을 유지하면서 macOS 앱 확인, 화면 캡처, 알림, 권한 처리를 Electron에 연결하기 쉽다.

### 영향 범위

- Renderer 화면 구조
- Main·Preload·Renderer 분리
- macOS 권한 처리
- Extension과의 상태 연결

## 결정 002 - 민감 데이터 기본 처리

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

원본 카메라 영상과 원본 화면을 기본 저장하지 않고, 분석에 필요한 상태값과 최소 메타데이터만 사용한다.

### 결정 이유

사용자의 개인정보 노출과 불필요한 저장을 줄이기 위해서다.

### 후속 확인

- [x] 카메라 권한 거부 UX 확정
- [ ] 화면 캡처 데이터의 메모리 처리 범위 확정
- [ ] Server 전송 필드 확정

### 결정 003 - Desktop 중심 학습 세션과 Extension 동기화

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)·Server
- 대상 앱: Desktop·Extension
- 상태: 확정

#### 결정 내용

Desktop이 발급한 일회용 연결 코드를 Extension이 입력해 같은 사용자 기기로 pairing한다. 두 클라이언트는 동일한 학습 세션을 공유하고, 상태와 시간은 Server가 관리한다. Desktop은 학습 시작·카메라·화면 분석을 주로 담당하며 Extension은 타이머 조회와 일시정지·재개·종료 명령을 제공한다.

#### 결정 이유

두 클라이언트가 각자 타이머를 계산하면 시간 차이가 발생할 수 있다. Server를 기준으로 두면 Desktop에서 시작한 세션을 Extension에서 확인하거나, Extension에서 일시정지한 상태를 Desktop에 동일하게 반영하기 쉽다.

#### 고려한 대안

- Desktop과 Extension을 Native Messaging으로 직접 연결하는 방식
- 각 클라이언트가 독립적으로 타이머를 계산하는 방식

#### 후속 확인

- [x] 일회용 연결 코드 기반 Extension pairing 확정
- [ ] 초기 MVP 동기화 방식(API 주기 조회 또는 WebSocket) 확정
- [x] 상태 명령의 idempotency key와 version, Heartbeat 예외 확정

### 결정 004 - 화면 부유형 두더지 피드백

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

#### 결정 내용

학습 시작 시 Desktop 화면 위에 떠 있는 두더지 마스코트를 표시한다. 집중 상태에서는 파인 땅에서 한 손으로 팻말을 들고 응원 문구를 보여주고, 집중 이탈 상태에서는 표정·자세·말풍선을 바꿔 복귀를 안내한다. 상단 메뉴 막대 아이콘은 상태 확인·설정·앱 종료를 위한 보조 메뉴로만 사용한다.

#### 결정 이유

분석 결과를 숫자나 경고창만으로 전달하는 대신 FocusOn만의 반복 사용 가능한 화면 부유형 피드백 경험을 제공하기 위해서다.

#### 사용자 설정

- 팻말에 `남은 시간` 표시
- 팻말에 매번 바뀌는 `응원 문구` 표시
- 응원·경고 피드백 표시 여부
- 피드백 강도와 알림 빈도

#### 후속 확인

- [ ] 두더지 상태별 디자인 목록 확정
- [ ] 문구 생성·선택 방식과 금칙어 기준 확정
- [ ] 동일 사이트 재방문 시 문구 강도 기준 확정

## 결정 005 - Electron Build·Loading·배포 방식

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Main·Preload는 TypeScript Compiler로 `dist-electron/`에 Build한다.
- Renderer는 Next.js Static Export의 `out/`을 사용한다.
- 개발 환경은 `http://127.0.0.1:3000`, Production은 보안 설정된 `app://` Custom Protocol로 Renderer를 연다.
- Packaging은 현재 설치된 electron-builder를 사용한다.
- 배포 시 `arm64`와 `x64`를 각각 Build해 `dmg`와 `zip`을 제공한다.
- 정식 배포는 Developer ID Application 서명, Hardened Runtime, Apple Notarization과 Stapling을 적용한다.
- 자동 Update는 MVP에서 제외한다.

### 결정 이유

현재 설치된 도구를 활용하면서 Main·Preload와 Next.js Renderer의 Build 책임을 분리할 수 있다. Production에서 `file://`보다 접근 범위를 제한한 전용 Protocol을 사용하고, Architecture별 Build를 분리하면 이후 Native Module이 추가될 때 Universal Merge 문제를 줄일 수 있다.

## 결정 006 - Window와 앱 생명주기

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- 앱은 Single Instance로 실행한다.
- Main Window를 닫으면 Window만 숨기고 앱과 학습 세션은 유지한다.
- `Cmd+Q` 또는 명시적인 종료 메뉴에서만 앱을 완전히 종료한다.
- Settings는 MVP에서 Main Window의 `/settings` Route로 구현한다.
- Floating Window는 기본적으로 클릭 가능하며 지정된 Handle에서만 Drag한다.
- 위치는 Display ID와 화면 내부 비율로 저장하고 화면 구성이 바뀌면 Primary Display 안으로 보정한다.

### 결정 이유

학습 세션과 Floating Feedback을 유지하면서 macOS 앱의 일반적인 닫기·종료 동작을 제공하고, 초기 Window 수와 상태 복잡도를 줄이기 위해서다.

## 결정 007 - IPC 오류와 macOS 권한 UX

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Preload에는 기능별 IPC 함수만 노출하고 범용 Channel 전송 함수를 제공하지 않는다.
- IPC 실패 응답은 `code`와 `retryable`만 전달하며 Renderer가 사용자 안전 문구로 변환한다.
- Camera·Screen Recording·Notification은 실제 사용 직전에 각각 이유를 설명하고 요청한다.
- 권한 거부 시 Timer와 앱은 유지하고 해당 분석 기능만 중지한다.
- 제외 앱 또는 제외 도메인 상태에서는 화면·OCR·AI 콘텐츠 분석을 중지하고 타이머와 동의된 Camera 분석은 유지한다. 민감 화면 `PRIVACY_BLOCKED`에서는 Camera 분석도 중지한다. Notion DR-019 정책을 따른다.

### 결정 이유

Renderer에 내부 오류와 강한 Electron 권한이 노출되는 것을 막고, 사용자가 권한을 이해하고 선택할 수 있게 하기 위해서다.

## 결정 008 - 온보딩 제외 설정 단계와 첫 세션 시간 선택

- 결정일: 2026-10-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- 온보딩에서 분석 제외 사이트 설정 단계를 반드시 보여준다. 사용자가 사이트를 등록하거나 빈 목록으로 건너뛰기를 선택하면 이 단계를 완료하고 온보딩을 끝낼 수 있다. 이후 설정에서 목록을 수정할 수 있다.
- 첫 세션 시간 선택지는 `30분`, `1시간`, `2시간`, `직접 입력`이다. 직접 입력은 `5~300분`으로 제한한다.
- `종료 시간 없이 시작`은 MVP 온보딩 선택지로 제공하지 않는다. 저수준 API의 `targetDurationMs = null` 계약은 유지한다.

### 결정 이유

제외 목록을 설정할 기회를 모든 신규 사용자에게 제공하면서 등록 자체를 강제하지 않고, 확정된 세션 시간 선택지를 로그인·첫 세션 흐름에서 일관되게 사용하기 위해서다.

### 고려한 대안

- 제외 설정 단계를 통째로 생략: 제외 기능을 알아차리지 못할 수 있어 채택하지 않는다.
- 기존 `25·50·90분` 및 무기한 시작 프리셋 유지: 확정된 사용자 선택과 달라 채택하지 않는다.

### 영향받는 파일

- `docs/PROJECT_CONTEXT.md`
- `docs/API_CONTRACT.md`
- `apps/desktop/docs/DECISION_RECORD.md`
- Notion 로그인·기능 카탈로그·Figma 화면 가이드

## 결정 009 - Google 로그인 callback과 인증 IPC 경계

- 결정일: 2026-10-17
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Google 인증은 macOS 기본 시스템 브라우저에서 진행하고, Desktop Main이 임시 loopback listener(`http://127.0.0.1:<임시 포트>`)로 authorization code를 받는다.
- Main이 OAuth `state`와 PKCE verifier를 생성·검증하고, code 교환과 토큰 저장을 담당한다. 앱 내부 BrowserView/WebView와 커스텀 URI scheme callback은 사용하지 않는다.
- Renderer는 Preload가 제공하는 이름 있는 인증 함수만 호출한다. 범용 IPC Channel 전송 API는 노출하지 않는다.
- 로그인 상태 조회·로그인 시작·로그아웃은 `auth:session:get`, `auth:google-login:start`, `auth:logout`을 사용한다. 로그아웃은 기존 `POST /api/v1/auth/logout` 계약을 호출한다.
- 로그인 시작은 요청·응답 IPC로 접수하고, 완료·취소·실패·인증 상태 변화는 `auth:session:changed` Event와 `auth:session:get` Snapshot으로 전달한다.
- Renderer에는 화면에 필요한 인증 상태, 사용자 요약, `onboardingStatus`만 전달한다. Token과 OAuth 임시 비밀값은 전달하지 않는다.
- Access Token과 인증된 서버 요청은 Main에서 관리한다. Refresh Token은 macOS Keychain에 저장한다.

### 결정 이유

설치형 앱의 Google 인증을 기본 브라우저로 분리하고, Node.js·macOS 권한을 가진 Main과 웹 UI인 Renderer 사이에 최소 권한 경계를 유지하기 위해서다.

### 영향받는 파일

- `apps/desktop/docs/IPC_CONTRACT.md`
- `apps/desktop/docs/ELECTRON_ARCHITECTURE.md`
- `apps/desktop/docs/features/login-onboarding-auth-flow-PLAN.md`

## 결정 010 - Sandbox Preload 단일 파일 Build

- 결정일: 2026-10-18
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

결정 005의 Main·Preload Build 방향 중 Preload 부분을 구체화한다. Main은 TypeScript Compiler로 컴파일하고, `sandbox: true`인 Preload는 TypeScript 타입 검사 후 esbuild로 공유 런타임 코드를 단일 `.cjs` 파일에 묶는다. 두 결과는 모두 `dist-electron/`에 둔다.

### 결정 이유

TypeScript Compiler만 사용하면 Preload의 상대 경로 import가 런타임에 남아 실제 Electron Sandbox에서 로딩되지 않았다. 단일 파일로 묶으면 Sandbox 보안 설정을 유지하면서 공유 계약 코드를 사용할 수 있다.

### 영향받는 파일

- `apps/desktop/package.json`
- `apps/desktop/scripts/build-electron-preload.cjs`
- `apps/desktop/docs/ELECTRON_ARCHITECTURE.md`

## 결정 011 - Issue #22의 온보딩 완료 경계

- 결정일: 2026-10-19
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Issue #22는 Desktop Google 로그인, 인증 상태 복구·갱신·로그아웃, 로그인 성공 후 온보딩 시작 화면 또는 홈 화면 진입까지 구현한다.
- 닉네임 저장, Extension 연결, 권한·동의, 분석 제외 사이트 설정과 온보딩 완료 처리는 별도 Issue에서 구현한다.
- Notion의 전체 온보딩 순서·필수 단계·완료 조건은 바꾸지 않는다. 이번 결정은 Issue의 작업 종료 지점만 정한다.

### 결정 이유

인증을 끝낸 사용자를 올바른 화면으로 보내는 작업과, 이후 여러 온보딩 단계를 구현하는 작업의 완료 기준을 분리하기 위해서다.

### 영향받는 파일

- `apps/desktop/docs/features/login-onboarding-auth-flow-PLAN.md`
- `apps/desktop/docs/NEXT_TASK.md`

## 결정 012 - Google callback 수신의 실패 경계

- 결정일: 2026-10-19
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Google 데스크톱 Client ID는 Electron Main의 `FOCUSON_GOOGLE_CLIENT_ID` 환경 변수로 받는다. 값이 없으면 브라우저를 열지 않고 `SERVICE_UNAVAILABLE`을 반환한다.
- Main은 `127.0.0.1` 임시 포트의 기본 주소만 받고 Host·`state`를 검증한다. PKCE verifier와 `state`는 시도마다 새로 만든다. listener는 완료·취소·5분 timeout·앱 종료에 정리한다.
- `{ started: true }`는 브라우저 인증 시작만 뜻한다. Backend code 교환이 없으므로 callback을 받아도 인증 상태를 `SIGNED_IN`으로 만들지 않는다.

### 결정 이유

서버가 준비되기 전에 OAuth 수신 경계를 검증하면서도 실제 로그인 성공이나 Token 발급을 가장하지 않기 위해서다.

### 영향받는 파일

- `apps/desktop/electron/main/google-oauth.cts`
- `apps/desktop/electron/main/auth-ipc.cts`
- `apps/desktop/docs/IPC_CONTRACT.md`

## 결정 013 - Google code 교환의 redirectUri 계약

- 결정일: 2026-10-19
- 담당 영역: Frontend(`apps`)·Backend(`server`)
- 대상 앱: Desktop·Backend
- 상태: 확정

### 결정 내용

- Desktop Main은 Google 인증 요청에 사용한 `http://127.0.0.1:<임시 포트>` 주소를 `redirectUri`로 보관하고, callback의 `state` 일치를 검증한다.
- `POST /api/v1/auth/google/exchange`에는 `authorizationCode`, `codeVerifier`, `state`, `redirectUri`, `device`를 보낸다.
- Backend는 `redirectUri`를 loopback 기본 주소로 제한하고 서버 설정의 Desktop Google Client ID, code, verifier, 동일한 `redirectUri`를 Google token endpoint에 전달한다. 반환된 Google ID token을 검증한 뒤 FocusOn 토큰을 발급한다.
- Backend가 이전 `state`를 저장하지 않았다면 요청에 담긴 `state`만으로 일치 여부를 검증했다고 간주하지 않는다.

### 결정 이유

Google token endpoint는 code 교환에 최초 인증 요청의 `redirect_uri`를 요구한다. Desktop의 임시 포트는 시도마다 달라지므로 Desktop이 정확한 주소를 Backend에 전달해야 한다.

### 영향받는 문서

- Notion `POST /api/v1/auth/google/exchange`, `인증 · 토큰 · 디바이스 정책`
- `docs/API_CONTRACT.md`
- `apps/docs/AUTH_SESSION_FLOW.md`
- `apps/desktop/docs/features/login-onboarding-auth-flow-PLAN.md`
- `server/docs/CONTEXT.md`, `server/docs/NEXT_TASK.md`

## 결정 014 - Desktop 인증 토큰 저장과 서버 요청 경계

- 결정일: 2026-10-19
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- `@napi-rs/keyring`의 비동기 API로 Refresh Token 원문을 macOS Keychain에 직접 저장한다. Backend origin별로 Keychain 항목을 분리한다.
- Access Token은 Electron Main 메모리에만 둔다. 인증 API 요청과 응답 검증도 Main에서 처리하며 Renderer에는 `AuthSessionSnapshot`만 전달한다.
- `FOCUSON_API_BASE_URL`은 HTTPS 또는 로컬 개발용 loopback HTTP origin만 허용한다. 설정이 없으면 Google 브라우저 인증을 시작하지 않는다.
- Google code 교환 성공이라도 Keychain 저장이 실패하면 `SIGNED_IN`으로 전이하지 않는다. 앱 재실행 때 저장된 Refresh Token을 1회 회전하고 `auth/me`를 조회한다.
- 로그아웃은 Backend 응답 `204` 또는 계약상 `401`을 받은 뒤 Keychain을 비운다. 네트워크·서버 오류라면 토큰을 유지한다.

### 결정 이유

Refresh Token을 Keychain에만 보관한다는 Notion 정책과 Renderer에 토큰을 노출하지 않는 IPC 계약을 지키면서, 서버 인증 API가 없는 동안에도 성공을 가장하지 않기 위해서다.

### 영향받는 파일

- `apps/desktop/electron/main/auth-api.cts`, `auth-service.cts`, `auth-keychain.cts`, `desktop-identity.cts`
- `apps/desktop/electron/main/index.cts`, `auth-ipc.cts`
- `apps/desktop/.env.example`, `apps/desktop/package.json`
