# Chrome Extension 기능 기획서

## 기본 정보

- 기능명: 모듈 연동 로거와 로그 복사
- 기능 ID: E-LOG
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 작성자: jae1
- 작성일: 2026-10-29
- 우선순위: MVP - 핵심
- 관련 Issue: #105
- 예정 Branch: `feat/105-extension-logger`
- 관련 Notion·Figma: [모듈 연동 로깅 규칙](https://app.notion.com/p/4070b6e5e64c8156abc9d1bff00aef88), 루트 `docs/INTEGRATION_DEBUG.md`, Desktop 결정 030, `FocusOn-design/extension-popup.html`(`.p-foot`·`.text-btn`)

## 기능 목적

- 사용자의 문제: 연동 중 분석 요청이 실패해도 service worker가 꺼지면 콘솔 기록이 사라져 어디서 끊겼는지 알 수 없다.
- Extension에서 제공할 가치: Desktop과 같은 형식·traceId로 요청을 기록하고, 팝업 버튼 하나로 기록을 복사해 공유할 수 있다.

## 사용자 흐름

1. 평소에는 화면 변화가 없다. 서버 요청과 service worker 재시작이 기록된다.
2. 문제가 생기면 팝업 하단 `로그 복사`를 누른다.
3. 최근 기록(최대 500건)이 JSON으로 클립보드에 들어가고 `로그를 복사했어요`가 보인다. 디스코드·Issue에 붙여 넣는다.

## 수집 범위

- 수집할 값: 요청 경로(Query 제외)·method·status·걸린 시간·traceId·`contentSource`·`guardStatus`·판정 결과 `relation`·도메인·제목과 본문의 **글자 수**
- 수집하지 않을 값: 페이지 제목·본문 원문, 전체 URL, 토큰, 이메일·이름
- 수집 시작 조건: Extension 설치 뒤 모든 서버 요청
- 수집 중지 조건: 없음. 최근 500건만 남는다
- 분석 제외 도메인 처리: 제외 사이트는 `guardStatus`만 남는다(도메인 이름도 보내지 않는 요청은 로그에도 없다)

## Extension 상세 동작

- Popup 동작: 하단 `p-foot`에 `로그 복사` 텍스트 버튼. 성공하면 `로그를 복사했어요`, 실패하면 `로그를 복사하지 못했어요` 안내를 버튼 옆에 잠깐 보여 준다.
- Content Script 동작: 변경 없음
- Service Worker 동작: 시작할 때 `sw.restart`. 서버 요청은 `api.*` / `decision.*` OUT·IN·fail.
- 페이지 이동 처리: 변경 없음
- 15초 체류 재분석 처리: 변경 없음
- 상태 변화: 변경 없음
- 공통 요청(`api-client.ts`)에서 `X-Trace-Id`를 붙인다. 재시도·토큰 갱신 뒤 재전송은 같은 traceId, `analysis-runs`는 `clientRunId`를 쓴다.
- 저장: INFO 이상만 `chrome.storage.local`의 `focuson.logs`에 쌓고 500건을 넘으면 오래된 것부터 버린다. 같은 실행 환경 안에서는 쓰기를 순서대로 처리한다.

## Chrome 권한

- `permissions`: 추가 없음(`storage` 기존)
- `host_permissions` 또는 `optional_host_permissions`: 변경 없음
- 권한이 필요한 이유: 해당 없음
- 권한 거부 시: 해당 없음

## Desktop·Server·AI 연결

- 전달 대상: Server
- 전달 시점: 기존 요청과 같음
- 전달 데이터: `X-Trace-Id` 헤더(무작위 UUID) 하나 추가
- 연결 실패 시: `*.request.fail`과 `errorClass` 기록
- 재전송 필요 여부: 없음

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Extension 처리 |
| --- | --- | --- |
| 저장소 쓰기 실패 | 없음 | 그 줄만 건너뛴다 |
| 클립보드 권한·실패 | `로그를 복사하지 못했어요` | 기록은 그대로 둔다 |
| 기록이 없음 | `로그를 복사했어요` (빈 배열) | 그대로 복사 |

## 보안·개인정보

- 민감 정보 차단 방법: 허용 필드 목록만 남기고 `title`·`pageTitle`·`text`는 글자 수로 바꾼다(Desktop 로거와 같은 규칙).
- 원본 본문 저장 여부: 없음
- Server·AI 전송 데이터: `X-Trace-Id` 추가만
- 로그에서 제거할 값: 제목·본문 원문, URL 경로·Query, 토큰, 개인정보

## 완료 조건

- [x] 기획 내용 확인 (PM 확정 규칙)
- [x] 필요한 권한만 선언 (추가 없음)
- [x] 정상 흐름 구현
- [x] 제외 도메인 구현 (변경 없음)
- [x] 연결 실패 처리
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [x] 테스트 완료
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (`logger-bigint-target-ERROR.md`)
