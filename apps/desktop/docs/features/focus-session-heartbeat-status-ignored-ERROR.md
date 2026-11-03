# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: focus-session-005
- 오류 식별명: heartbeat-status-ignored
- 기능명: 집중 화면 (D10·D11·D15·D16)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 높음 (서버가 세션을 멈춰도 Desktop 타이머가 계속 흘러 시간이 잘못 쌓임)
- 상태: 해결
- 관련 Issue·PR: #48, 화면 위 포키 F07 작업 중 발견

## 오류 제목

- heartbeat 응답이 `AUTO_PAUSED`여도 Desktop은 `RUNNING`으로 계속 셈

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 개발 실행
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. 집중을 시작한다.
2. 서버가 확장 연결 끊김 등으로 세션을 `AUTO_PAUSED`로 바꾸고, 다음 heartbeat 응답에 `status: AUTO_PAUSED`를 준다(서버 heartbeat가 아직 없어 QA 중계 서버로 재현).

## 기대 결과

- Desktop도 서버가 확정한 누적 시간으로 멈춘다(Notion `Chrome · macOS 권한 정책` 2.4: Extension 연결 상실 시 `AUTO_PAUSED`·타이머 중단).

## 실제 결과

- 수정 전 코드에서 서버가 25초에 `AUTO_PAUSED`라고 답했는데 Desktop은 `RUNNING`으로 60초까지 셈.

## 오류 메시지

```text
수정 전: status=RUNNING accumulatedActiveMs=60000 (heartbeat 응답 { status: AUTO_PAUSED, accumulatedActiveMs: 25000 })
```

## 원인 및 해결

- 추정 원인: heartbeat 응답 형식 검사(`parseHeartbeat`)가 `AUTO_PAUSED`를 거른다.
- 실제 원인: 추정과 달랐다(형식 검사는 통과). `SessionRuntime.heartbeat()`가 응답을 `SESSION_ABANDONED` 오류만 보고, 성공 응답의 `status`는 읽지 않았다. 서버 heartbeat가 아직 없어 드러나지 않았다.
- 해결 방법: Desktop이 `RUNNING`이고 명령 처리 중이 아닐 때 heartbeat가 `AUTO_PAUSED`·`PAUSED`를 주면 서버 누적 시간으로 멈추고 멈춘 이유(`pauseReason`, 없으면 null)를 남긴다. 집중 화면은 이유대로 안내하고 모르면 "자동으로 멈췄어요"라고만 한다. 확장 쪽 이유면 화면 위 포키 F07로 알린다.
- 변경 파일: `electron/main/session-runtime.cts`, `electron/shared/session-contract.cts`, `features/session/FocusSession.tsx`, `tests/electron/session-runtime.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (실제 앱 + 중계 서버: 멈춘 뒤 6초 간격 모두 28:55)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다. (재실행 복구는 "앱을 다시 열어 멈춰 있어요")
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron` 161개, 새 테스트 2개)

## 재발 방지 및 남은 작업

- 서버 응답의 상태 값을 받으면 반영하는지까지 테스트한다(받기만 하고 버리는 필드가 없게).
- 서버 heartbeat 응답에 `pauseReason`을 넣어 달라고 Backend에 요청(2026-11-03).
