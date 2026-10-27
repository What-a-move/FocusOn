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
- 대시보드 `오늘 학습`의 첫 세션 시간 선택지는 `30분`, `1시간`, `2시간`, `직접 입력`이다. 직접 입력은 `5~300분`으로 제한한다.
- `종료 시간 없이 시작`은 MVP 대시보드 선택지로 제공하지 않는다. 당시에는 저수준 API의 `targetDurationMs = null` 계약을 유지했으나, 2026-10-25 재확인한 세션 생성 정본은 MVP null 거절로 변경됐다. 현재 적용 기준은 루트 `docs/API_CONTRACT.md`다.

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
- 로그아웃은 Backend 응답 `204` 또는 명시적 재로그인 오류가 확인된 뒤 Keychain을 비운다(결정 026). 네트워크·서버 오류라면 토큰을 유지한다.

### 결정 이유

Refresh Token을 Keychain에만 보관한다는 Notion 정책과 Renderer에 토큰을 노출하지 않는 IPC 계약을 지키면서, 서버 인증 API가 없는 동안에도 성공을 가장하지 않기 위해서다.

### 영향받는 파일

- `apps/desktop/electron/main/auth-api.cts`, `auth-service.cts`, `auth-keychain.cts`, `desktop-identity.cts`
- `apps/desktop/electron/main/index.cts`, `auth-ipc.cts`
- `apps/desktop/.env.example`, `apps/desktop/package.json`

## 결정 015 - 온보딩 완료와 대시보드 목표 설정 경계

- 결정일: 2026-10-21
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop·Extension
- 상태: 확정

### 결정 내용

- 온보딩 순서는 닉네임 → Extension 연결 → 필수 권한·동의 → 분석 제외 사이트 설정이다.
- 분석 제외 사이트는 저장하거나 빈 목록 건너뛰기를 선택할 수 있다. 두 경우 모두 설정 단계를 완료한 것으로 기록한다.
- 필수 단계 완료 후 대시보드로 이동한다. 목표 입력·AI 명확성 확인·시간·개입 설정은 대시보드 `오늘 학습`에서 시작하며 온보딩 완료 상태에 포함하지 않는다.
- 앱 재실행 뒤 이어갈 단계는 `GET /api/v1/users/me/onboarding-readiness`의 `currentStep`으로 복구한다. 앱 시작·복귀 때 Desktop Main은 로컬 필수 권한을 다시 확인하고 서버에 갱신한다.
- Extension 연결 상태는 서버 pairing, 필수 동의는 서버 consent 기록으로 판정한다. 클라이언트가 완료 상태를 임의로 표시하지 않는다.
- 카메라 동의는 선택 사항이며 온보딩 완료를 막지 않는다. 알림 권한은 결정 018(2026-10-23)에 따라 필수다.

### 결정 이유

필수 계정·기기·권한 준비와 학습 세션 구성을 분리하고, 앱이 종료된 뒤에도 사용자가 마지막 미완료 단계부터 이어갈 수 있게 한다. 빈 제외 목록은 미방문과 건너뛰기를 나타내므로 완료 marker를 별도로 보관한다.

### 영향받는 문서

- Notion `로그인 → 학습 목표 설정`
- Notion `GET /api/v1/users/me/onboarding-readiness`
- Notion `PUT /api/v1/users/me/onboarding-readiness`
- `docs/PROJECT_CONTEXT.md`, `docs/API_CONTRACT.md`
- `apps/desktop/docs/CONTEXT.md`, `NEXT_TASK.md`
- `apps/extension/docs/CONTEXT.md`, `NEXT_TASK.md`

## 결정 016 - 온보딩 macOS 권한 IPC 구현 방식

