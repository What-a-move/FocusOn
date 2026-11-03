# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 집중 화면
- 기능 ID: D10 집중 중 · D11 일시정지 · D15 세션 종료 확인 · D16 세션 완료
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-25
- 우선순위: MVP - 핵심
- 관련 Issue: #48
- 예정 Branch: `feat/48-desktop-focus-session`
- 관련 Notion·Figma: 로그인 설계서 8장(monotonic timer), 세션 상태 전이 정책, FocusOn API 명세서 `학습 세션`, Figma 가이드 D10·D11·D15·D16

## 기능 목적

- 사용자의 문제: 집중 중에는 남은 시간과 목표만 보고 싶고, 멈추거나 끝낼 때 기록이 정확해야 한다.
- Desktop에서 제공할 가치: 시스템 시계와 상관없이 정확히 센 시간을 서버와 맞추고, 앱이 꺼져도 세션을 잃지 않는다.

## 사용자 흐름

1. 세션 설정 `집중 시작하기` 성공 → `/session/` (Main이 바로 타이머 시작)
2. 진행 링·남은 시간·목표·`멈추기`/`끝내기`. 왼쪽 위 `홈`으로 나가도 타이머는 Main에서 계속 센다.
3. `멈추기` → 일시정지(배경·링 회색, `이어 하기`) → `이어 하기`
4. `끝내기` → 확인 창(`계속하기`/`끝내기`) → 완료 화면
5. 목표 시간 도달 → 누적을 멈추고 확인 창(`더 공부하기`/`세션 종료`, Esc로 닫히지 않음) → `더 공부하기`면 10·25·50분·직접(5~300분) 연장
6. 앱 재실행 → 활성 세션 복구. 실행 중이었으면 서버 확정 시간으로 `APP_RESTART` 일시정지 후 `이어 하기`
7. 서버가 heartbeat 시간 초과로 끝낸 세션(ABANDONED)은 되살리지 않고 마지막 확인 시간까지 기록됐다고 안내

## Desktop 상세 동작

Electron Process·Window·IPC·권한의 확정 기본값은 `ELECTRON_ARCHITECTURE.md`, `WINDOW_LIFECYCLE.md`, `IPC_CONTRACT.md`, `MACOS_PERMISSION_FLOW.md`를 따른다. 변경하려면 먼저 `DECISION_RECORD.md`를 갱신한다.

- 타이머: Main `SessionRuntime`이 `performance.now()`(monotonic)로 `targetDurationMs`·`accumulatedActiveMs`를 센다. Renderer는 받은 값에 받은 뒤 흐른 시간만 더해 그린다.
- 주기 작업: 1초마다 목표 도달 확인, 10초마다 heartbeat(서버는 60초 무응답이면 후보, 3분이면 ABANDONED).
- 상태 명령: pause·resume·extend·end는 `Idempotency-Key`·`If-Match-Version`, 자동 재전송 없음, 같은 명령 재시도는 같은 키, `SESSION_VERSION_CONFLICT`면 `GET /sessions/{id}`로 최신값에 맞춤.
- 상태 변화: `RUNNING ⇄ PAUSED/AUTO_PAUSED → (목표 도달) AWAITING_END_CONFIRMATION → ENDED`, 서버 자동 종료 `ABANDONED`.

## macOS 권한 및 시스템 기능

- 재개 전 알림 확인 기록이 없으면 서버에 요청하지 않는다(결정 022). 화면 기록 상태는 Main이 확인해 `readiness.screenAnalysisReady`로 보낸다.

## 화면 및 사용자 경험

- 화면 위치: `app/session/page.tsx` → `features/session/FocusSession.tsx`, `focus-session.css`
- 디자인 원본: `FocusOn-design/session.html`. 시안의 `종료 없이`·미리보기 링크(`화면 위 포키 시안` 등)는 넣지 않는다.
- 완료 화면 `리포트 보기`는 리포트 Issue 전까지 `홈으로`로 둔다.

## 데이터 및 IPC/API

- `session:current:get`·`pause`·`resume`·`extend`·`end`, Event `session:changed` (`IPC_CONTRACT.md` 집중 세션 Channel)
- 확정(결정 023): 활성·상세 세션 응답 `{ sessionId, status, version, goalTextSnapshot, targetDurationMs, accumulatedActiveMs }`, 목표 도달은 heartbeat로 서버가 판단(별도 전이 API 없음), 재개 `{ readiness: { screenAnalysisReady } }`, heartbeat 10초(2026-10-25 정본 정책과 통일).

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 네트워크 오류 | `인터넷 연결을 확인한 뒤 다시 눌러 주세요.` | 상태 유지, 같은 키로 재시도 |
| 버전 충돌 | `다른 곳에서 상태가 바뀌어 최신 상태로 맞췄어요.` | 서버 최신 조회 |
| 알림 확인 없음 | 세션 설정에서 시험 알림 확인 안내 | 재개 요청 안 보냄 |
| 서버 자동 종료 | `집중이 자동으로 끝났어요` | 되살리지 않음 |
| 활성 세션 없음 | `진행 중인 집중이 없어요` + 홈으로 | — |

## 보안·개인정보

- 이 화면은 화면·카메라 원본을 다루지 않는다. 서버로는 세션 id, 누적 시간, 일시정지 사유, 연장 시간, 관측 시각만 보낸다.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현
- [x] 권한 거부 흐름 구현 (재개 시 알림 확인)
- [x] 예외 상황 확인
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [x] 테스트 완료 (단위·가짜 bridge 화면 확인, 실제 API QA는 백엔드 병합 뒤)
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (tailwind-ring-class, dialog-margin-preflight, render-impure-time)
- [ ] `*-REPORT.md` 작성
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
