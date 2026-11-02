# Extension 결정 기록

> Chrome Extension의 권한·수집·분석·Desktop 연결에 관한 합의를 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - Extension 규격

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

Chrome Extension은 Manifest V3를 기준으로 개발한다.

### 결정 이유

현재 Chrome 확장 기능의 표준 구조에 맞춰 Popup, Content Script, Service Worker를 분리하기 위해서다.

### 영향 범위

- `public/manifest.json`
- Content Script
- Service Worker
- Chrome 권한과 보안 정책

## 결정 002 - 분석 제외 도메인

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

사용자가 설정한 제외 도메인과 하위 도메인은 페이지 본문 수집과 분석 전송을 즉시 중지한다. Extension은 제외 상태만 Server에 전달하고, Desktop은 확인된 `EXCLUDED` 상태에서 화면·OCR·AI 콘텐츠 분석을 중지하고 타이머를 유지한다. 별도 카메라 동의와 권한이 유효하면 로컬 카메라 분석만 허용한다. 최신 제외 상태를 확인할 수 없거나 `PRIVACY_BLOCKED`이면 화면·카메라 분석을 모두 중지한다.

### 결정 이유

사용자가 원하지 않는 사이트의 정보가 분석 대상이 되는 것을 막기 위해서다.

### 확정 기준

- `example.com`을 제외하면 `www.example.com` 같은 하위 도메인도 제외한다.
- 제외 목록의 정본은 사용자별 Server `analysis_excluded_domains`다. Extension은 최신 정규화 host 목록만 `chrome.storage.local`에 캐시하고 Content Script에서 직접 접근하지 않는다. 변경은 `PUT /api/v1/users/me/analysis-excluded-domains`의 전체 교체 결과를 기준으로 반영한다.
- 제외 상태는 Server를 통해 Desktop에 전달한다.
- 원본 URL·본문 대신 정규화한 Domain과 제외 여부만 전달한다.
- Desktop이 Server에서 최신 제외 상태를 확인할 수 없으면 확인될 때까지 화면·OCR·AI 콘텐츠 분석과 카메라 분석을 모두 중지한다. 확인된 `EXCLUDED`에서는 별도 카메라 동의와 권한이 유효할 때 로컬 카메라 분석만 허용하며, `PRIVACY_BLOCKED`에서는 카메라 분석도 중지한다.

### 결정 003 - Desktop 학습 세션 조회·제어

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)·Server
- 대상 앱: Desktop·Extension
- 상태: 확정

#### 결정 내용

Extension은 Desktop에서 시작한 학습 세션을 조회해 타이머를 표시하고, 일시정지·재개·종료 명령을 Server에 요청한다. 세션 상태와 기준 시간은 Server가 관리하며 Extension은 독립적으로 시간을 확정하지 않는다.

> 2026-10-25 변경: MVP에서는 Extension이 조회·표시만 하고 일시정지·재개·종료 명령은 P1로 미룬다(결정 012).

#### 결정 이유

Popup이 닫히거나 Service Worker가 다시 시작되어도 Desktop과 Extension의 시간이 달라지지 않도록 하기 위해서다.

#### 후속 확인

- [x] 일회용 연결 코드 기반 Extension pairing 확정
- [x] 세션 상태 조회 주기와 실시간 동기화 방식 확정 (팝업이 열려 있는 동안 10초 조회, 결정 012)
- [x] MVP 팝업은 표시만 하고 일시정지·재개·종료는 Desktop에서 한다. 팝업 조작은 P1 (결정 012로 일부 변경)
- [x] 상태 명령 idempotency/version과 Heartbeat 예외 확정

### 결정 004 - Popup의 피드백 표시 범위

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

#### 결정 내용

집중 상태를 표현하는 두더지 마스코트의 주 표시 영역은 Desktop 화면 위에 떠 있는 부유형 창으로 둔다. Extension Popup은 타이머·세션 상태·연결 상태와 최소한의 분석 결과를 표시하며, Desktop의 부유형 마스코트와 중복되는 장식 요소는 기본 제공하지 않는다.

#### 결정 이유

Chrome Popup은 사용자가 다른 곳을 클릭하면 닫히므로 지속적인 피드백은 Desktop의 부유형 창이 담당하는 편이 안정적이다.