- 결정일: 2026-10-22
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- `IPC_CONTRACT.md`의 확정 Channel(`permission:screen:get-status`, `permission:screen:open-settings`, `permission:camera:get-status`, `permission:camera:request`, `notification:show`)을 그대로 구현하고 Type은 `electron/shared/permission-contract.cts`에 둔다. Sender 검증은 인증 IPC와 같은 `isTrustedAuthSender`를 쓴다.
- 화면 기록은 가짜 요청 API를 만들지 않는다. 상태가 `not-determined`일 때만 캡처 목록을 한 번 조회해 시스템 설정 목록에 앱이 나타나게 한 뒤 설정 화면을 연다.
- 카메라는 `not-determined`일 때만 OS 허용 창을 띄운다. 이미 거부했으면 상태만 돌려준다.
- macOS는 알림 허용 상태를 조회할 수 없으므로 온보딩에서는 Main이 정한 code의 시험 알림을 보내 허용 창을 띄우고, 화면에는 허용 여부를 단정하지 않고 `요청했어요`로 표시한다.
- 개발 실행에서만 `FOCUSON_DEV_START_PATH`로 첫 화면 경로를 지정할 수 있다. 같은 앱 출처 경로만 허용하고 패키징 앱에서는 무시한다.

### 결정 이유

실제 macOS 동작을 그대로 보여 주고, OS가 알려 주지 않는 상태(화면 기록 요청 여부·알림 허용)를 화면에서 성공으로 꾸미지 않기 위해서다.

### 영향받는 파일

- `apps/desktop/electron/main/permission-ipc.cts`, `electron/shared/permission-contract.cts`, `electron/preload/index.cts`, `electron/main/index.cts`
- `apps/desktop/features/onboarding/onboarding-api.ts`, `PermissionsStep.tsx`
- `apps/desktop/docs/IPC_CONTRACT.md`, `MACOS_PERMISSION_FLOW.md`

## 결정 017 - 온보딩 Backend 연결 구조

- 결정일: 2026-10-22
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (실제 Backend 왕복 QA 전)

### 결정 내용

- 온보딩 Backend 호출은 Renderer가 직접 하지 않고 Main `OnboardingService`가 Desktop 토큰으로 처리한다. 공통 토큰 처리는 `AuthService.authorizedRequest` 하나로 모은다.
- Notion API에 닉네임 중복 확인 전용 endpoint가 없으므로, 화면의 `중복 확인`은 `PUT /users/me/profile` 저장 요청이며 409 `PROFILE_NICKNAME_DUPLICATE`로 중복을 알린다. 성공하면 다음 단계에서 다시 저장하지 않는다.
- Extension 연결 완료는 별도 알림 채널이 없어 Extension 단계에서 3초마다 `onboarding-readiness`의 `extensionLinked`를 확인한다.
- 동의 저장 버전은 `GET /users/me/consents`의 `currentVersion`을 쓰고, 응답에 없을 때만 `2026-09-17`을 쓴다. 출처는 `ONBOARDING`이다(2026-10-22 변경, 결정 019 확정).
- 앱 진입 시 `onboarding-readiness.currentStep`으로 이어갈 화면을 정한다. 조회 실패 시 첫 단계로 보내며, 각 저장은 서버가 다시 검증한다.

### 결정 이유

토큰을 Main에만 두는 기존 인증 경계를 지키면서, 서버 계약이 없는 기능(중복 확인·연결 완료 알림)을 임의 API로 만들지 않기 위해서다.

### 영향받는 파일

- `apps/desktop/electron/main/auth-service.cts`, `auth-api.cts`, `onboarding-service.cts`, `onboarding-ipc.cts`, `index.cts`
- `apps/desktop/electron/shared/onboarding-contract.cts`, `auth-contract.cts`, `electron/preload/index.cts`
- `apps/desktop/features/onboarding/*`, `app/page.tsx`

## 결정 018 - 알림 권한 필수와 확인 방식

- 결정일: 2026-10-23
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정)

### 결정 내용

- 알림 권한은 필수다. 온보딩 권한 확인 화면은 화면 기록과 알림이 모두 준비되어야 다음 단계로 넘어간다.
- macOS가 알림 허용 상태를 알려 주지 않으므로 시험 알림을 보내고 사용자가 `보였어요`를 눌렀을 때만 준비됨으로 본다. 화면에 허용 여부를 단정해 표시하지 않는다.
- 서버 준비 상태에는 알림 필드가 없어 이 조건은 Desktop 화면에서 확인한다. 서버에도 기록하지 않는다(2026-10-24 확정, 결정 019).
- 결정 016의 "알림은 `요청했어요`로만 표시" 부분을 이 결정으로 대체한다.

