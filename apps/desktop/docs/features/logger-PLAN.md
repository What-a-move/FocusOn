# Desktop 기능 기획서

## 기본 정보

- 기능명: 모듈 연동 로거
- 기능 ID: D-LOG
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-29
- 우선순위: MVP - 핵심
- 관련 Issue: #101
- 예정 Branch: `feat/101-desktop-logger`
- 관련 Notion·Figma: [모듈 연동 로깅 규칙](https://app.notion.com/p/4070b6e5e64c8156abc9d1bff00aef88), 루트 `docs/DATA_PRIVACY.md` 로그 기준, `docs/ARCHITECTURE.md` 로그·추적 원칙

## 기능 목적

- 사용자의 문제: 모듈을 합쳤을 때 판정이 안 뜨거나 알림이 이상해도 어느 구간에서 끊겼는지 증거가 없다. 지금 Desktop은 실패를 화면 문구로만 바꾸고 기록을 남기지 않는다.
- Desktop에서 제공할 가치: 서버 요청마다 같은 `traceId`를 서버·AI와 공유해 추적표 1·7·8번 구간을 기록한다. 상태 전이·절전 기록으로 "쉬는데 알림이 왔다" 같은 문제를 나중에 재구성할 수 있다.

## 사용자 흐름

1. 사용자는 평소처럼 앱을 쓴다. 화면 변화는 없다.
2. Main이 서버 요청·응답·실패, 세션 상태 전이, 알림, 절전 진입·복귀를 `userData/logs/focuson.log`에 JSON 한 줄씩 남긴다.
3. 문제가 생기면 개발자가 로그 파일에서 `traceId`로 검색해 서버 로그와 잇는다.

## Desktop 상세 동작

- 진입 조건: 앱 시작(`app.whenReady`) 때 로그 파일 위치를 정한다. 그 전 기록은 버린다.
- 정상 동작:
  - `log(level, event, fields)` 하나만 쓴다. `ts`(벽시계 ISO), `level`, `module: desktop`, `component`, `event`, `traceId`, `durMs`를 넣는다.
  - 허용 목록에 없는 필드는 버린다. `title`·`text`처럼 원문이 될 수 있는 필드는 글자 수(`titleLen`·`textLen`)로만 바꾼다. `path`는 Query를 지운다.
  - 1MB를 넘으면 `focuson.1.log`로 넘기고 그 이전 것은 지운다.
  - 서버 요청: `AuthApiClient.call`·`logout`에서 `api.request.out` / `api.response.in` / `api.request.fail`을 남긴다. `analysis-runs`는 `decision.*` 이름을 쓴다. 실패는 `errorClass`(`timeout`·`network`·`auth`·`rate_limit`·`bad_response`·`unknown`)로 나눈다.
  - `X-Trace-Id`: 요청을 시작하는 쪽에서 UUID v4로 만든다. `authorizedRequest`는 재시도·토큰 갱신 뒤 재전송에도 같은 값을 쓴다. `analysis-runs`는 `clientRunId`를 그대로 쓴다.
  - 상태 전이: `SessionRuntime`이 화면에 상태를 알릴 때 이전 상태와 다르면 `state.transition`(`from`·`to`·`trigger`·`heldMono`·`heldWall`·`sleepSuspected`)을 남긴다.
  - 알림: 포키·macOS 알림을 띄울 때 `notify.fire`(`interventionType`·`channels`).
  - 절전: Electron `powerMonitor`의 suspend·resume에서 `power.suspend` / `power.resume`(`heldMono`·`heldWall`·`sleepSuspected`). 절전 시간을 집중 시간에 넣을지는 정하지 않고 기록만 한다.
- 종료 조건: 앱 종료. 로그 파일은 남는다.
- 상태 변화: 세션 상태 자체는 바꾸지 않는다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 없음
- 권한 요청 시점: 해당 없음
- 권한 허용 시: 해당 없음
- 권한 거부 시: 해당 없음
- 활성 앱 또는 Bundle Identifier 처리: 로그에 남기지 않는다.

## 화면 및 사용자 경험

- 화면 위치: 없음 (Main 전용)
- 주요 컴포넌트: `electron/main/logger.cts`
- 알림 문구: 없음
- 로딩·빈 상태·오류 상태: 로그 파일을 못 쓰면 기록만 건너뛰고 앱 동작은 그대로 둔다.

## 데이터 및 IPC/API

- Renderer 입력: 없음
- Main 또는 Preload 전달값: 없음 (IPC 추가 없음)
- Server·AI 요청 여부: 모든 요청에 `X-Trace-Id` 헤더 추가 (`docs/API_CONTRACT.md` 요청 헤더)
- 저장 데이터: `userData/logs/focuson.log`, `focuson.1.log` (최대 약 2MB)
- 분석 제외 대상: 제외 앱 이름·bundleId는 기록하지 않는다.

```json
{"ts":"2026-10-29T03:03:11.482Z","level":"INFO","module":"desktop","component":"api","event":"decision.request.out","traceId":"c3d2e0a0-...","method":"POST","path":"/api/v1/sessions/901/analysis-runs","durMs":0}
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 로그 파일 쓰기 실패 | 없음 | 그 줄만 건너뛴다. 앱 동작에 영향 없음 |
| 서버가 `X-Trace-Id`를 아직 모름 | 없음 | 서버는 헤더를 무시하고, Desktop 로그에만 남는다 |
| 원문 필드를 실수로 넘김 | 없음 | 로거가 버리거나 글자 수로 바꾼다 |

## 보안·개인정보

- 카메라 원본 저장 여부: 없음
- 화면 원본 저장 여부: 없음. OCR·창 제목·탭 제목 원문은 글자 수로만 남긴다.
- 메모리에서 데이터가 유지되는 시간: 로그 한 줄을 쓰는 동안만
- Server로 전송하는 최소 데이터: `X-Trace-Id`(무작위 UUID) 하나 추가

## 완료 조건

- [x] 기획 내용 확인 (PM 확정 규칙)
- [x] 정상 흐름 구현
- [x] 권한 거부 흐름 구현 (해당 없음)
- [x] 예외 상황 확인
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인 (화면 없음)
- [x] 테스트 완료
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (기존 헤더 비교 테스트 2개가 실패했으나 의도한 계약 변경이라 기대값만 고쳤다. 제품 오류 없음)
- [ ] `*-REPORT.md` 작성 (서버 traceId 기록과 추적표 확인 뒤)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
