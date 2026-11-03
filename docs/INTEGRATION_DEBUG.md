# 모듈 연동 오류 추적 지침

Frontend(Desktop·Extension)·Backend·AI를 연결하다 오류가 나면 이 순서로 원인을 찾는다. 사람과 AI 코딩 도구 모두 같은 절차를 따른다. 로그 형식·event 이름·traceId 규칙의 정본은 Notion [모듈 연동 로깅 규칙](https://app.notion.com/p/4070b6e5e64c8156abc9d1bff00aef88)이다.

## 원칙

- **추측으로 코드를 고치지 않는다.** 먼저 로그로 어느 구간에서 끊겼는지 찾고, 그 구간의 코드만 본다.
- 로그는 사람을 가리키지 않는다. "누가 틀렸나"가 아니라 "몇 번 구간에서 끊겼나"로 말한다.
- 끊긴 구간이 다른 영역(예: Frontend 작업 중 Server)이면 그 영역 코드를 직접 고치지 않는다. 아래 장애 보고 양식으로 담당자에게 넘긴다.

## 1. 모듈별 로그 위치

| 모듈 | 위치 | 현재 상태 |
| --- | --- | --- |
| Desktop | `~/Library/Application Support/@focuson/desktop/logs/focuson.log` (1MB 넘으면 `focuson.1.log`). 개발 실행 중에는 터미널에도 같은 줄이 찍힌다 | Issue #101로 구현 |
| Extension | 팝업 "로그 복사" 버튼(예정). 그전에는 `chrome://extensions` → service worker 콘솔 | 로거 미구현 |
| Backend | `./gradlew bootRun` 터미널 출력, Docker면 `docker compose logs backend` (`server/` 폴더에서) | `requestId`만 기록. `X-Trace-Id` 기록은 서버 작업 예정 |
| AI | 실행 터미널 출력 | 로거 미구현 |

```bash
tail -n 50 ~/Library/Application\ Support/@focuson/desktop/logs/focuson.log
```

## 2. 실패한 요청의 traceId 찾기

1. 오류를 한 번 재현하고 시각을 적어 둔다.
2. Desktop 로그에서 실패 기록을 찾는다. 실패가 없으면 마지막 `*.request.out`을 본다.

```bash
grep -E '"(api|decision)\.request\.fail"' ~/Library/Application\ Support/@focuson/desktop/logs/focuson.log | tail -n 5
```

3. 그 줄의 `traceId`로 모든 모듈 로그를 검색한다. 같은 traceId가 Desktop·Extension 요청 → Server → AI → 다시 클라이언트까지 이어져야 한다.

```bash
grep '<traceId>' ~/Library/Application\ Support/@focuson/desktop/logs/focuson.log
```

- 분석 요청(`analysis-runs`)은 `traceId`가 요청 본문의 `clientRunId`와 같다.
- 서버 응답 헤더 `X-Trace-Id`와 오류 응답의 `requestId`도 같이 확인한다. `requestId`는 서버가 따로 만드는 ID다.

## 3. 추적표로 끊긴 구간 찾기

| # | 구간 | event | 영역 |
| --- | --- | --- | --- |
| 1 | 클라이언트 요청 생성 | `decision.request.out` / `api.request.out` | Desktop·Extension |
| 2 | Backend 수신 | `http.in` | Server |
| 3 | 규칙 게이트 통과 | `gate.rule.pass` / `gate.rule.block` | Server |
| 4 | AI 호출 시작 | `ai.call.start` | Server·AI |
| 5 | AI 응답 수신 | `ai.call.ok` / `ai.call.fail` | Server·AI |
| 6 | Backend 응답 반환 | `http.out` | Server |
| 7 | 클라이언트 응답 수신 | `decision.response.in` / `api.response.in` / `*.request.fail` | Desktop·Extension |
| 8 | 상태 전이 | `state.transition` | Desktop·Extension |

| 로그 모양 | 뜻 | 볼 곳 |
| --- | --- | --- |
| OUT 있음 / IN 없음 | 전송·네트워크·주소 문제. 받는 쪽 코드는 아직 의심하지 않는다 | `FOCUSON_API_BASE_URL`, 서버 실행 여부, 네트워크 |
| OUT·IN 있음 / 응답 없음 | 받는 쪽 내부에서 멈춤 | 마지막으로 찍힌 모듈 내부 |
| `validate.fail` | 계약 불일치(필드 이름·타입) | `docs/API_CONTRACT.md`, Notion API 명세와 실제 값 비교 |
| 전부 있는데 `durMs`가 큼 | 동작은 정상, 성능 문제 | 구간별 `durMs` 비교 |
| `*.request.fail`의 `errorClass` | `timeout`·`network`·`auth`·`rate_limit`·`bad_response`·`unknown` | `auth`는 토큰·권한, `bad_response`는 응답 형식(계약) |

로그가 없는 구간이 있으면 그 모듈의 로거가 아직 없는 것이다. 이때는 `console.log`를 임시로 넣지 말고, 그 모듈의 공용 로거로 정본 event 이름의 OUT·IN 기록을 먼저 추가한다.

## 4. 고친 뒤 확인

- 같은 재현을 다시 하고, 같은 방식으로 1~8번이 모두 찍히는지 본다.
- Frontend 기능 작업 중이면 오류마다 `기능명-오류명-ERROR.md`를 쓰고, **추정 원인과 로그로 확인한 실제 원인을 나눠** 적는다.

## 5. 장애 보고 양식

다른 영역으로 넘길 때 아래 다섯 줄로 쓴다. "누가"는 쓰지 않는다.

```text
[증상]      판정 결과가 화면에 표시되지 않음
[발생]      2026-11-03 14:03 / 재현 3회 중 3회
[traceId]   c3d2e0a0-...
[구간]      6번(http.out)까지 찍힘, 7번(decision.response.in) 없음
[기대/실제] 기대: 응답 받고 상태 갱신 / 실제: 아무 반응 없음
```

## 6. 로그를 다룰 때 금지 사항

- 로그 원문을 채팅·Issue·PR에 붙일 때 토큰·제목·본문·이메일이 없는지 먼저 확인한다. 로거가 거르지만 사람이 한 번 더 본다.
- 원인을 보려고 로거의 마스킹(허용 필드)을 풀거나 원문을 남기는 코드를 넣지 않는다. 필요한 값은 글자 수·결과 코드처럼 원문을 복원할 수 없는 형태로 추가한다(`docs/DATA_PRIVACY.md` 로그 기준).
- `traceId`를 인증·권한·중복 판단·DB 키로 쓰지 않는다.
- 디버깅용 `console.log`·`System.out`·`print`를 남기지 않는다.