## 결정 005 - 활성 학습 세션 중 현재 Tab 자동 추적

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

Server에서 활성 학습 세션이 확인된 동안 사용자가 보고 있는 현재 활성 Tab을 자동 추적한다. `tabs.onActivated`는 Tab 전환, `webNavigation.onCommitted`는 최상위 Frame의 실제 Page 이동을 담당한다. Background Tab과 Incognito Window는 기본 분석하지 않는다.

### 결정 이유

사용자가 Page를 이동할 때마다 Extension을 누르지 않아도 학습 흐름을 이어서 분석하되, 학습 중이 아닌 탐색과 보이지 않는 Tab까지 과도하게 수집하지 않기 위해서다.

## 결정 006 - Extension 저장소와 Server 기준 상태

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

- 제외 Domain의 정본은 Server 사용자 설정이며, `chrome.storage.local`에는 빠른 차단·오프라인 복구용 최신 목록만 캐시한다. 동기화 실패 시 콘텐츠 수집을 시작하지 않는다.
- 현재 Navigation과 짧은 연결 Cache는 `chrome.storage.session`에 저장한다.
- 학습 세션과 Desktop 연결 상태의 최종 기준은 Server다.
- Page 본문과 인증 Token은 `chrome.storage.local` 또는 `sync`에 저장하지 않는다. 단, Extension Refresh Token은 결정 011에 따라 `chrome.storage.local`에 둔다(2026-10-22 변경).

### 결정 이유

제외 Domain처럼 기기에서 유지할 설정과 Worker 재시작 동안만 필요한 임시 상태를 분리하고, Desktop·Extension이 서로 다른 세션 상태를 가지는 문제를 막기 위해서다.

## 결정 007 - 15초 체류와 Message 계약

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 결정 014로 변경 (2026-10-26)

### 결정 내용

15초 체류 Timer는 Content Script에서 실행하고 Service Worker가 시작·취소·최종 검증을 담당한다. Worker는 Navigation ID를 `chrome.storage.session`에 기록하고 Timer 완료 Message를 받으면 현재 활성 Tab·URL·제외 상태·Server 세션을 다시 확인한다. Message 이름과 Payload는 `MESSAGE_CONTRACT.md`를 기준으로 사용한다.

### 결정 이유

Manifest V3 Service Worker는 유휴 상태에서 종료될 수 있으므로 Worker의 전역 Timer에 의존할 수 없다. Content Script Timer와 Worker 재검증을 함께 사용하면 Worker가 다시 시작되거나 Page가 이동한 경우에도 오래된 분석 요청을 막을 수 있다.

## 결정 008 - Chrome 120과 HTTP Page 지원

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

최소 지원 Version은 Chrome 120으로 정한다. 오래된 학습 사이트를 위해 HTTP Page 분석도 지원하지만 FocusOn Server와 인증 통신은 HTTPS만 사용한다.

### 결정 이유

Manifest V3 Service Worker와 `chrome.storage.session`을 일관된 기준으로 사용하면서 HTTP로만 제공되는 학습 자료도 분석하기 위해서다.

## 결정 009 - 전체 Host 권한 승인 방식

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정 (2026-10-25 결정 013, Notion Chrome·macOS 권한 정책 2.2). 전체 사이트를 한 번에 요청하고, 문구는 E01b·E06 시안을 따른다. Content Script 상시 주입 대신 결정 014의 일회성 주입을 쓴다.

### 결정 내용

`https://*/*`와 `http://*/*`는 `optional_host_permissions`에 선언하고, 연결 뒤 팝업 E01b에서 이유를 설명한 다음 사용자 클릭으로 한 번에 요청한다. 사이트별 권한 모드는 두지 않는다. 거부하면 E01b 허용 거부, 집중 중 철회하면 E06 사이트 접근 꺼짐을 보여 주고 `local-events`로 서버에 알린다. 페이지는 결정 014대로 필요할 때만 `scripting.executeScript`로 한 번 읽는다.

## 결정 010 - 제외 도메인 정본과 로컬 캐시

- 결정일: 2026-10-12
- 담당 영역: Frontend(`apps`)·Server
- 대상 앱: Chrome Extension·Desktop
- 상태: 확정 — Notion 사용자 설정 API와 DR-019 반영