### 결정 이유

알림은 집중 이탈 안내의 핵심 경로라 필수로 둔다. OS가 상태를 주지 않는 상황에서 성공을 꾸미지 않으려면 사용자의 직접 확인이 가장 정확하다.

### 영향받는 파일

- `apps/desktop/electron/main/permission-ipc.cts`, `electron/shared/permission-contract.cts`, `electron/preload/index.cts`
- `apps/desktop/features/onboarding/PermissionsStep.tsx`, `onboarding-api.ts`, `onboarding.css`
- `FocusOn-design/onboarding.html`, `apps/desktop/docs/MACOS_PERMISSION_FLOW.md`

## 결정 019 - Backend·AI·기획 확인 항목 확정

- 결정일: 2026-10-24
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정, Notion 반영)

### 결정 내용

- `GET /users/me/consents` 응답은 `{ currentVersion, consents: [{ consentType, isGranted, consentVersion, grantedAt, withdrawnAt }] }`다. 현재 `OnboardingService.parseConsents`와 같다.
- 알림 준비 상태는 서버 `onboarding-readiness`에 기록하지 않는다. 세션 시작 때 막는 방식은 결정 022로 바뀌었다(서버가 아니라 Desktop이 막음).
- MVP 온보딩은 제외 사이트(도메인)만 저장한다. 제외 앱은 다음 단계로 미루며, 정할 때 bundleId 문자열 배열(전체 교체)로 제안한다.
- 권한 목록에서 "macOS 활성 앱 확인"을 뺀다. 앞에 띄운 앱 이름 확인은 macOS 권한이 필요 없다.
- 개입 임계값은 정책 `v1`로 서버에서만 관리한다. Desktop은 판단 결과만 받고 숫자를 들고 있지 않는다. 세션에는 `interventionPolicyVersion`으로 고정된다.
- 세션 시작 요청(`POST /sessions`)에는 기기 시간대 IANA 이름(`Intl.DateTimeFormat().resolvedOptions().timeZone`)을 `timeZone`으로 보낸다. 서버는 IP로 추정하지 않고, 값이 없거나 이상하면 `Asia/Seoul`을 쓴다. 시작·종료·일시정지 시각은 서버 시계(UTC)로 확정한다.
- 학습 정리 `keyConcepts`는 리포트 설계서 5.3의 `{ name, briefExplanation, sessionEvidence }`(최대 5개)다. 근거가 부족하면 `INSUFFICIENT_DATA` 상태다.

### 결정 이유

확인 대기로 남아 있던 항목을 Frontend 추천안으로 확정해 Desktop 구현 기준을 하나로 맞춘다.

### 영향받는 파일

- `apps/desktop/electron/main/onboarding-service.cts` (변경 없음, 기준 확인)
- `apps/desktop/docs/MACOS_PERMISSION_FLOW.md`, `docs/features/onboarding-flow-PLAN.md`, `docs/NEXT_TASK.md`
- 이후 세션 시작·리포트 구현 시 이 결정을 따른다.

## 결정 020 - 일간 리포트 세션 항목과 목표 확인 요청 형식

- 결정일: 2026-10-24
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정, Notion API 명세 반영)

### 결정 내용

- `GET /api/v1/reports/daily`의 `sessions[]` 항목은 `{ sessionId, goalTextSnapshot, startedAt, endedAt, accumulatedActiveMs }`다. ERD `learning_sessions` 컬럼 이름을 따른다. 홈 지난 목표가 쓴다.
- `POST /api/v1/goals/clarify` 요청은 `{ goalText, conversationTurn, clarificationSessionId?, previousAnswers: [{ question, answer }] }`, 응답은 기존 필드에 `clarificationSessionId`를 더한다(AI 분석 계약 3.2와 같은 필드). 저장(`POST /goals`)에도 같은 `clarificationSessionId`를 보낸다.
- Desktop은 목표 확인 POST를 자동 재전송하지 않는다. AI 재시도(1초 → 3초, 최대 2회)는 Backend가 맡고, 최종 실패면 Desktop이 수동 확정을 보여 준다.

