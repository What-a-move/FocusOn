# Desktop 기능 결과 리포트

## 기본 정보

- 기능명: 포키 알림 새 방식 연결 (전경 context · 전송 receipt)
- 기능 ID: -
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop (+ Extension 분석 요청)
- 작성자: jae1
- 작성일: 2026-11-13
- 관련 기획서: `intervention-context-PLAN.md`
- 관련 Issue·PR: #218
- 관련 Branch: `feat/218-intervention-context`

## 구현 결과

- 기획한 내용 중 구현한 범위: 전경 context PUT·순번 영속·재시도·lease 재등록, heartbeat·analysis-runs `observationContextId`, 알림 context·남은 시간 필터, journal/outbox receipt(SENT·NOT_SENT·UNKNOWN), Extension 분석 직전 담당 확인.
- 기획과 달라진 내용: 없음. dev의 카메라 분석(#222)과 합치면서 분석 요청 래퍼가 화면 판정 기록과 context 부착을 함께 한다.
- 구현하지 못한 내용: AI 분석이 연결되지 않아 서버가 실제 알림을 만들지 않으므로, 실제 서버 알림의 포키 표시·receipt는 단위 테스트로만 확인.

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `electron/main/observation-context.cts` | 전경 context 등록·순번 영속·lease 재등록 |
| `electron/main/intervention-watcher.cts` | context·TTL 필터, journal/outbox receipt |
| `electron/main/index.cts` | 1초 루프 context 갱신, heartbeat·분석에 context 부착 |
| `electron/main/session-runtime.cts` · `analysis-sender.cts` | heartbeat·analysis-runs 선택 필드 |
| `apps/extension/extension/background/index.ts` · `shared/observation-context.ts` | 이 기기가 담당일 때만 context 부착 |

## 사용자 흐름 테스트

- [x] 앱 실행 (로컬 Docker 서버 dev, 2026-11-13)
- [x] 정상 흐름: 세션 6·7에서 Desktop 분석 27건(세션 6 15건, 세션 7 12건) 모두 DESKTOP context, Extension 분석(TAB_META)에 EXTENSION·기기 3 context
- [x] 전경 전환: 앱·창이 바뀔 때마다 순번이 1씩 올라 등록(세션 7 순번 1~26), Chrome이면 EXTENSION
- [x] 일시정지·재개·종료: RUNNING이 아니면 context 비움(단위 테스트)
- [ ] 권한 거부: 해당 없음
- [ ] 분석 제외 앱: 제외 판정도 같은 context로 기록됨(별도 확인 없음)
- [x] 앱 재실행: 순번 이어 세기(단위 테스트)

## 테스트 환경

- macOS: 26.6.1 / Apple Silicon
- Electron: ^44.1.0
- 테스트 일시: 2026-11-13

## 오류 기록

- 코드 오류 없음. QA 중 환경 문제 3건:
  - 확장을 다시 올리면 기존 연결이 풀리고 사이트 접근 권한이 꺼진다 → 연결 코드 재입력·팝업에서 권한 허용.
  - osascript `open location`은 맨 앞 Chrome 프로필 창에 탭을 연다. FocusOn이 없는 프로필이면 Desktop은 EXTENSION으로 넘기지만 확장은 아무것도 보내지 않는다.
  - 확장 서비스 워커 개발자 도구를 열어 두면 `chrome.windows.getLastFocused().focused`가 false라 확장이 분석하지 않는다. 닫으면 정상.

## 성능 및 제한사항

- 네트워크 끊김 처리: context·receipt 모두 같은 nonce로 재시도(단위 테스트).
- Chrome이 앞에 있는데 확장이 포커스를 못 받는 경우(위 개발자 도구 등) 그 구간은 분석이 비지만 context는 EXTENSION이라 Desktop도 보내지 않는다.

## 다음 작업

- AI 분석 연결 뒤 실제 서버 알림으로 포키 표시·deliveries 기록·5분 cooldown QA.
