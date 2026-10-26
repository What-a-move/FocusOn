# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 화면 관측 기반과 분석 제외 앱 (화면 분석 1단계)
- 기능 ID: 화면 분석 3.1(1차 메타데이터)·6.1.1(분석 제외 앱)·6.2(민감 화면 관측 전 차단)·7.2(화면 권한 상실)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-25
- 우선순위: MVP - 핵심
- 관련 Issue: #54
- 예정 Branch: `feat/54-desktop-screen-observation`
- 관련 Notion·Figma: 화면 분석(3.1·6.1.1·6.2·7.2), AI 분석 계약 4.1, Chrome·macOS 권한 정책 3.4, `POST /sessions/{id}/local-events`, 결정 024
- 디자인 원본: `FocusOn-design/exclusions.html`(앱 탭·`제외할 앱 고르기` 대화상자), `FocusOn-design/onboarding.html`(제외 사이트 단계)

## 기능 목적

- 사용자의 문제: 세션 중 지금 보고 있는 앱이 목표와 관련 있는지 판단하려면 먼저 "지금 어떤 앱·창을 보고 있는지"를 안전하게 알아야 한다. 분석을 원하지 않는 앱도 고를 수 있어야 한다.
- Desktop에서 제공할 가치: 맨 앞 앱·창 제목을 권한 팝업 없이 읽고, 제외 앱·민감 앱은 캡처 전에 걸러 낸다. 화면 기록 권한이 사라지면 바로 타이머를 멈춘다.

## 이번 단계 범위

| 포함 | 다음 단계로 미룸 |
| --- | --- |
| Swift 도우미 1차(맨 앞 앱·창 제목, 설치된 앱 목록) | 대상 창 캡처·Vision OCR (2단계) |
| 관측 상태 기계(15초 유지, Chrome·FocusOn 건너뜀, 제외·민감 판정) | `POST /sessions/{id}/analysis-runs` 전송 (2단계) |
| 분석 제외 앱 저장(로컬)과 온보딩 제외 단계의 앱 선택 | 설정 › 분석·권한의 제외 앱 관리 (설정 화면 작업 때) |
| 화면 기록 권한 철회 감지 → 로컬 타이머 정지 + `local-events` | 같은 창 내용 변경(30초) 감지 — OCR 텍스트가 필요해 2단계 |

## 사용자 흐름

1. 온보딩 제외 사이트 단계에 `분석 제외 앱 (선택)`이 함께 보인다. `앱 추가`를 누르면 설치된 앱 목록(실행 중인 앱 먼저)에서 고른다. 건너뛰어도 된다.
2. 세션이 `RUNNING`이면 Main이 1초마다 맨 앞 앱을 확인한다. 앱·창 제목이 바뀌고 15초 그대로면 관측 1건을 만든다.
3. 세션 중 화면 기록 권한을 끄면 즉시 타이머가 멈추고(`AUTO_PAUSED`) 집중 화면에 권한 안내가 보인다. 권한을 다시 켜야 `이어 하기`가 된다(기존 재개 준비 검사).

## Desktop 상세 동작

### Swift 도우미 (`native/observer/main.swift` → `build/native/focuson-observer`)

- Main이 세션 관측이나 앱 목록이 필요할 때 한 번 띄워 두고 표준 입출력으로 JSON 한 줄씩 주고받는다. 요청마다 `id`를 붙여 응답을 맞춘다.
- `frontmost`: `NSWorkspace.frontmostApplication`의 번들 ID·이름·pid, `CGWindowListCopyWindowInfo`에서 그 pid의 맨 앞 창(layer 0) 제목. 창 제목은 화면 기록 권한이 없으면 `null`이다.
- `apps`: `/Applications`, `/Applications/Utilities`, `/System/Applications`, `~/Applications`의 `.app` 번들 ID·이름·경로와, 지금 실행 중인 일반 앱의 번들 ID.
- 도우미가 죽으면 다음 요청 때 다시 띄운다. 응답이 1초 안에 없으면 그 관측은 "대상 앱 확인 불가"로 본다.
- 빌드: `pnpm build:electron`이 `swiftc -O -target <arch>-apple-macos14.0`로 함께 빌드한다(지원 macOS 14 이상, 결정 024).

### 관측 상태 기계 (`electron/main/screen-observer.cts`, 순수 로직)