### 결정 이유

API 명세에 항목 필드와 질문 답 전달 방법이 없어 화면을 만들 수 없었다. 이미 정한 ERD 이름과 AI 계약 필드를 재사용해 새 이름을 만들지 않는다.

### 영향받는 파일

- `apps/desktop/electron/shared/report-contract.cts`, `goal-contract.cts`
- `apps/desktop/electron/main/report-ipc.cts`, `goal-ipc.cts`
- `apps/desktop/docs/IPC_CONTRACT.md`, `features/dashboard-home-PLAN.md`, `features/goal-setting-PLAN.md`

## 결정 021 - 세션 시작 요청 형식

- 결정일: 2026-10-24
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정, Notion `POST /api/v1/sessions` 반영)

### 결정 내용

- `POST /api/v1/sessions` 본문은 `{ goalId, targetDurationMs, timeZone, sessionOptions: { interventionLevel, notificationChannels, cameraAnalysisEnabled }, startReadiness: { screenAnalysisReady } }`다. 헤더는 `Idempotency-Key`·`X-Device-Id`.
- `sessionOptions`는 focus-settings와 같은 필드 이름을 쓰고 `breakSuggestionEnabled`는 넣지 않는다.
- `startReadiness`는 Main이 확인한 화면 기록 상태만 보낸다. 알림은 서버에 기록하지 않는다(결정 019와 같은 이유).
- `targetDurationMs`는 5~300분(분 단위)만 보낸다. MVP에는 종료 없는 세션이 없어 명세의 null 설명을 지웠다.
- 시작 요청은 자동 재전송하지 않고, 같은 선택으로 다시 누르면 같은 `Idempotency-Key`를 보낸다.

### 결정 이유

Notion에 `sessionOptions and startReadiness` 이름만 있어 화면을 만들 수 없었다. 이미 정한 focus-settings 필드와 알림 미기록 결정을 재사용한다.

### 영향받는 파일

- `apps/desktop/electron/shared/session-contract.cts`, `electron/main/session-ipc.cts`, `electron/main/auth-service.cts`
- `apps/desktop/features/session/`, `docs/IPC_CONTRACT.md`, `docs/features/session-setup-PLAN.md`

## 결정 022 - 알림 준비는 Desktop이 막고 서버는 검증하지 않음

- 결정일: 2026-10-25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정, Notion 반영)

### 결정 내용

- `startReadiness`에는 화면 기록 상태만 보내므로 서버는 알림 준비 여부를 알 수 없다. 서버는 알림 권한을 검증했다고 처리하지 않고, `SESSION_START_REQUIREMENT_MISSING`의 누락 항목은 `SCREEN_PERMISSION`·`EXTENSION_LINK`뿐이다.
- Desktop은 사용자가 시험 알림을 보고 누른 `보였어요`/`안 보였어요`를 Main이 `userData/notification-readiness.json`에 기록한다(온보딩 권한 화면, 세션 설정 화면).
- `SessionService.start`는 기록이 없거나 `안 보였어요`면 서버에 요청하지 않고 `LOCAL_NOTIFICATION_NOT_CONFIRMED`를 돌려준다. 세션 설정 화면은 그 자리에서 시험 알림 → 확인(안 보이면 시스템 설정 › 알림) 흐름을 보여 준다.
- 세션 시작 헤더는 `Authorization`·`Idempotency-Key`·`X-Device-Id` 세 개다. `Authorization`은 `AuthService.authorizedRequest`가 모든 인증 요청에 붙인다.

### 결정 이유

macOS는 앱에 알림 허용 상태를 알려 주지 않는다. 서버가 검증한다고 적어 두면 실제로는 아무도 막지 않는 빈틈이 생기므로, 사용자 확인을 기록한 Desktop이 시작을 막는다.

### 한계

- 사용자가 확인 뒤 시스템 설정에서 알림을 끄면 Desktop도 알 수 없다. macOS가 상태 API를 주면 그걸로 바꾼다.

### 영향받는 파일