### 결정 내용

사용자별 제외 도메인 목록의 정본은 Server `analysis_excluded_domains`다. Extension의 `chrome.storage.local`은 최신 정규화 host 목록의 캐시다. 목록 확인에 실패하면 화면·카메라 분석을 모두 보류한다. 확인된 `EXCLUDED`에서는 화면 분석을 중단하고 타이머를 유지한다. 별도 카메라 동의·권한이 유효하면 로컬 카메라 분석만 허용하며, `PRIVACY_BLOCKED`에서는 카메라도 중단한다.

## 새 결정 기록

다음 결정은 `결정 012`부터 추가한다.

### 결정 012

- 결정일:
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop / Extension / 공통
- 주제:
- 상태: 제안 / 확정 / 변경됨 / 폐기
- 결정 내용:
- 결정 이유:
- 고려한 대안:
- 영향받는 파일:
- 관련 Issue·PR:

## 결정 011 - Extension 인증 Token 저장 위치

- 결정일: 2026-10-22
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정 (사용자 결정)

### 결정 내용

- pairing 성공으로 받은 Extension 전용 Refresh Token은 `chrome.storage.local`에 저장한다. 브라우저를 다시 열어도 연결이 유지된다.
- Access Token과 만료 시각은 `chrome.storage.session`(메모리)에 두고, 저장 전에 `setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' })`로 Content Script 접근을 막는다.
- installationId는 비밀값이 아니므로 `chrome.storage.local`에 둔다.
- Desktop 토큰과 공유하지 않고 Content Script·페이지로 전달하지 않는다. 저장에 실패하면 연결 완료로 표시하지 않는다.
- Backend origin은 빌드 시 `NEXT_PUBLIC_FOCUSON_API_BASE_URL`로 넣고 HTTPS 또는 로컬 loopback HTTP만 허용한다.

### 결정 이유

메모리에만 두면 Chrome을 다시 열 때마다 연결 코드를 새로 받아야 한다. Refresh Token은 사용할 때마다 회전하고 재사용이 감지되면 family 전체가 폐기되므로(인증 정책), 프로필 폴더에 평문으로 남는 위험을 감수하고 연결 유지를 택했다. WebCrypto 키로 암호화하는 안은 같은 Extension 코드가 복호화할 수 있어 실제 보호 효과가 제한적이라 택하지 않았다.

### 영향받는 파일

- `apps/extension/features/pairing/extension-auth.ts`, `pairing-api.ts`, `PairingStep.tsx`
- Notion `인증 · 토큰 · 디바이스 정책`의 Extension 저장 영역 항목

## 결정 012 - MVP 팝업은 세션 표시만, 조작은 Desktop

- 결정일: 2026-10-25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension (Desktop 연동)
- 상태: 확정 (사용자 결정, Notion Figma 가이드 E03·세션 상태 전이 정책·API 명세 반영)

### 결정 내용

- MVP에서 Extension 팝업은 진행 중 세션의 목표·남은 시간·상태를 보여 주기만 한다. 일시정지·재개·연장·종료는 Desktop에서 한다. 팝업에는 `멈추기·끝내기는 데스크톱 앱에서 할 수 있어요`를 안내한다.
- 팝업은 열려 있는 동안 10초마다 `GET /api/v1/sessions/active`를 다시 읽고, `RUNNING`이면 받은 뒤 흐른 시간만 화면용으로 더한다. 시간 확정은 서버·Desktop이 한다.
- 결정 003의 "Extension이 일시정지·재개·종료 명령을 요청한다"는 P1로 미룬다.
- 시안의 `FocusOn 앱 열기`는 Extension에서 Desktop을 여는 방법이 없어 넣지 않는다.

### 결정 이유

Desktop Main이 monotonic 타이머와 10초 heartbeat를 맡는다. 팝업에서 상태를 바꾸면 Desktop은 다음 heartbeat 응답 전까지(최대 10초) 이를 모르고, 일시정지 본문의 누적 시간도 Desktop이 센 값과 어긋날 수 있다. 시간을 세는 쪽을 하나로 두어 기록을 정확하게 한다.