- 입력: 1초마다의 맨 앞 앱 `{ bundleId, appName, pid, windowTitle }` 또는 확인 불가.
- 화면 key = `bundleId + windowTitle`. key가 바뀌면 그 시각부터 다시 센다. 15초 그대로면 그 화면의 관측을 한 번만 만든다.
- 판정 순서(관측 전 Privacy Guard):
  1. Chrome 계열(`com.google.Chrome`, `.beta`, `.dev`, `.canary`) → 관측하지 않음(Extension 담당)
  2. FocusOn 자신(pid가 Main과 같음)·지원하지 않는 브라우저(Safari·Arc·Edge·Firefox·Whale·Brave·Opera·Vivaldi·Chromium·DuckDuckGo) → `guardStatus: EXCLUDED` (MVP는 macOS + Chrome만 지원, 직전 판정을 끊기 위해 보냄)
  3. 사용자 제외 앱 → `guardStatus: EXCLUDED` (메타데이터 없음)
  4. 고정 민감 앱 또는 창 제목 민감 단어 → `guardStatus: PRIVACY_BLOCKED` (메타데이터 없음)
  5. 그 외 → `contentSource: APP_META`, `metadata { appName, windowTitle }`
- 대상 앱을 확인할 수 없으면 관측을 만들지 않는다(이탈로 추정하지 않음, 화면 분석 3.1).
- 세션이 `RUNNING`이 아니면 관측하지 않고 상태를 비운다.
- 이번 단계에서는 관측 결과를 메모리에만 두고(마지막 1건) 서버로 보내지 않는다.

### 고정 민감 앱 (PRIVACY_BLOCKED, ponytail: 목록 기반, 놓친 앱은 사용자가 제외 앱으로 추가)

- 비밀번호·키체인: `com.apple.keychainaccess`, `com.apple.Passwords`, `com.1password.1password`, `com.agilebits.onepassword7`, `com.bitwarden.desktop`
- 메시지·메일: `com.apple.MobileSMS`, `com.kakao.KakaoTalkMac`, `com.tinyspeck.slackmacgap`, `com.hnc.Discord`, `ru.keepcoder.Telegram`, `net.whatsapp.WhatsApp`, `jp.naver.line.mac`, `com.apple.mail`, `com.microsoft.Outlook`
- 시스템 설정: `com.apple.systempreferences`
- 창 제목 단어(대소문자 무시): 비밀번호, password, passcode, OTP, 인증번호, 인증 코드, verification code, 결제, payment, 계좌, api key, access token, secret

### 화면 기록 권한 철회 (화면 분석 7.2)

- 세션이 `RUNNING`이면 10초마다 `systemPreferences.getMediaAccessStatus('screen')`을 확인한다. `granted`가 아니면:
  1. 로컬 타이머를 즉시 멈추고 `AUTO_PAUSED`로 표시한다(오류 code `LOCAL_SCREEN_PERMISSION_REVOKED`).
  2. `POST /sessions/{id}/local-events` `{ localEventCode: 'LOCAL_SCREEN_PERMISSION_REVOKED', occurredAt }`을 보낸다. 실패하면 메모리에 두고 다음 heartbeat 주기에 다시 보낸다.
- 재개는 기존 `resume` 준비 검사(`screenAnalysisReady`)를 그대로 쓴다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 화면 기록(창 제목 읽기). 맨 앞 앱 이름·번들 ID는 별도 권한 없음.
- 권한 요청 시점: 기존 온보딩 권한 단계. 이번 단계에서 새로 요청하지 않는다.
- 권한 거부 시: 창 제목이 `null`이라 앱 단위로만 구분한다. 세션 중 철회는 위 7.2 흐름.
- 활성 앱 또는 Bundle Identifier 처리: 제외·민감 판정은 번들 ID 기준이다. 앱 이름은 표시용이다.

## 화면 및 사용자 경험

- 화면 위치: 온보딩 `분석하지 않을 사이트가 있나요?` 단계(`/onboarding/sites/`) 아래에 앱 영역 추가. 집중 화면에는 `session-states.html`의 `권한 꺼짐` 상태(빨간 `자동으로 멈춤 · 마지막 확정 시간`, `화면 분석 권한이 꺼졌어요.`, `권한 확인`)를 넣는다. 말풍선 연출은 Floating Poki 단계에서 맞춘다.
- 주요 컴포넌트: `exclusions.html`의 `.app-add`(안내 + `앱 추가`. 온보딩 단계의 다른 `추가` 버튼과 같게 보조 버튼 스타일, 주 버튼은 하단 `다음`), 제외한 앱 목록(아이콘·이름·빼기), `제외할 앱 고르기` 대화상자(`.app-option`, 추가된 앱은 `추가됨`·비활성).
- 안내 문구(존댓말): `메시지·메일 앱은 처음부터 분석하지 않아요`(시안 문구), `여기 있는 앱에서는 화면 분석만 멈추고, 집중 타이머는 계속돼요.`
- 시안의 `(시안은 예시 목록)`과 예시 앱 목록은 넣지 않는다. 실제 설치된 앱만 보여 준다.
- 로딩·빈 상태·오류 상태: 목록을 읽는 중 `설치된 앱을 확인하고 있어요.`, 실패 시 `설치된 앱을 불러오지 못했어요. 다시 시도해 주세요.`, 저장 실패 시 단계를 넘기지 않고 오류를 보여 준다.