- `apps/desktop/electron/main/notification-readiness.cts`, `permission-ipc.cts`, `session-ipc.cts`, `index.cts`, `electron/shared/permission-contract.cts`, `preload/index.cts`
- `apps/desktop/features/onboarding/PermissionsStep.tsx`, `onboarding-api.ts`, `features/session/`

## 결정 023 - 집중 세션 조회·목표 도달·재개 형식

- 결정일: 2026-10-25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정 (사용자 결정, Notion API 명세 반영)

### 결정 내용

- `GET /api/v1/sessions/active`(없으면 204)·`GET /api/v1/sessions/{id}` 응답 data는 `{ sessionId, status, version, goalTextSnapshot, targetDurationMs, accumulatedActiveMs }`다(상세는 `interventionPolicyVersion` 포함).
- 목표 시간에 닿으면 Desktop이 누적을 멈추고 사용자 확인(끝내기·연장)을 기다리며, heartbeat는 `accumulatedActiveMs = targetDurationMs`로 계속 보낸다. 서버는 이 값으로 `AWAITING_END_CONFIRMATION`을 판단하고 별도 전이 API는 없다.
- `POST /sessions/{id}/resume` 본문은 `{ readiness: { screenAnalysisReady } }`다. Chrome 연결은 서버가 직접 확인하고, 알림은 보내지 않는다(결정 022).
- heartbeat는 10초마다 보낸다. 처음에는 20초로 정했으나 정본(학습 세션 설계서·세션 상태 전이 정책)이 10초라 2026-10-25에 10초로 통일했다.
- MVP에서 세션 명령(일시정지·재개·연장·종료)과 heartbeat는 Desktop만 보낸다. Chrome Extension 팝업은 표시만 한다(Extension 결정 012).

### 결정 이유

Notion에 조회 응답 필드와 목표 도달 전이 방법이 없어 앱 재실행 복구와 시간 종료 처리를 만들 수 없었다. 새 API 없이 이미 있는 heartbeat로 판단해 계약을 늘리지 않는다.

### 영향받는 파일

- `apps/desktop/electron/main/session-runtime.cts`, `electron/shared/session-contract.cts`, `electron/main/auth-api.cts`(204 처리)
- `apps/desktop/docs/IPC_CONTRACT.md`, `docs/features/focus-session-PLAN.md`

## 결정 024 - 화면 분석 수집·전송 방식

- 결정일: 2026-10-25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop (Extension 결정 013과 함께)
- 상태: 확정 (사용자 결정, Notion 화면 분석·AI 분석 계약·Chrome·macOS 권한 정책·`POST /sessions/{id}/analysis-runs` 반영). 백엔드·AI·PM에 통보함.

### 결정 내용

