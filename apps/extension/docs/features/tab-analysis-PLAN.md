# Extension 기능 기획서

> Chrome Extension 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 기능 ID: E01b 사이트 접근, 화면 분석 3.1·3.2·6, AI 분석 계약 4.1·8
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 작성자: jae1
- 작성일: 2026-10-26
- 우선순위: MVP - 핵심
- 관련 Issue: #61
- 예정 Branch: `feat/61-extension-tab-analysis`
- 관련 Notion·Figma: 화면 분석, AI 분석 계약 4.1·4.4·8, Chrome·macOS 권한 정책 2, API `POST /sessions/{id}/analysis-runs`·`GET /users/me/analysis-excluded-domains`·`POST /sessions/{id}/local-events`, 공통 Error Code(`LOCAL_EXTENSION_HOST_PERMISSION_MISSING`), 결정 013·014
- 디자인 원본: `FocusOn-design/extension-popup.html` E01 연결 완료, E01b 사이트 접근(허용 안내·허용 거부), E06 사이트 접근 꺼짐

## 기능 목적

- 사용자의 문제: 공부를 가장 많이 하는 Chrome에서 지금 보는 페이지가 목표와 맞는지 알아야 이탈 알림이 의미 있다.
- Extension에서 제공할 가치: 집중 중 지금 보는 탭 하나만, 필요한 만큼만 읽어 서버로 보낸다.

## 사용자 흐름

1. 연결 코드로 연결하면 `연결됐어요!` → `다음` → E01b `사이트 접근을 허용해 주세요` → `사이트 접근 허용하기`(Chrome 권한 창).
2. 거부하면 E01b 허용 거부(`허용하기 전에는 집중을 시작할 수 없어요`). 허용하면 세션 화면(E02).
3. 집중 중 탭을 15초 이상 보면 Extension이 탭 제목·도메인을 보낸다. 결과가 관련 없음·판단 보류면 페이지 본문 일부를 다시 보낸다.
4. 집중 중 사이트 접근 권한을 끄면 서버에 알리고(세션 자동 일시정지), 팝업은 E06 `사이트 접근 권한이 꺼졌어요`를 보여 준다.

## 수집 범위

- 수집할 값: 현재 활성 탭의 제목·도메인(host), 필요할 때 본문 최소 텍스트(제목·h1~h3·본문 문단·코드 블록 제목, 최대 2,000자).
- 수집하지 않을 값: URL 경로·query·hash, 입력칸·textarea·contenteditable 값, 다른 탭·시크릿 창, 화면 이미지.
- 수집 시작 조건: 서버 세션이 `RUNNING`이고, Chrome 창이 맨 앞이며, 같은 탭·주소가 15초 유지됨.
- 수집 중지 조건: 탭 전환·주소 이동·Chrome이 뒤로 감·세션이 `RUNNING` 아님.
- 분석 제외 도메인 처리: 서버 목록(5분 캐시)과 host·하위 도메인 비교, 맞으면 `guardStatus: EXCLUDED`만. 목록을 한 번도 받지 못했으면 내용 없이 `EXCLUDED`(확인 불가 시 수집 보류).

## Extension 상세 동작

