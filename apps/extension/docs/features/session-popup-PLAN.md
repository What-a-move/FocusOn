# Extension 기능 기획서

> Extension 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.

## 기본 정보

- 기능명: 팝업 세션 화면과 Access Token 갱신
- 기능 ID: E02 세션 없음 · E03 세션 진행 중 · E06 연결 오류
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 작성자: jae1
- 작성일: 2026-10-25
- 우선순위: MVP - 핵심
- 관련 Issue: #52
- 예정 Branch: `feat/52-extension-session-popup`
- 관련 Notion·Figma: `POST /api/v1/auth/refresh`, `GET /api/v1/sessions/active`, `DELETE /api/v1/extension-pairings/{extensionDeviceId}`, Figma 가이드 E02·E03·E06, `FocusOn-design/extension-popup.html`

## 기능 목적

- 사용자의 문제: 브라우저에서 공부하다가 지금 집중 상태와 남은 시간을 빨리 확인하고 싶다.
- Extension에서 제공할 가치: 팝업에서 Desktop이 진행 중인 세션을 같은 값으로 보여 주고, 연결이 끊기거나 해제되면 무엇을 해야 하는지 안내한다.

## 사용자 흐름

1. 툴바 아이콘 → 연결 전이면 연결 코드(E01, 기존), 연결됐으면 집중 상태
2. 세션 없음(E02): `지금은 집중 중이 아니에요`, `이 브라우저 연결 해제`
3. 세션 진행 중(E03): 목표, 남은 시간, 진행 막대. 일시정지·자동 일시정지·목표 시간 도달은 상태별 안내. 멈추기·끝내기는 Desktop 앱에서 한다.
4. 서버 연결 불가(E06): 마지막으로 확인한 남은 시간과 그 시각, `다시 연결`
5. 연결 해제·토큰 폐기: 로컬 토큰을 지우고 연결 코드 화면에 `연결이 해제됐어요` 안내

## 수집 범위

- 이 기능은 페이지 내용을 수집하지 않는다. 탭 추적·분석은 이후 Issue다.

## Extension 상세 동작

- Popup 동작: 열려 있는 동안 10초마다 `GET /api/v1/sessions/active`를 다시 읽고, `RUNNING`이면 받은 뒤 흐른 시간만 화면용으로 더한다(결정 003, 시간 확정은 서버·Desktop).
- 인증: Access Token 만료 30초 전이면 먼저 `POST /api/v1/auth/refresh`(single-flight, rotation 저장). 서버가 `AUTH_ACCESS_TOKEN_EXPIRED`면 한 번 갱신·한 번 재요청. `AUTH_RELOGIN_REQUIRED`·`AUTH_TOKEN_REUSE_DETECTED`·`LINK_TOKEN_REVOKED`면 로컬 토큰 삭제 후 연결 화면. GET은 네트워크·503·504에서 1초 → 3초 재시도, DELETE는 자동 재전송 없음.
- Service Worker·Content Script: 변경 없음.
- 상태 변화: `연결 전 → 세션 없음 ⇄ 세션 진행 중 → (오류) 연결 오류`, 해제 시 `연결 전`

## Chrome 권한

- 새 권한 없음(`storage` 사용).

## Desktop·Server·AI 연결

- 팝업은 표시만 한다. 일시정지·재개·종료는 Desktop이 맡는다(팀 확인 전 제안). 이유: Notion 상태 명령의 호출 주체가 Desktop이고, Desktop Main이 monotonic 타이머와 heartbeat를 맡아 팝업 명령은 Desktop과 어긋날 수 있다. 결정 003·Figma E03의 팝업 조작은 확인 뒤 반영한다.
- 시안의 `FocusOn 앱 열기`는 Desktop을 여는 방법(사용자 지정 URL 등)이 없어 넣지 않았다.

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Extension 처리 |
| --- | --- | --- |
| 서버 연결 불가 | `서버에 연결할 수 없어요` + 마지막 확인 시각 | GET 재시도 후 오류, `다시 연결` |
| 연결 해제·토큰 폐기 | `연결이 해제됐어요` | 로컬 토큰 삭제, 연결 코드 화면 |
| 연결 해제 실패 | `연결을 해제하지 못했어요` | 상태 유지 |
| Worker 재시작 | 해당 없음 | 팝업은 열릴 때마다 서버에서 다시 읽음 |

## 보안·개인정보

- Access Token은 `chrome.storage.session`(신뢰된 화면만), Refresh Token·deviceId는 `chrome.storage.local`(결정 011). 갱신 때 Refresh Token을 바로 새 값으로 바꾼다.
- 로그에 토큰을 남기지 않는다. 서버로는 토큰과 deviceId만 보낸다.

## 완료 조건

- [ ] 기획 내용 확인
- [ ] 필요한 권한만 선언 (변경 없음)
- [ ] 정상 흐름 구현
- [ ] 제외 도메인 구현 (해당 없음)
- [ ] 연결 실패 처리
- [ ] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [ ] 테스트 완료 (`pnpm --filter @focuson/extension test`)
- [ ] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성
- [ ] `*-REPORT.md` 작성
- [ ] `CONTEXT.md`, `NEXT_TASK.md` 갱신