### 고려한 대안

- 팝업 조작 + Desktop이 heartbeat 응답의 상태가 다르면 최신 세션을 다시 조회해 맞추기: 최대 10초 지연과 시간 차이가 생겨 P1로 미룸.

### 영향받는 파일

- `apps/extension/features/session/`, `docs/features/session-popup-PLAN.md`
- Notion: Figma 화면 설계 가이드 E03, 세션 상태 전이 정책 6.3, pause·resume·extend·end API 비고

## 결정 013 - Chrome 화면 분석 수집 범위

- 결정일: 2026-10-25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension (Desktop 결정 024와 함께)
- 상태: 확정 (사용자 결정, Notion 화면 분석·AI 분석 계약·Chrome·macOS 권한 정책 반영). 백엔드·AI·PM에 통보함.

### 결정 내용

- 수집은 `TAB_META` → `CHROME_DOM` 순서다. `TAB_META` 결과가 `RELATED`면 확정하고, `UNRELATED`·`UNCERTAIN`이면 항상 DOM으로 다시 보내 그 결과로 확정한다(2026-10-26 변경, Desktop 결정 024와 같은 이유). DOM을 읽을 수 없으면 `UNCERTAIN`이다.
- `CHROME_VIEWPORT_OCR`(`captureVisibleTab()`)은 MVP에서 제외하고 P1로 미룬다. DOM으로 부족한 페이지는 `UNCERTAIN`이며 알림을 만들지 않는다.
- DOM은 제목·h1~h3·본문 주요 텍스트·코드 블록 제목만 최대 2,000자로 읽는다. `input`·`textarea`·`contenteditable`의 입력값은 읽지 않는다.
- 늦게 도착한 이전 AI 결과는 최신 상태를 덮지 않는다(2026-10-26). 관측마다 번호를 붙이고, 응답이 지금 화면의 관측이 아니면 화면에 반영하지 않으며 그 응답으로 다음 단계도 보내지 않는다. 예: 화면 A의 2차 요청 중 제외 대상으로 옮겨 `EXCLUDED`를 보냈다면, 늦게 온 A 결과는 버린다. 서버도 같은 기기에서는 `contentSource`와 관계없이 `observedAt`이 가장 늦은 관측을 현재 상태로 본다(AI 분석 계약 8).
- 탭이 바뀌고 15초 유지되면 보낸다(기존 15초 체류 흐름). 같은 페이지는 다시 보내지 않고, 내용 변경은 최대 30초에 한 번 보낸다.
  - (2026-10-26) 같은 페이지 안 내용 변경(스크롤 등) 재전송은 미루고 아직 구현하지 않았다. 지금은 주소·탭이 바뀔 때만 보낸다.
- 제외 도메인이면 `guardStatus: EXCLUDED`, 비밀번호·일회용 코드·카드 입력 칸이 있거나 메일 작성 URL·메신저 도메인이면 `guardStatus: PRIVACY_BLOCKED`만 보낸다. DOM 텍스트에 민감 패턴이 있으면 버리고 `PRIVACY_BLOCKED`로 보낸다.
- manifest를 Notion 권한 정책에 맞춘다: 필수 `host_permissions`·정적 `content_scripts`를 없애고 `optional_host_permissions` + `scripting`으로 Chrome 분석을 켤 때 요청한다. 권한 제거(`chrome.permissions.onRemoved`) 시 `local-events`를 보낸다.

### 결정 이유

Extension에는 기본 OCR이 없어 한국어 OCR 엔진을 따로 넣어야 하고, Desktop이 Chrome 창을 대신 찍으면 URL을 모른 채 제외·민감 페이지를 캡처할 수 있다. 판단 보류는 알림을 만들지 않아 MVP에서 빼도 잘못된 경고가 생기지 않는다.

## 결정 014 - 15초 체류는 Service Worker가 세고, 페이지는 필요할 때만 읽는다

- 결정일: 2026-10-26
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정 (사용자 결정, 결정 007 변경). Issue #61

### 결정 내용