- Desktop은 Chrome 밖의 앱을 맡는다. 맨 앞 앱이 Chrome이면 관측·전송하지 않고 Extension에 맡긴다.
- MVP는 macOS + Chrome만 지원한다. Chrome이 아닌 브라우저(Safari·Arc·Edge 등)와 FocusOn 자기 창은 15초 유지 뒤 `guardStatus: EXCLUDED`만 보낸다(앱 이름·창 제목·OCR 없음). 아무것도 보내지 않으면 서버가 직전 판정(UNRELATED 등)을 이어 가 이탈로 셀 수 있기 때문이다. 리포트에는 분석 제외 시간으로 잡히고, 다른 브라우저에서의 이탈은 감지하지 못한다(MVP 제한, 2026-10-25 추가).
- Swift 도우미 프로세스를 앱에 넣는다. Main이 띄우고 JSON 줄 단위로 통신한다. 도우미가 맨 앞 앱(`NSWorkspace`)·창 제목(`CGWindowList`)을 확인하고, 대상 창 하나만 캡처(`SCScreenshotManager`)해 Apple Vision으로 OCR한다. 캡처 이미지는 도우미 메모리에서만 다루고 OCR 직후 버린다. Node·Renderer에는 텍스트만 넘긴다.
- 창 하나만 캡처하므로 Floating Poki가 이미지에 들어가지 않는다. 지원 macOS는 14 이상이다.
- 먼저 `APP_META`만 보낸다. 결과가 `RELATED`면 확정하고, `UNRELATED`·`UNCERTAIN`이면 항상 `DESKTOP_APP_OCR`로 다시 보내 그 결과로 확정한다(2026-10-26 변경). 정확도 테스트에서 정보가 부족한 제목에도 AI가 `UNRELATED`를 낸 경우가 있어, 메타데이터만으로는 관련 없음을 확정하지 않는다. 서버도 메타데이터만 근거인 `UNRELATED`는 이탈 시간에 넣지 않는다. OCR 텍스트는 최대 2,000자, 이메일·전화번호·긴 토큰 형태 문자열은 지운다.
- 늦게 도착한 이전 AI 결과는 최신 상태를 덮지 않는다(2026-10-26). 관측마다 번호를 붙이고, 응답이 지금 화면의 관측이 아니면 화면에 반영하지 않으며 그 응답으로 다음 단계도 보내지 않는다. 예: 화면 A의 2차 요청 중 제외 대상으로 옮겨 `EXCLUDED`를 보냈다면, 늦게 온 A 결과는 버린다. 서버도 같은 기기에서는 `contentSource`와 관계없이 `observedAt`이 가장 늦은 관측을 현재 상태로 본다(AI 분석 계약 8).
- 앱·창 제목이 바뀌고 15초 유지되면 보낸다. 같은 화면은 다시 보내지 않고, 같은 창의 내용 변경은 최대 30초에 한 번 보낸다.
- 분석 제외 앱은 MVP에 포함한다. 번들 ID 목록을 Desktop 로컬 파일에만 저장하고 서버에 보내지 않는다. 설정(분석·권한)과 온보딩 제외 단계(선택)에서 고른다. 맨 앞 앱이 제외 앱이면 `guardStatus: EXCLUDED`만 보낸다.
- 민감 화면은 고정 민감 앱 목록·창 제목 단어로 관측 전에, OCR 텍스트 패턴으로 보내기 전에 걸러 `guardStatus: PRIVACY_BLOCKED`로 보낸다.
- 관측 전송(2026-10-25, Issue #56): 관측마다 새 `clientRunId`, 세션별 `sequence`, `contentSource: APP_META`로 보낸다. 실패하면 다시 보내지 않고 쌓지 않는다(서버가 기록하지 못한 시간은 판단 보류와 같다). 429는 60초 쉰다.
- 진행 순서(2026-10-25): 메인 흐름을 먼저 잇는다. 2단계 Desktop 전송 → 3단계 Extension 탭·DOM → 4단계 개입 표시 → 5단계 Desktop OCR(정확도 보강).
- 개입은 10초마다 `GET /sessions/{id}/interventions`로 조회해 Floating Poki·macOS 알림으로 표시하고 `POST /interventions/{id}/acknowledgements`로 기록한다.
- 세션 중 화면 기록 권한 철회는 10초마다 권한 확인 + 캡처 실패로 감지하고, 즉시 로컬 타이머를 멈춘 뒤 `local-events`에 `LOCAL_SCREEN_PERMISSION_REVOKED`를 보낸다.

### 결정 이유

알림 판단은 서버 Policy Engine만 하므로 클라이언트는 최소 정보를 정확히 걸러 보내는 데 집중한다. 화면 변화 때만 보내 분당 8회 제한과 AI 비용을 지키고, 원본 이미지를 Node 밖 도우미에 가둬 개인정보 노출 범위를 줄인다. 제외 앱 목록은 그 자체가 민감할 수 있고 기기마다 달라 서버에 두지 않는다.

### 서버 쪽 전제 (통보함)

- `analysis-runs` 요청에 `metadata`·`guardStatus`, 응답에 `reasonCode`를 추가한다.
- Policy Engine은 새 관측 전까지 마지막 판정이 계속된다고 보고 heartbeat마다 이탈 시간을 다시 계산한다.

## 결정 025 - 서버 계약 타입은 shared-types로 공유한다 (타입만)

- 결정일: 2026-10-26
- 담당 영역: Frontend(`apps`)·Shared(`packages/shared-types`)
- 대상 앱: Desktop·Chrome Extension
- 상태: 확정 (Issue #69)

### 결정 내용

- 두 앱이 함께 쓰는 서버 계약 타입(`Relation`, `ContentSource`, `GuardStatus`, `ReasonCode`, `SessionStatus`, `AnalysisRunRequest`·`AnalysisRunResponse`)은 `@focuson/shared-types`에 두고 `import type`으로만 가져온다. 기존 이름(`SessionStatus`, `GuardStatus`, `RunBody`, `AnalysisRun`)은 각 앱에서 다시 내보내 사용처를 바꾸지 않았다.
- 확인(parse) 함수·허용 값 배열 같은 **실행 코드는 공유하지 않고** 각 앱에 둔다.

### 결정 이유

같은 계약을 두 앱이 따로 적으면 계약이 바뀔 때 한쪽을 놓친다. 다만 `shared-types`는 빌드 없이 TS 원본을 내보내므로, Desktop Main(`.cjs`)이나 Extension `node --test`에서 실행 값을 가져오면 깨진다. `import type`은 컴파일 때 사라져 이 문제가 없다(빌드 결과에 `shared-types` 참조가 남지 않음을 확인).

### 영향받는 파일

- `packages/shared-types/src/index.ts`
- `electron/shared/session-contract.cts`, `electron/main/screen-observer.cts`, `electron/main/analysis-sender.cts`

## 결정 026 - 로그아웃 도중 Access Token 만료 처리

- 결정일: 2026-10-26 / 상태: 기존 로그인 계약의 버그 수정 / Issue #59·PR #60.
- AuthApiClient는 logout의 모든 401을 성공으로 바꾸지 않고 서버 오류 code를 전달한다.
- AuthService는 유효한 token으로 logout을 먼저 요청하고, 서버 AUTH_ACCESS_TOKEN_EXPIRED에는 single-flight refresh 뒤 logout을 한 번만 재시도한다. 두 번째 만료·알 수 없는 401·네트워크/서버 실패는 성공 표시와 Keychain 삭제를 하지 않는다.
- 204 또는 명시적 AUTH_RELOGIN_REQUIRED·AUTH_TOKEN_REUSE_DETECTED·AUTH_OAUTH_REAUTH_REQUIRED만 로컬 정리를 완료한다. API 형식과 서버 계약은 그대로다.
- 실제 만료 JWT/서버/Keychain QA에서 401→refresh200→logout204와 연결 Extension 폐기·활성 기기 슬롯 반환을 확인했다. 실제 Google 인증 완료를 뜻하지 않는다.

## 결정 027 - 세션 결과 summary 응답은 제안 형식으로 받고 빠진 값은 "기록 없음"으로 둔다

- 결정일: 2026-10-27 / 상태: 제안 (백엔드 확인 대기) / Issue #73.
- Notion 리포트 설계서 3.1에는 시간 필드 이름만 있어, `GET /sessions/{id}/summary`를 `sessionId`·`goalSnapshot`·`targetDurationMs`·`accumulatedActiveMs`(필수)와 `status`·`startedAt`·`endedAt`·`totalPausedMs`·`related/unrelated/uncertainDurationMs`·`extensionCount`·`extendedDurationMs`·`pauseCount`·`autoPauseCount`·`autoPauseReasons[]`·`interventionCount`(선택)로 받는다.
- 필수 값이 없으면 조회 실패로 보고, 선택 값이 없거나 형식이 다르면 null로 두어 화면에 "기록 없음"으로 표시한다. 흐름 막대는 관련·무관·보류 세 값이 모두 있을 때만 그린다.
- 분석 안 됨 시간과 목표 활동 비율은 Desktop이 계산한다(설계서 3.2). 학습 정리는 `/learning-summary`로 따로 받는다.
- 이유: 백엔드가 일부 필드만 먼저 내려도 화면이 열리게 하고, 거짓 값을 만들지 않기 위해서다. 백엔드가 이름을 바꾸면 `parseSessionSummary`만 고친다.
- 영향받는 파일: `electron/shared/report-contract.cts`, `electron/main/report-ipc.cts`, `features/report/*`
