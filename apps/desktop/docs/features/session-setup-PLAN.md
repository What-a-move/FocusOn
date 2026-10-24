# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 세션 설정
- 기능 ID: D09
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-24
- 우선순위: MVP - 핵심
- 관련 Issue: #46
- 예정 Branch: `feat/46-desktop-session-setup`
- 관련 Notion·Figma: 로그인 → 학습 목표 설정 7·8장, `POST /api/v1/sessions`, `GET /api/v1/users/me/focus-settings`, 화면 분석 5.2(정책 v1), Figma 가이드 D09

## 기능 목적

- 사용자의 문제: 목표를 정한 뒤 얼마나, 어떤 강도로 도움을 받을지 한 화면에서 빨리 정하고 시작하고 싶다.
- Desktop에서 제공할 가치: 고른 강도로 포키가 어떻게 알려 주는지 미리 보고, 한 번에 세션을 시작한다.

## 사용자 흐름

1. 목표 설정 완료 화면 `집중 시간 정하기` → `/session-setup/` (저장한 목표는 `sessionStorage['focuson.confirmedGoal']`)
2. 목표 줄 확인(`수정` → `/goal/`), 목표가 없으면 목표부터 정하라고 안내
3. 집중 시간: `30분`·`1시간`·`2시간`·`직접`(5~300분, 5분 단위 버튼)
4. 포키가 알려주는 정도: `약하게`·`보통`(기본)·`적극적으로` → 오른쪽 미리보기 문구·포키·기준이 바뀜
5. 알림 방식: `화면 위 포키`·`macOS 알림`·`소리` 중 1개 이상
6. `집중 시작하기` → `POST /api/v1/sessions` 성공 → 집중 화면(다음 Issue, 그 전에는 안내)
7. 왼쪽 위 `홈으로`로 언제든 나간다.

## Desktop 상세 동작

Electron Process·Window·IPC·권한의 확정 기본값은 `ELECTRON_ARCHITECTURE.md`, `WINDOW_LIFECYCLE.md`, `IPC_CONTRACT.md`, `MACOS_PERMISSION_FLOW.md`를 따른다. 변경하려면 먼저 `DECISION_RECORD.md`를 갱신한다.

- 진입 조건: 로그인·온보딩 완료, 저장한 목표가 있음.
- 기본값: `GET /api/v1/users/me/focus-settings`의 `interventionLevel`·`notificationChannels`·`cameraAnalysisEnabled`. 실패하면 보통 + 포키·macOS 알림을 쓰고 카메라는 `확인하지 못했어요`로 둔다.
- 시작: Main이 `Idempotency-Key`(UUID)와 `X-Device-Id`를 붙여 `POST /api/v1/sessions`를 보낸다. Renderer는 같은 선택으로 다시 누르면 같은 키를 쓰고, 선택을 바꾸면 새 키를 만든다. POST라 자동 재전송하지 않는다.
- 요청 본문: `{ goalId, targetDurationMs, timeZone, sessionOptions: { interventionLevel, notificationChannels, cameraAnalysisEnabled }, startReadiness: { screenAnalysisReady } }`. `timeZone`은 기기 IANA 시간대, `startReadiness`는 Main이 직접 확인한다(Renderer 입력 아님).
- 상태 변화: 세션 생성 성공 시 서버 `RUNNING`. 타이머는 다음 Issue(집중 화면)에서 시작한다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 화면 기록·알림(필수, 온보딩에서 준비). 이 화면은 새로 요청하지 않는다.
- 권한 거부 시: 서버가 `SESSION_START_REQUIREMENT_MISSING`(`SCREEN_PERMISSION`·`NOTIFICATION_PERMISSION`·`EXTENSION_LINK`)을 주면 무엇이 빠졌는지 안내한다.

## 화면 및 사용자 경험

- 화면 위치: `app/session-setup/page.tsx` → `features/session/SessionSetup.tsx`
- 디자인 원본: `FocusOn-design/session-setup.html`. 시간 선택지는 시안(25·50·90분·종료 없이)이 아니라 Notion 계약(30분·1시간·2시간·직접, MVP에 무기한 없음)을 따른다.
- 미리보기 기준 문구는 정책 v1을 따른다: 약하게 `3분 넘게 이어지거나 10분 안에 1분 넘게 3번`, 보통 `1분 넘게 이어지거나 10분 안에 30초 넘게 2번`, 적극적으로 `30초 넘게 이어지거나 10분 안에 20초 넘게 2번`.
- 문구: 포키 미리보기 말투는 강도별 UX 문구, 시스템 안내는 존댓말.

## 데이터 및 IPC/API

- Renderer 입력: 목표 id, 분, 강도, 알림 채널, Idempotency-Key
- Main 전달값: `window.focusOn.sessions.getFocusSettings()`, `sessions.start({ goalId, targetDurationMs, timeZone, sessionOptions, idempotencyKey })`
- 저장 데이터: 시작한 세션 `{ sessionId, goalText, targetDurationMs, startedAt }`를 집중 화면용으로 `sessionStorage`에 둔다.

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 목표 없음 | `먼저 목표를 정해 주세요.` + 목표 정하기 | 시작 막음 |
| 직접 입력 범위 밖 | `5~300분(5시간) 사이로 입력해 주세요` | 시작 막음 |
| 알림 방식 0개 | `알림 방식을 하나 이상 골라 주세요.` | 시작 막음 |
| 필수 준비 누락 | 빠진 항목(화면 기록·알림·Chrome 연결)과 다시 확인 안내 | 시작 실패 |
| 이미 진행 중인 세션 | `진행 중인 집중이 있어요.` | 시작 실패 |
| 네트워크·서버 오류 | `집중을 시작하지 못했어요.` + 다시 시도 | 같은 Idempotency-Key로 재시도 |

## 보안·개인정보

- 카메라·화면 원본: 이 화면은 다루지 않는다. 카메라 사용 여부만 표시한다.
- Server로 전송하는 최소 데이터: 목표 id, 시간, 강도, 알림 채널, 카메라 사용 여부, 시간대, 화면 기록 준비 여부.

## 완료 조건

- [ ] 기획 내용 확인
- [ ] 정상 흐름 구현
- [ ] 권한 거부 흐름 구현 (시작 실패 안내)
- [ ] 예외 상황 확인
- [ ] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [ ] 테스트 완료
- [ ] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성
- [ ] `*-REPORT.md` 작성
- [ ] `CONTEXT.md`, `NEXT_TASK.md` 갱신