## 데이터 및 IPC/API

- Renderer 입력: 고른 앱 `{ bundleId, name }[]` (최대 50개).
- IPC(신규, `analysis:` 접두사): `analysis:installed-apps:list` → `{ apps: { bundleId, name, icon, running }[] }`, `analysis:excluded-apps:get` → `{ apps }`, `analysis:excluded-apps:save` `{ apps }` → `{ apps }`.
- Server·AI 요청 여부: `local-events`만. 제외 앱 목록은 서버로 보내지 않는다.
- 저장 데이터: `userData/excluded-apps.json` `{ apps: [{ bundleId, name }], updatedAt }`. 이 기기에만 있다.
- 분석 제외 대상: 사용자 제외 앱(로컬), 제외 도메인(서버 정본, Chrome은 Extension 담당).

```json
{ "contentSource": "APP_META", "observedAt": "2026-10-25T10:00:15Z", "metadata": { "appName": "Xcode", "windowTitle": "FocusOn — main.swift" } }
{ "guardStatus": "EXCLUDED", "observedAt": "2026-10-25T10:05:15Z" }
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 세션 중 화면 기록 권한 철회 | 집중 화면 자동 일시정지 + 권한 안내 | 로컬 타이머 정지, `local-events` 전송(실패 시 다음 주기 재전송) |
| 도우미 실행 실패·응답 없음 | 없음(세션 유지) | 관측을 만들지 않음. 다음 요청 때 다시 띄움 |
| 분석 제외 앱 | 없음 | `guardStatus: EXCLUDED`만, 메타데이터 없음 |
| 민감 앱·창 제목 | 없음 | `guardStatus: PRIVACY_BLOCKED`만, 메타데이터 없음 |
| Chrome이 맨 앞 | 없음 | 관측하지 않음(Extension 담당) |
| 지원하지 않는 브라우저·FocusOn 창이 맨 앞 | 없음 (안내는 Floating Poki 단계 `분석 제외` 상태) | `guardStatus: EXCLUDED`만. 이 브라우저에서의 이탈은 감지하지 못함(MVP 제한) |
| 설치된 앱 목록 실패 | 다시 시도 안내 | 제외 앱 저장은 이전 값 유지 |

## 보안·개인정보

- 카메라 원본 저장 여부: 해당 없음.
- 화면 원본 저장 여부: 이번 단계는 캡처하지 않는다.
- 메모리에서 데이터가 유지되는 시간: 마지막 관측 1건만, 세션이 `RUNNING`이 아니면 비운다. 창 제목을 로그에 남기지 않는다.
- Server로 전송하는 최소 데이터: 이번 단계는 `local-events`(이벤트 code·시각)만.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현 (도우미·관측 상태 기계·제외 앱 선택·권한 철회 처리)
- [x] 권한 거부 흐름 구현 (창 제목 없음, 세션 중 철회)
- [x] 예외 상황 확인 (도우미 실패, 제외·민감·Chrome·FocusOn)
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [x] 테스트 완료 (관측 상태 기계·제외 앱 저장·권한 철회 단위 테스트, 실제 Mac에서 도우미 확인) (단위·가짜 bridge 화면 확인, 실제 API QA는 백엔드 병합 뒤)
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (screen-observation-dialog-margin-preflight)
- [ ] `*-REPORT.md` 작성 (백엔드 `local-events` 병합 뒤 실제 QA 포함)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신

## 남은 작업

- 배포본에 도우미를 넣는 서명·공증(electron-builder `extraResources`·hardened runtime)은 패키징 작업 때 처리한다.
- 도우미는 빌드하는 Mac의 CPU 구조로 만든다. 배포 Build는 arm64·x64를 각각 만들므로 각 Build에서 같은 스크립트를 쓴다.
