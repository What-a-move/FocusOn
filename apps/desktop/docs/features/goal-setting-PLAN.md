# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 목표 설정
- 기능 ID: D06 목표 만들기 · D07 AI 목표 확인
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-24
- 우선순위: MVP - 핵심
- 관련 Issue: #44
- 예정 Branch: `feat/44-desktop-goal-setting`
- 관련 Notion·Figma: 로그인 → 학습 목표 설정 6장, AI 분석 계약 3장, `POST /api/v1/goals/clarify`, `POST /api/v1/goals`, Figma 가이드 D06·D07

## 기능 목적

- 사용자의 문제: 목표가 넓거나 여러 개가 섞이면 세션 중 무엇이 관련 활동인지 판단하기 어렵다.
- Desktop에서 제공할 가치: 한 세션에 목표 하나를 짧은 문장으로 정하게 돕는다. AI는 판정만 하고, 장애 때도 사용자가 직접 정할 수 있다.

## 사용자 흐름

1. 홈 `목표 설정하기` → `/goal/`
2. 입력(2~40자, 예시 칩) → `다음` → AI 명확성 확인(`목표를 살펴보고 있어요`)
3. 결과에 따라
   - `CLEAR`: 확인 화면(`normalizedGoalText`, 없으면 입력 문구)
   - `AMBIGUOUS` + `suggestions`: 추천 2~3개 중 선택 → 확인 화면 (질문 횟수에 세지 않음)
   - `AMBIGUOUS` + `question`: 범위 질문에 짧게 답함 → 다시 확인 요청
   - `MULTI_GOAL`: 질문 + `goalCandidates` 중 하나 선택 → 확인 화면 (질문 횟수 포함)
   - `UNKNOWN_TERM`: `맞아요`면 답과 함께 다시 확인 요청, `아니에요`면 입력으로 돌아가 문장 수정
4. 질문 2회를 다 썼거나 AI 장애·오프라인이면 `지금 문구로 정하기`(수동 확정)
5. 확인 화면 `이 목표로 정하기` → `POST /api/v1/goals` 성공 뒤 완료 화면
6. 완료 화면 `집중 시간 정하기`는 세션 설정 Issue에서 연결한다(그 전에는 준비 중 안내).
7. 언제든 ✕로 홈에 돌아간다.

## Desktop 상세 동작

Electron Process·Window·IPC·권한의 확정 기본값은 `ELECTRON_ARCHITECTURE.md`, `WINDOW_LIFECYCLE.md`, `IPC_CONTRACT.md`, `MACOS_PERMISSION_FLOW.md`를 따른다. 변경하려면 먼저 `DECISION_RECORD.md`를 갱신한다.

- 진입 조건: 로그인·온보딩 완료 사용자. 로그아웃되면 `/`로 돌아간다.
- 정상 동작: 화면 상태 `GOAL_DRAFT → GOAL_ANALYZING → GOAL_NEEDS_QUESTION/GOAL_NEEDS_CHOICE → GOAL_CONFIRMED/GOAL_MANUAL_CONFIRMED`.
- 중복 요청: 요청 중 버튼·입력을 막는다. 응답이 오기 전에 입력이 바뀌면 그 응답은 버린다(`goalDraftVersion` 대신 요청 번호).
- 재시도: `POST`라 Desktop은 자동 재전송하지 않는다(공통 정책 4.3). AI 재시도는 Backend가 맡고, 최종 실패면 수동 확정을 보여 준다.
- 종료 조건: 목표 저장 성공 또는 ✕.

## macOS 권한 및 시스템 기능

- 필요한 권한: 없음
- 활성 앱 또는 Bundle Identifier 처리: 해당 없음

## 화면 및 사용자 경험

- 화면 위치: `app/goal/page.tsx` → `features/goal/GoalSetting.tsx`
- 디자인 원본: `FocusOn-design/goal-setting.html`(+ `fo-base.css`). 시안의 가짜 판정 규칙과 `?state=` 미리보기는 넣지 않는다.
- 문구: 포키 질문은 존댓말 해요체, 오류·안내도 존댓말.
- 로딩·오류: AI 확인 중 점 세 개, AI 장애 `앗, 지금은 확인이 어려워요.`, 오프라인 `인터넷이 끊겼어요.` + 하단 안내, 저장 실패는 하단 안내 + 다시 시도.

## 데이터 및 IPC/API

- Renderer 입력: 목표 문구, 질문 답, 선택지
- Main 전달값: `window.focusOn.goals.clarify({ goalText, conversationTurn, clarificationSessionId?, previousAnswers })`, `goals.create({ goalText, confirmationSource, clarificationSessionId? })`
- Server 요청: `POST /api/v1/goals/clarify`, `POST /api/v1/goals`
- 저장 데이터: 저장한 목표 `{ goalId, goalText }`를 세션 설정 화면에 넘기기 위해 `sessionStorage`에 둔다(민감 정보 아님).
- Backend 확인 필요: 클라이언트 API 명세의 clarify 요청에는 `goalText`·`conversationTurn`만 있어 질문 답을 보낼 수 없다. AI 계약 3.2처럼 `clarificationSessionId`·`previousAnswers`를 받고, 응답에 `clarificationSessionId`를 돌려주는 형식으로 제안한다.

```json
{
  "goalText": "rq 캐싱 조지기",
  "conversationTurn": 1,
  "clarificationSessionId": "701",
  "previousAnswers": [{ "question": "rq가 React Query를 말하는 게 맞아요?", "answer": "맞아요" }]
}
```

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 2자 미만 | `2자 이상 입력해 주세요.` | 요청하지 않음 |
| AI 장애·시간 초과 | `목표를 확인하지 못했어요.` / `입력한 문구 그대로 시작할 수 있어요.` | 수동 확정 버튼 |
| 오프라인 | `인터넷 연결을 확인해 주세요.` | 수동 확정 버튼(저장은 연결 뒤) |
| 질문 2회 소진 | `질문을 모두 썼어요. 문장을 고치거나 지금 문구로 바로 정할 수 있어요.` | 수동 확정 버튼 |
| 저장 실패 | `목표를 저장하지 못했어요.` | 완료로 넘어가지 않음, 다시 시도 |
| `GOAL_TEXT_INVALID` | `목표는 2~40자로 적어 주세요.` | 입력으로 돌아감 |

## 보안·개인정보

- 목표 문구만 서버로 보낸다. 화면·카메라 데이터와 무관하다.
- AI Provider Key는 클라이언트에 없다(Backend Adapter).

## 완료 조건

- [ ] 기획 내용 확인
- [ ] 정상 흐름 구현 (AI 응답 4종, 질문 2회 제한, 수동 확정)
- [ ] 권한 거부 흐름 구현 (해당 없음)
- [ ] 예외 상황 확인 (AI 장애·오프라인·저장 실패·늦은 응답)
- [ ] 키보드 사용·접근 가능한 이름·색상 대비·Focus 표시 확인
- [ ] 테스트 완료
- [ ] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성
- [ ] `*-REPORT.md` 작성
- [ ] `CONTEXT.md`, `NEXT_TASK.md` 갱신
