# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 화면 위 포키 이탈 알림 (화면 분석 4단계)
- 기능 ID: F04 이탈 안내(약하게·보통·적극), F05 휴식 제안
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-26
- 우선순위: MVP - 핵심
- 관련 Issue: #63
- 예정 Branch: `feat/63-desktop-floating-poki`
- 관련 Notion·Figma: 화면 분석 5.3·5.4, UX 문구·알림 문구 정책 2, Figma 가이드 Floating Poki(평소 표시 없음), API `GET /sessions/{id}/interventions`·`POST /interventions/{id}/acknowledgements`, 결정 024
- 디자인 원본: `FocusOn-design/floating-poki.html` (F04·F05)

## 기능 목적

- 사용자의 문제: 딴 데 보고 있는 걸 스스로 알아차리기 어렵다.
- Desktop에서 제공할 가치: 서버가 이탈이라고 판단하면 어떤 앱 위에서든 포키가 화면 아래에서 올라와 짧게 알려 준다.

## 사용자 흐름

1. 평소에는 아무것도 띄우지 않는다(시간도 보이지 않음).
2. 서버에 새 이탈 알림이 생기면 포키가 먼저 화면 오른쪽 아래에서 크게 고개를 내밀고, 이어서 포키 왼쪽에 카드가 떠 말한다(개입 강도별 문구). `확인했어`·`잠시 쉬기`.
3. `확인했어` → 확인 기록 → `지금 목표는 이거야.` + 목표 + `알겠어` → 닫힘(5분 재집중, 화면 상태만).
4. `잠시 쉬기` → 확인 기록 + 세션 일시정지 → 닫힘.
5. ✕나 포키를 누르면 닫힘(해제 기록). 판정·타이머는 바꾸지 않는다.

## Desktop 상세 동작

- `InterventionWatcher`(Main): 세션이 `RUNNING`이면 10초마다(heartbeat와 같은 주기) `GET /sessions/{id}/interventions`를 조회해 처음 보는 `PENDING` 알림 하나를 보여 준다.
- 채널: `MOLE_UI`면 포키 창, `MACOS_NOTIFICATION`면 macOS 알림, `SOUND`면 소리(macOS 알림 소리). 세션 시작 때 고른 값을 쓰고, 앱을 다시 켜 이어받은 세션은 `MOLE_UI`·`NORMAL`로 본다(ponytail: 세션 조회 응답에 옵션이 없어서).
- 포키 창(2026-11-08 사용자 확정): 테두리·배경 없는 투명 창(720×300)을 화면 오른쪽 아래 모서리에 붙인다. 포키가 주인공이라 고개 내민 포키(360px)가 먼저 아래에서 스르륵 올라와 표정으로 알리고, 0.6초 뒤 흰 카드(모서리 24px)가 포키 바로 왼쪽에 뜬다(튀지 않게). 카드에는 이름표(포키 / 오류는 FocusOn)·포키 말·설명·버튼(모서리 13px, 앱 버튼과 같은 아래 두께)이 들어간다. 카드는 모두 흰색이고 세기는 포키 표정과 말투로 구분한다. 연결 끊김만 빨간 선·글자(결정 031). 상황별 이미지 `poki-peek-*.png` 6장(noticed, long-distraction, caught-you, take-a-break, back-to-study, chrome-disconnected)은 몸 위치가 같아 얼굴만 바뀐다. 다른 앱·전체화면 위에 뜨고 포커스를 빼앗지 않는다(`showInactive`). 알림이 없으면 숨긴다. 창의 투명한 빈 영역이 그 아래 클릭을 막는 점은 후속 과제다.
- 확인 기록: `POST /interventions/{id}/acknowledgements { action: ACKNOWLEDGED | DISMISSED }` (`Authorization`·`X-Device-Id`). 실패해도 화면은 닫는다(다시 보내지 않음).
- 문구(포키 반말, UX 문구 정책 2.1, 2026-11-08 사용자 확정): 약하게 `어, 잠깐 샜네?`/`아까 하던 거 마저 할까?`, 보통 `어? 또 딴 데 보고 있네 👀`/`목표로 돌아가자!`, 적극 `어 얘 봐라? 또 놀고 있네 😏`/`이러다 오늘 목표 다 날아간다?`. 휴식 제안 `벌써 세 번째 흐트러졌어.`/`잠깐 쉬었다 갈까?`(`잠시 쉬기`·`계속할게`). Chrome 연결 끊김(F07)은 오류라 존댓말 유지.

## 데이터 및 IPC/API

- Event `floating:show` (Main → 포키 창) `{ interventionId, kind: 'OFF_TASK' | 'BREAK', level, goalText }`
- `intervention:act` (포키 창 → Main) `{ interventionId, action: 'RECENTER' | 'ACK' | 'REST' | 'CLOSE' }`
- 알림 목록 응답은 `{ interventionId, type, status, channels }` 배열(또는 `{ interventions: [] }`)로 읽는다. `type`은 `OFF_TASK`·`BREAK_SUGGESTION`만 표시한다(결정 035, 2026-11-02 Notion 반영).

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 조회 실패 | 없음 | 다음 10초에 다시 조회 |
| 확인 기록 실패 | 없음(창은 닫힘) | 다시 보내지 않음 |
| 세션이 `RUNNING`이 아님 | 포키 숨김 | 조회하지 않음 |

## 보안·개인정보

- 화면 원본·분석 근거를 말풍선에 넣지 않는다. 구체 활동(예: 게임)을 추측해 말하지 않는다.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현
- [x] 예외 상황 확인
- [x] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [x] 테스트 완료 (단위·가짜 bridge 화면 확인, 실제 API QA는 백엔드 병합 뒤)
- [ ] `*-REPORT.md` 작성 (백엔드 병합 뒤)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신

## 남은 작업

- 휴식 제안 포키(sleepy)는 에셋이 없어 기존 포키로 대신한다. F06(분석 제외·민감)·F07(연결 끊김) 말풍선, 위치 끌어 옮기기는 다음 작업이다.
