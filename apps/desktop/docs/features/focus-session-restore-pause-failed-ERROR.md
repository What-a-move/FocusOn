# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: session-qa-01
- 오류 식별명: restore-pause-failed
- 기능명: 집중 화면 — 앱 재실행 뒤 진행 중 세션 복구 (실제 서버 QA)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-01
- 작성자: jae1
- 심각도: 중간 (화면 시간과 실제 누적 시간이 어긋나고, 무엇이 실패했는지 알 수 없음)
- 상태: 해결
- 관련 Issue·PR: #125

## 오류 제목

- 앱을 다시 열어 집중 화면에 들어가면 아무것도 누르지 않았는데 빨간 `이 기능은 아직 준비 중이에요.`가 뜨고, 타이머는 계속 흐른다.

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 · 11.19.0
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음
- 서버: 로컬 Docker(dev, 백엔드 #124 — 세션 시작·활성 조회만 있음)

## 재현 방법

1. 목표를 정하고 집중을 시작한다(`POST /sessions` 201).
2. 앱을 완전히 끄고 다시 연다.
3. 주소로 `/session/`에 들어간다.

## 기대 결과

- 앱이 꺼져 있던 동안은 세지 않도록 세션이 멈춘 상태(`AUTO_PAUSED`)로 보이고, 사용자가 `이어 하기`로 다시 시작한다.

## 실제 결과

- 화면은 실행 중처럼 시간을 계속 센다. 아래에 무엇이 실패했는지 없이 `이 기능은 아직 준비 중이에요.`가 빨간색으로 뜬다.

## 오류 메시지

```text
desktop api.response.in GET /api/v1/sessions/active status=200
desktop state.transition from=null to=RUNNING trigger=sync
desktop api.request.fail POST /api/v1/sessions/1/pause status=404 code=NOT_FOUND
```

## 원인 및 해결

- 추정 원인: 복구가 서버 누적 시간을 잘못 읽었다.
- 실제 원인: 복구는 실행 중이던 세션을 `resumedAt` 없이(세지 않는 값으로) `RUNNING`에 두고 `APP_RESTART` 일시정지를 보낸다. 서버에 일시정지 API가 아직 없어 실패하면 Main은 `RUNNING`인 채 세지 않고, 화면은 `RUNNING`을 보고 시간을 계속 세서 둘이 어긋났다. 실패 code만 화면에 넘겨 자동 동작인지 알 수 없었다.
- 해결 방법: 서버에 알리지 못해도 로컬에서 먼저 `AUTO_PAUSED`로 바꾸고 `LOCAL_RESTART_PAUSE_UNSYNCED`를 남긴다. 화면은 `멈춘 상태를 서버에 알리지 못했어요. 연결을 확인한 뒤 ‘이어 하기’를 눌러 주세요.`를 보여 준다. 홈·사이드바·집중 화면이 동시에 복구를 물어도 서버 조회와 일시정지는 한 번만 보낸다.
- 변경 파일: `electron/main/session-runtime.cts`, `features/session/FocusSession.tsx`, `tests/electron/session-runtime.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (실제 앱 재실행 → `AUTO_PAUSED`, 30:00에서 멈춤, 안내 문구 확인)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron` 153개 통과, 복구 테스트 2개 추가)

## 재발 방지 및 남은 작업

- 서버에 `pause`·`resume`·`heartbeats`가 생기면 재실행 → 서버 `AUTO_PAUSED` 확정 → `이어 하기`까지 다시 확인한다. heartbeat가 없어 지금은 재실행 시 타이머가 서버 확정값(0)부터 다시 시작한다.