- **15초 타이머(결정 014, 결정 007 변경):** Service Worker가 `setTimeout`으로 15초를 센다. 시작 정보 `{ tabId, url, navigationId, startedAt }`를 `chrome.storage.session`에 두고, Worker가 꺼졌다 깨어나면(30초 `chrome.alarms` 보조) 저장된 시작 시각으로 이어서 판단한다. Content Script를 상시 넣지 않는다.
- **이벤트:** `tabs.onActivated`, `webNavigation.onCommitted`·`onHistoryStateUpdated`(최상위 frame, SPA 이동 포함), `windows.onFocusChanged`(Chrome이 뒤로 가면 중지). 같은 탭·주소면 다시 시작하지 않는다.
- **15초 뒤 판정 순서:** 현재 탭·주소가 같은지, 세션이 `RUNNING`인지 다시 확인 → Chrome 내부·지원 안 하는 페이지 `EXCLUDED` → 제외 도메인 `EXCLUDED` → 메일·메신저 도메인 `PRIVACY_BLOCKED` → 페이지에 비밀번호·일회용 코드·카드 입력칸 `PRIVACY_BLOCKED` → `TAB_META { pageTitle, urlHost }` 전송.
- **본문으로 다시 보내기(AI 분석 계약 4.1, 2026-10-26):** `TAB_META` 결과가 `UNRELATED`·`UNCERTAIN`이고 아직 같은 화면이면 `scripting.executeScript`로 본문을 읽어 `CHROME_DOM { metadata, contentPayload: { text } }`로 보낸다. 이메일·전화번호·긴 토큰은 지우고, 민감 단어가 있으면 버리고 `PRIVACY_BLOCKED`. 읽지 못하면 보내지 않는다(서버는 메타 근거 UNRELATED를 이탈로 세지 않음).
- **늦은 응답:** 응답이 올 때 이미 다른 화면이면 버리고 다음 단계도 보내지 않는다.
- **전송:** `clientRunId`(UUID v4), `contentSource`별 `sequence`(세션별 1부터, `chrome.storage.local`에 두어 브라우저를 다시 켜도 이어 셈), `Authorization`·`X-Device-Id`. 다시 보내거나 쌓지 않고 429면 60초 쉰다.
- **권한 철회:** `permissions.onRemoved`에서 사이트 접근이 빠지고 세션이 `RUNNING`이면 `local-events { localEventCode: LOCAL_EXTENSION_HOST_PERMISSION_MISSING }`.
- 상태 변화: `IDLE → TRACKING(15초) → ANALYZING → (META → DOM)`

## Chrome 권한

- `permissions`: `tabs`, `storage`, `webNavigation`, `scripting`, `alarms`
- `optional_host_permissions`: `https://*/*`, `http://*/*` (E01b에서 사용자 동작으로 요청)
- `host_permissions`: FocusOn 서버 origin 하나만(빌드 때 `NEXT_PUBLIC_FOCUSON_API_BASE_URL`에서 넣음). 필수 사이트 권한을 없애면 서버 호출이 CORS로 막히기 때문이다.
- `content_scripts` 정적 등록 제거, `minimum_chrome_version: 120`
- 권한 거부 시: 연결은 유지, 분석 불가, E01b 허용 거부 안내(세션 시작 차단은 서버·Desktop 기준).

## Desktop·Server·AI 연결

- 전달 대상: Server `POST /api/v1/sessions/{id}/analysis-runs`, `GET /api/v1/users/me/analysis-excluded-domains`, `GET /api/v1/sessions/active`, `POST /api/v1/sessions/{id}/local-events`
- 연결 실패 시: 보내지 않고 버림. 새 화면부터 다시.

```json
{ "clientRunId": "uuid", "contentSource": "TAB_META", "observedAt": "2026-10-26T10:00:15Z", "sequence": 3, "guardStatus": null, "metadata": { "appName": "Google Chrome", "pageTitle": "Important Defaults - TanStack Query", "urlHost": "tanstack.com" } }
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Extension 처리 |
| --- | --- | --- |
| 사이트 접근 거부 | E01b 허용 거부 | 분석하지 않음 |
| 집중 중 사이트 접근 꺼짐 | E06 사이트 접근 꺼짐 | `local-events` 전송 |
| Chrome 내부 페이지·새 탭 | 없음 | `EXCLUDED`만 |
| 제외 도메인 | 없음 | `EXCLUDED`만 |
| 민감 페이지 | 없음 | `PRIVACY_BLOCKED`만 |
| 서버·네트워크 오류 | 없음 | 버림 |

## 남은 작업

- 같은 페이지 안 내용 변경(스크롤 등) 30초 재전송은 이번에 하지 않는다(ponytail: 주소·제목이 바뀔 때만). 필요하면 본문 hash 비교로 추가한다.
- 실제 Chrome에서 권한 창·Worker 종료 복구 QA, 백엔드 API 병합 뒤 실제 왕복 QA와 REPORT.
