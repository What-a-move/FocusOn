# Chrome Extension 아키텍처

> 현재 상태: Manifest V3, Popup 정적 Build, Service Worker·Content Script 진입 파일만 존재한다. 실제 탭 수집과 Message 처리는 아직 구현하지 않았다.
> 결정 상태: 자동 추적 범위, 저장 위치, 15초 체류 처리, Message 계약과 최소 Chrome Version은 확정했다. 전체 Host 권한을 필수 또는 선택 권한으로 요청할지는 검토 중이다.

## 목적

Popup, Service Worker, Content Script의 책임을 분리해 Page 데이터와 Extension 권한이 불필요하게 섞이지 않도록 한다.

## 구조

```text
사용자
  → Popup UI
      ↕ Message
Service Worker
  ↕ Message / Chrome API
Content Script
  ↕ 최소 허용 범위
Web Page

Service Worker
  ↕ Axios
FocusOn Server
```

| 영역 | 담당 | 금지 |
| --- | --- | --- |
| Popup | 세션·연결 UI, 사용자 명령 | 타이머 기준 확정, Page 본문 직접 접근 |
| Service Worker | 탭 Event, Message 조정, Server 전달, 상태 복구 | 장기 상태를 전역 변수에만 저장 |
| Content Script | 허용된 Page 정보 수집 | Token 접근, 제외 Domain 전송 |
| Server | 인증과 학습 세션 기준 | Chrome UI 상태 관리 |

## 자동 추적 범위

- Server에서 활성 학습 세션이 확인된 동안만 현재 활성 Tab을 자동 추적한다.
- Background Tab, Incognito Window, Chrome 내부 Page, 다른 Extension Page는 기본 분석하지 않는다.
- `tabs.onActivated`는 사용자가 보고 있는 Tab 전환을 확인하고, `webNavigation.onCommitted`는 최상위 Frame의 실제 Page 이동을 확인한다.
- 같은 이동을 두 API가 모두 알리면 `tabId`와 Navigation ID로 중복을 제거한다.
- 학습 세션이 끝나거나 일시정지되면 Content Script의 체류 Timer와 Page 수집을 즉시 중지한다.

## 권장 파일 구조

```text
apps/extension/
├── app/                       Popup Renderer
├── features/                  Popup 기능
├── extension/
│   ├── background/            Service Worker
│   ├── content/               Content Script
│   └── shared/                Message·Storage Type
├── public/
│   └── manifest.json
└── docs/
```

현재 Build가 `public/service-worker.js`와 `public/content-script.js`를 복사하는 구조이므로 TypeScript Entry와 Build 방식은 구현 전에 확정한다.

## 데이터 흐름

```text
탭 활성화·이동
  → Service Worker가 Event 수신
  → 제외 Domain 확인
  → Content Script가 활성 체류 시간 15초 확인
  → Service Worker가 현재 Tab·Navigation·세션을 재검증
  → Content Script에 최소 정보 요청
  → 중복·민감 데이터 제거
  → Server에 분석 요청
  → Popup은 Server 세션과 최소 결과 표시
```

## 상태 원칙

- Popup이 닫혀도 학습 세션 기준 상태는 Server에 남는다.
- Service Worker가 종료되어도 현재 Navigation 식별값은 `chrome.storage.session`에서 복구한다.
- 제외 Domain은 `chrome.storage.local`에 저장하고 Extension의 신뢰된 Context에서만 접근한다.
- TanStack Query는 Popup의 Server 상태 UI에 사용하고 Service Worker 생존 수단으로 사용하지 않는다.
- Content Script는 Extension의 인증 Token과 전체 설정을 보관하지 않는다.

## 보안과 개인정보

- Page 본문은 명시된 기능 범위에서 최소한으로 수집한다.
- 제외 Domain에서는 본문 수집과 전송을 즉시 중지하고 Server에 제외 상태만 전달한다.
- Server를 통해 제외 상태를 받은 Desktop은 화면·OCR·AI·Camera 분석을 모두 중지한다.
- 비밀번호, 결제 정보, 개인 메시지를 기본 수집하지 않는다.
- 외부 Page에서 Extension 내부 Message를 호출할 수 있게 열지 않는다.
- Remote Hosted Code를 사용하지 않는다.

## 확정한 기준과 남은 항목

- 자동 추적: 활성 학습 세션 중 현재 활성 Tab만 추적
- 이동 감지: `tabs`와 `webNavigation`을 역할을 나눠 함께 사용
- 15초 체류: Content Script Timer와 Service Worker 재검증 조합
- 제외 Domain: `chrome.storage.local`
- 세션·연결 상태 기준: Server
- 지원 최소 Version: Chrome 120
- HTTP Page: 분석 지원. FocusOn Server 통신은 HTTPS만 사용
- 검토 필요: 전체 HTTP·HTTPS Host 권한을 필수 또는 Optional 권한으로 요청할지
- 검토 필요: TypeScript Service Worker·Content Script Build 방식
- 검토 필요: 로그인 Token 저장과 Desktop·Extension 연결 방식
- 검토 필요: Server 실시간 동기화 방식

## 공식 참고

- [Chrome Extension Manifest V3](https://developer.chrome.com/docs/extensions/develop/migrate/what-is-mv3)
- [Extension Service Worker Lifecycle](https://developer.chrome.com/docs/extensions/develop/concepts/service-workers/lifecycle)
- [Chrome tabs](https://developer.chrome.com/docs/extensions/reference/api/tabs)
- [Chrome webNavigation](https://developer.chrome.com/docs/extensions/reference/api/webNavigation)