- 15초 체류는 Service Worker의 `setTimeout`으로 센다. 시작 정보 `{ key(tabId+주소), navigationId, startedAt, done }`를 `chrome.storage.session`에 두고, Worker가 꺼졌다 깨어나면(30초 `chrome.alarms`·브라우저 시작) 저장된 시작 시각으로 이어서 판단한다. 판정 전에 현재 탭·주소와 서버 세션(`RUNNING`)을 다시 확인한다.
- Content Script를 상시 등록·주입하지 않는다. 15초가 지나고 제외·민감 사이트가 아닐 때만 `chrome.scripting.executeScript`로 `inspectPage`를 한 번 실행해 민감 입력칸 확인(`maxLength: 0`)과 본문 읽기(`maxLength: 2000`)를 한다. 그래서 `MESSAGE_CONTRACT.md`의 `DWELL_*`·`TAB_CONTEXT_*` Message는 쓰지 않는다.
- 이벤트: `tabs.onActivated`, `webNavigation.onCommitted`·`onHistoryStateUpdated`(최상위 frame), `windows.onFocusChanged`(Chrome이 뒤로 가면 추적 중지, 그 시간은 Desktop이 본다).
- 사이트 권한은 optional이라 서버 호출이 CORS로 막히지 않게 FocusOn 서버 origin 하나만 빌드 때 필수 `host_permissions`로 넣는다(`NEXT_PUBLIC_FOCUSON_API_BASE_URL`).
- Chrome 내부 페이지·새 탭·사이트 권한이 없는 페이지는 `EXCLUDED`, 제외 목록을 한 번도 받지 못했을 때도 `EXCLUDED`로 보낸다(아무것도 보내지 않으면 서버가 직전 판정을 이어 간다).

### 결정 이유

Worker 유휴 종료는 30초 뒤라 15초 타이머는 대부분 그대로 실행되고, 꺼지더라도 저장된 시작 시각과 알람으로 복구할 수 있다. 상시 Content Script는 제외·민감 사이트에도 들어가고 Message 6종이 필요했다. 필요한 순간에만 읽으면 개인정보 노출과 코드가 함께 줄어든다.

### 영향받는 파일

- `extension/background/`, `extension/content/inspect-page.ts`, `extension/shared/`, `public/manifest.json`, `scripts/prepare-extension-build.mjs`

## 결정 015 - 서버 계약 타입은 shared-types로 공유한다 (타입만)

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
- `extension/background/tab-analyzer.ts`, `features/session/session-model.ts`

## 결정 016 - Extension 로그는 Desktop과 같은 형식으로 chrome.storage.local에 최근 500건만 둔다

- 결정일: 2026-10-29 / 상태: 확정 (PM 확정 Notion 모듈 연동 로깅 규칙) / Issue #105.
- 공용 로거는 `features/session/logger.ts` 하나다. 허용 필드만 남기고 `title`·`pageTitle`·`text`는 글자 수로 바꾼다(Desktop 결정 030과 같은 규칙). shared-types는 타입만 두기로 해서(결정 015) 함수는 복사해 둔다.
- MV3 service worker는 쉬면 꺼져 콘솔 기록이 사라진다. INFO 이상은 `chrome.storage.local`의 `focuson.logs`에 최근 500건만 남긴다. 팝업 하단 `로그 복사`로 JSON을 클립보드에 넣어 공유한다.
- 공통 요청(`api-client.ts`)과 연결 코드 요청(`pairing-api.ts`)에서 `X-Trace-Id`를 붙이고 `api.*`(분석은 `decision.*`) OUT·IN·fail을 남긴다. 재시도·토큰 갱신 뒤 재전송은 같은 traceId, `analysis-runs`는 `clientRunId`를 쓴다.
- service worker가 새로 켜질 때마다 `sw.restart`를 남긴다. 세션 상태 전이는 Desktop이 기록하므로 Extension에는 두지 않는다.
- 팝업과 service worker가 동시에 쓰면 한쪽 줄이 빠질 수 있다. 같은 실행 환경 안에서만 순서대로 쓴다. 문제가 되면 service worker 한 곳에서만 저장한다.
- 영향받는 파일: `features/session/logger.ts`, `LogCopy.tsx`, `api-client.ts`, `session-api.ts`, `features/pairing/pairing-api.ts`, `PairingStep.tsx`, `SessionPopup.tsx`, `extension/background/index.ts`

