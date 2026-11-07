# AI 기능 기획서: 학습 목표 설정 보조

> 상태: **MVP 구현 계획 — 공개 계약은 Notion `POST /api/v1/goals/clarify`와 `AI 분석 계약` 기준**
> 목표를 사용자가 최종 확정하기 전에 명확성을 검사하고 추천·질문을 제공한다.

## 기본 정보

- 기능명: 학습 목표 설정 보조
- 기능 ID: `goal-assistance`
- 작성일: 2026-09-22
- 우선순위: MVP - 핵심
- 관련 Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12), PR [#137](https://github.com/What-a-move/FocusOn/pull/137)
- 작업 Branch: `feat/12-ai-goal-assistance`
- 관련 문서:
  - `AI/docs/goal_session_spec.md`
  - `AI/docs/api_spec.md`
  - `AI/docs/prompt_guide.md`
  - `AI/docs/evaluation_spec.md`

## 기능 목적

사용자가 입력한 자연어 목표가 너무 넓거나 여러 목표를 포함해도 임의로 확정하지 않고, 사용자가 하나의 학습 목표를 확인·수정·선택하도록 돕는다.

AI가 담당하는 범위:

- 목표 명확성 상태 분류
- 구조화 목표 후보 생성
- 추천 목표 2~3개 생성
- 한 응답에 확인 질문 최대 1개 생성, 대화 전체 최대 2회
- 약어·오타 후보 제안

AI가 담당하지 않는 범위:

- 사용자 최종 확인·저장
- 목표 소유권·인증
- 세션 시작·종료
- 방문 콘텐츠를 이용한 목표 자동 확장

## 입력·출력 계약

입력은 사용자가 직접 입력한 목표, 같은 목표의 이전 구조화 결과와 허용된 피드백 일부만 사용한다. 방문 콘텐츠와 전체 화면·DOM·카메라 데이터는 목표 보조 입력에 사용하지 않는다.

```json
{
  "goalText": "React랑 Docker랑 영어 공부",
  "conversationTurn": 0
}
```

`decision` 허용 값:

- `CLEAR`
- `AMBIGUOUS`
- `MULTI_GOAL`
- `UNKNOWN_TERM`

```json
{
  "decision": "MULTI_GOAL",
  "normalizedGoalText": null,
  "question": "이번 세션에는 어떤 걸 할까요?",
  "suggestions": [],
  "goalCandidates": ["React 학습", "Docker 학습", "영어 학습"],
  "conversationTurn": 1
}
```

사용자 확인 전 `GoalProfile`은 확정 판단 기준이 아니다. AI 실패·Timeout·무응답이면 사용자가 직접 작성한 목표를 확인한 뒤 세션을 시작할 수 있다.

## 분석 기준

- `CLEAR`: 하나의 주제와 예상 활동이 비교적 명확하다.
- `AMBIGUOUS`: 범위가 넓거나 목적이 모호하다. 목표 제안 2~3개 또는 확인 질문 1개를 반환한다.
- `MULTI_GOAL`: 여러 목표가 섞였다. `goalCandidates`와 선택 질문 1개를 반환한다.
- `UNKNOWN_TERM`: 약어·오타의 의미가 불확실하다. 추측하지 않고 확인 질문 1개를 반환한다.
- 빈 입력·길이 오류는 AI 판정값이 아니라 `GOAL_TEXT_INVALID` 입력 검증 오류다.

AI는 추천 후보를 만들 수 있지만 사용자의 확인을 대신하지 않는다. `confirmedByUser`는 AI 출력이 아니라 Server가 사용자 확인 이벤트로 관리한다.

## 처리 흐름

1. Server가 사용자 소유권과 요청 크기를 검증한다.
2. AI가 목표 원문을 개인정보·Prompt Injection 관점에서 검사한다.
3. AI가 계약에 정의된 네 가지 값 중 하나의 `decision`과 구조화 후보를 반환한다.
4. Client가 후보·질문을 표시한다.
5. 사용자가 하나의 목표를 확인·수정한다. 질문은 누적 최대 2회이고, 이후에도 불명확하면 수동 확정을 제공한다.
6. Server가 확정 `GoalProfile`과 `goalVersion`을 저장한다.

## API·책임

- 공개 Endpoint: `POST /api/v1/goals/clarify`
- Client는 AI를 직접 호출하지 않는다.
- Server가 인증·소유권·저장·목표 버전을 관리한다.
- 방문 콘텐츠는 목표 범위를 자동 확장하는 근거로 사용할 수 없다.

## 예외 처리

| 상황 | 반환·처리 |
| --- | --- |
| 빈 입력 | `GOAL_TEXT_INVALID`, 재입력 안내 |
| 약어·오타 불명확 | `UNKNOWN_TERM`, 확인 질문 1개 |
| 여러 목표 | `MULTI_GOAL`, 후보 중 하나 선택하는 질문 1개 |
| 범위가 넓음 | `AMBIGUOUS`, 제안 2~3개 또는 질문 1개 |
| 질문 2회 이후에도 모호함 | 사용자가 현재 목표 문장을 수동 확정 |
| AI Timeout·무응답 | 직접 입력 목표 확인 경로 제공 |
| Prompt Injection 또는 Schema 오류 | 응답 미사용, 사용자 원문 재출력 금지 |

## 평가 계획

- “JavaScript Promise 학습” → `CLEAR`
- “영어 공부” → `AMBIGUOUS`
- “React랑 Docker랑 영어 공부” → `MULTI_GOAL`
- “React 상태 관리 학습” → `AMBIGUOUS` 또는 `CLEAR` 후보 검토
- “rq 캐싱 조지기” → `UNKNOWN_TERM`
- 빈 입력 → `GOAL_TEXT_INVALID`
- AI 실패·무응답에도 직접 목표로 시작 가능
- 페이지 본문에 목표 확정·Prompt 무시를 지시하는 문장이 포함된 경우 무시

## 보안·개인정보

- 원본 화면·카메라·전체 DOM·전체 방문 기록을 입력받지 않는다.
- 목표 원문과 답변은 최소 범위에서만 사용한다.
- API Key, Authorization Header, 다른 사용자 목표를 모델에 전달하지 않는다.
- 모델 원본 출력과 사용자 원문을 로그에 남기지 않는다.

## 완료 조건

- [ ] 4개 `decision`의 입력·출력 계약을 공유했다.
- [ ] 사용자 확인 전 목표를 확정하지 않는다.
- [ ] 추천·질문 개수 제한을 검증했다.
- [ ] AI 실패·무응답 직접 시작 경로를 검증했다.
- [ ] Prompt Injection과 Schema 오류를 평가했다.
- [ ] `goalId`·`goalVersion` 저장 책임을 Server와 합의했다.

## PR #137 충돌 해소와 현재 구현 경계

- 기준: `dev`의 공개 목표 `decision` 4종과 최신 화면 개입 `v1`, Client별 OCR·개인정보 계약을 유지한다. 2026-11-03 Notion 조회는 404였으므로 새 계약을 확정하지 않았다.
- 이 브랜치의 목표 보조 코드는 내부 `clarityStatus` 6종을 반환하는 JEV·Claude·LangGraph 구현이다. 공개 4종으로의 Backend 어댑터는 아직 검증되지 않았고 내부 결과를 공개 응답에 그대로 사용할 수 없다.
- 내부 요청은 `requestId`, `goalText`, `conversationTurn`(0~2), 선택적 `clarificationSessionId`, `previousAnswers[{question,answer}]`다. 회차와 답변 수가 같아야 하며 답변 2개 이후 추가 질문은 생성하지 않는다.
- 대화 ID의 발급·소유권·이력 검증, 사용자 최종 확인·목표 저장은 Backend 책임이다. AI는 전달받은 ID만 반환한다. AI HTTP 경로의 제공자 SDK 재시도는 0이며 Backend 재시도와 구분한다.
- 질문 스트리밍·최대 2건 병렬 생성·10초 이후 기본 질문 전환과 최대 3개 후보 검수/Repair는 유지한다. Laya는 비교 전용이다.
- 로컬 테스트용 원문 trace opt-in은 공유·배포 환경에 적용하지 않는다. 목표 최대 200자 내부 설정과 제품 2~40자, 내부 6종/공개 4종 변환은 통합 후속 검증 대상이다.

## #12 내부 구현 계획·이력 (공개 계약과 구분)

### AI 기능 기획서: 학습 목표 설정 보조

> 상태: 구현 승인 — AI 영역 한정
> JEV는 타입화된 확률 판단, Claude는 구조화된 자연어 생성, LangGraph는 결정적 분기와 실패 제어만 담당한다.

## 기본 정보

- 기능명: 학습 목표 설정 보조
- 기능 ID: `goal-assistance`
- 작성자: Codex
- 작성일: 2026-09-26
- 우선순위: MVP - 핵심
- 관련 Issue: [#12](https://github.com/What-a-move/FocusOn/issues/12)
- Branch: `feat/12-ai-goal-assistance`
- 관련 Desktop·Extension·Server 문서:
  - `AI/docs/goal_session_spec.md`
  - `AI/docs/api_spec.md`
  - `AI/docs/prompt_guide.md`
  - `AI/docs/evaluation_spec.md`

## 기능 목적

- 해결할 문제: 모호하거나 여러 주제가 섞인 자연어 목표를 사용자 확인 전에 임의 확정하지 않고 하나의 실행 가능한 학습 목표로 구체화한다.
- AI가 담당할 역할: 목표 유효성·복수 목표·불명확 용어·구체성 판단, 질문·추천·GoalProfile 초안 생성, 생성 결과 재검증과 안전한 fallback.
- AI가 담당하지 않는 범위: 사용자 인증, 목표 저장, `goalId`·`goalVersion` 발급, 세션 시작, 제품 UI, 공개 Server API, 콘텐츠 안전성 정책, 장기 대화 저장. 실제 Workflow 확인용 로컬 Streamlit 테스트 화면은 포함한다.

## 입력 데이터

- 입력 출처: Server 내부 호출
- 필수 필드: `requestId`, `originalText`
- 선택 필드: `selectedGoalText`, `clarificationAnswers`
- 전처리: 공백 정리와 길이·개수 검증만 수행하며 사용자 표현을 자동 교정하지 않는다.
- 제외할 민감 데이터: API Key, 인증 Header, 화면·DOM·OCR·방문 기록, 다른 사용자의 목표.

```json
{
  "requestId": "req_01",
  "originalText": "rq 캐싱 조지기",
  "selectedGoalText": null,
  "clarificationAnswers": []
}
```

## 분석 기준

JEV 최초 평가는 최초 구현과 동일하게 같은 `state`에 다음 네 질문을 한 번에 전달한다.

1. `is_usable_goal`: Noul
2. `has_multiple_main_goals`: Noul
3. `has_unclear_term`: Noul
4. `specificity_level`: Score

JEV는 설명이나 사용자 문구를 생성하지 않는다. Router는 실험 Threshold를 다음 우선순위로 적용한다.

```text
INVALID
→ UNRECOGNIZED_TERM
→ NEEDS_SELECTION
→ is_usable_goal < 0.60: NEEDS_QUESTION
→ specificity_confidence < 0.45: NEEDS_QUESTION
→ specificity_level >= 2.00: CLEAR
→ specificity_level >= 1.00: NEEDS_SUGGESTION
→ otherwise: NEEDS_QUESTION
```

`specificity_level`은 0(해석 불가)부터 3(명확한 단일 실행 목표)까지의 Score다. 2.00 이상은 `CLEAR`, 1.00 이상은 `NEEDS_SUGGESTION`, 그 미만은 `NEEDS_QUESTION`으로 보낸다. `is_usable_goal`의 `INVALID` 임계값, 복수 목표, 불명확 용어의 `UNRECOGNIZED_TERM`·`NEEDS_SELECTION` 우선순위와 임계값은 변경하지 않는다. 추천 화면에서는 사용자가 원문을 명시적으로 선택해 GoalProfile 확인으로 진행할 수 있다.

최초 판단은 최초 기능의 영어 instructions와 criteria를 유지한다. 약어·오타 때문에 의미를 확신 있게 해석하지 못하면 `UNRECOGNIZED_TERM`이며, 단지 범위가 넓다는 이유만으로는 불명확 용어로 처리하지 않는다.

JEV 또는 Claude 실패를 `INVALID`로 바꾸지 않는다.

## 생성과 재검증

- Claude: 한 요청에서 질문은 최대 1개, 선택지는 2~3개로 생성한다. 전체 대화에서 AI 질문은 최대 2회이며, 이후에는 사용자가 목표 문장을 직접 확인·수정하도록 안내한다. 여러 목표가 입력되면 최종 GoalProfile이 하나의 목표만 담도록 후보 중 하나를 선택하게 한다.
- 목표 입력은 MVP에서 2~40자, 누적 질문 답변은 최대 2개로 제한한다.
- 추천 목표 2~3개와 이유, GoalProfile 초안은 구조화 Schema로 생성한다.
- JEV: 각 생성 결과에 대해 `preserves_user_intent`, `contains_one_goal`, `is_specific_enough`를 Noul로 검증한다.
- 생성된 후보 검증과 검증 실패 후보의 Repair는 서로 독립된 항목끼리 병렬 실행한다. 후보 응답 Schema 상한(최대 3개)을 동시 호출 상한으로 유지한다.
- 확인 질문은 같은 구조화 생성 요청을 최대 2건 병렬 실행하고 먼저 도착한 유효한 질문 하나만 반환한다. 이미 답한 질문은 제외하고 다른 결과를 기다린다. 동시 호출 수는 `CLAUDE_QUESTION_PARALLELISM`(기본 2, 허용 1~2)으로 제한하며, 실제 지연·호출 비용은 Live 평가에서 확인한다.
- Streamlit은 Claude 응답의 질문 문구를 생성 중 초안으로 표시한다. 구조화 Schema 검증을 마친 결과만 답변 선택지로 확정한다. 5초는 성능 목표이며, 유효한 모델 질문은 먼저 완성되는 즉시 반환한다. 기본 10초가 지나도록 유효한 결과가 없거나 두 요청 모두 일시 실패·Schema 오류·기존 질문 반복이면 그때 상태별 로컬 기본 질문을 사용한다. 설정·인증 오류는 숨기지 않고 즉시 표시한다. 최초 JEV 판단과 화면 렌더링 시간은 이 10초 대기 기준 밖이다.
- 검증 실패 시 실패 항목만 전달해 Claude Repair를 최대 1회 수행한다.
- 두 번째 검증도 실패하면 `NEEDS_QUESTION` 또는 직접 수정 fallback으로 종료한다.
- 사용자가 답변하거나 후보를 선택하면 새 요청으로 네 가지 최초 평가 전체를 다시 실행한다. 생성 Prompt는 최신 답변을 사용자 요구사항으로 취급해 GoalProfile의 `interpretedGoal`과 후보 문구에 반영한다.

## 출력 데이터

```json
{
  "data": {
    "clarityStatus": "UNRECOGNIZED_TERM",
    "interpretedGoal": null,
    "recommendedGoals": [],
    "question": {
      "id": "question_01",
      "text": "확인이 필요한 표현이 있어요.",
      "options": [],
      "allowCustomAnswer": true
    },
    "goalProfileDraft": null,
    "invalidReason": null,
    "requiresUserConfirmation": true,
    "fallbackAllowed": true
  }
}
```

허용 `clarityStatus`는 `CLEAR`, `NEEDS_SELECTION`, `NEEDS_SUGGESTION`, `NEEDS_QUESTION`, `INVALID`, `UNRECOGNIZED_TERM`이다. 모든 정상 응답은 `requiresUserConfirmation=true`다.

## 처리 흐름

```text
START
→ validate_input
→ assess_with_jev
→ route_by_clarity
  ├─ CLEAR → GoalProfile 생성 → JEV 검수 → 조건부 Repair → 확인 응답
  ├─ NEEDS_SELECTION → 후보 생성 → 후보별 JEV 검수 → 조건부 Repair → 선택 응답
  ├─ NEEDS_SUGGESTION → 후보 생성 → 후보별 JEV 검수 → 조건부 Repair → 추천 응답
  ├─ NEEDS_QUESTION → 질문 생성 → 질문 응답
  ├─ UNRECOGNIZED_TERM → 용어 확인 질문 생성 → 질문 응답
  └─ INVALID → 무효 응답
```

Graph는 Checkpointer 없이 요청마다 `START`부터 실행한다. 같은 Node에 정적 Edge와 조건부 Edge를 함께 연결하지 않는다.

Streamlit 테스트 화면의 최초 동작명은 `목표 저장`이지만 클릭 즉시 저장하지 않는다. 위 Workflow를 먼저 실행하고 질문·추천이 끝나 `CLEAR`와 검증된 GoalProfile을 얻은 뒤 사용자가 별도의 최종 저장 버튼을 눌러야 테스트 세션 메모리에 저장한다.

Streamlit 테스트 화면에서 사용자가 질문에 답하면 답변 문구를 현재 목표 입력값 끝에 반영하고, 수정된 입력값으로 다시 판단한다. 이 문구는 사용자가 계속 편집할 수 있으며 `CLEAR` GoalProfile을 별도로 최종 확인하기 전에는 저장하지 않는다.

## 모델·외부 연동

- 기본 로컬 테스트 설정: `TYPESAFE_API_KEY`와 `ANTHROPIC_API_KEY`를 사용한다.
- JEV: TypeSafe Python SDK `AsyncTypeSafeClient`, TypeSafe 공식 API 기본 주소, 모델 `jev-latest`
- 생성: 공식 `AsyncAnthropic.messages.parse`·`messages.stream`, Anthropic 공식 API 기본 주소, 모델 `claude-haiku-4-5-20251001`
- 질문·추천·GoalProfile 생성은 기본 최대 1,200 출력 토큰으로 호출한다. OpenAI 전용 `reasoning_effort`·`verbosity`는 제거했다. 생성 모델은 상태 판단에 관여하지 않는다.
- Vercel AI Gateway 설정과 우회 경로는 지원하지 않는다.
- Timeout·Retry: Pydantic Settings와 TypeSafe `RetryPolicy`로 제한하고 인증·검증 4xx는 재시도하지 않는다.
- 기본 테스트: Fake Client만 사용하며 실제 외부 API를 호출하지 않는다.
- 실제 평가: 별도 명령과 환경 변수로 명시적으로 활성화할 때만 실행한다.
- Streamlit 비교 화면: 로컬 `convaiinnovations/laya-multilingual`에 JEV와 같은 state·네 질문을 전달해 결과만 표시한다. Laya는 상태 Router, 질문·추천 생성, GoalProfile, 저장·세션 시작에 관여하지 않는다.

## 내부 API와 오류

- 2026-10-25 요청 필드를 `goalText`, `conversationTurn`, `clarificationSessionId?`, `previousAnswers[{question,answer}]`로 정합화했다. 내부 추적용 `requestId`·선택 목표 문맥은 유지한다. 정상 응답에 Backend가 전달한 대화 ID를 반환하며 ID 발급·소유권·최종 목표 연결은 Backend 책임이다. 회차·답변 최대 2회 뒤 추가 질문을 만들지 않는다. 상세 계약과 호환 범위는 `AI/docs/api_spec.md` 4절을 따른다.
- Backend가 retryable 일시 오류에 1초·3초 후 최대 2회 재시도한다. Desktop은 자동 재전송하지 않으며 AI HTTP 경로의 SDK 재시도는 0이다. 로컬 Streamlit 직접 호출 설정·병렬 후보 생성·의미 검수 Repair와 구분한다.
- Endpoint: `POST /internal/v1/goals/clarify`
- 입력 검증: HTTP 422, `VALIDATION_ERROR`, fallback 불가
- JEV 및 질문 외 Claude 생성·검증의 일시 장애: HTTP 503, `AI_UNAVAILABLE`, fallback 허용. 확인 질문 생성만 두 호출이 모두 일시 실패하면 정상 응답에 로컬 기본 질문을 넣는다.
- Repair 후 Schema 오류: HTTP 502, `INVALID_MODEL_RESPONSE`, fallback 허용
- 확정된 서비스 인증 계약이 없으므로 로컬·테스트 Endpoint로만 구현하고 배포를 차단한다.

## 보안·개인정보

- 로그 허용: `requestId`, 상태, 모델 버전, 지연시간, 재시도 횟수, Token Usage, 오류 코드.
- 로그 금지: 목표·선택 후보·질문·답변 원문, 모델 원본 출력, API Key, Authorization Header.
- TypeSafe SDK 로그 기본값은 `warning`, LangSmith Trace는 비활성화한다.
- 외부 모델 전송은 `AI-PRIV-003` 승인 전 로컬 개발·Mock 검증에 한정한다.

## 평가 계획

- 고정 Fixture로 6개 상태, 답변·후보 선택 후 재평가, 생성 결과 검수·Repair, Prompt Injection, Timeout과 Schema 실패를 검증한다.
- 실제 평가에서는 상태별 Precision·Recall, confusion matrix, fallback 비율, 후보 검수 실패율, 호출 횟수와 지연시간을 기록한다.
- 임계값은 평가 전까지 실험값이며 설정에서 교체 가능해야 한다.

## 완료 조건

- [x] 네 가지 최초 JEV 판단과 결정적 Router가 구현됐다.
- [x] Claude는 생성만 담당하고 clarityStatus를 바꾸지 않는다.
- [x] 후보·GoalProfile을 세 가지 JEV 질문으로 재검증한다.
- [x] Repair는 최대 1회이며 무한 반복하지 않는다.
- [x] 질문은 한 개, 후보와 선택지는 2~3개다.
- [x] 답변·후보 선택 후 전체 평가를 다시 수행한다.
- [x] 사용자 확인 전 목표를 확정하지 않는다.
- [x] 모델 장애를 `INVALID`로 변환하지 않는다.
- [x] 기본 테스트에서 외부 API를 호출하지 않는다.
- [x] 원문과 비밀값이 로그·예외·Fixture에 남지 않는다.
- [x] 내부 API·단위·Workflow 테스트와 정적 검사가 통과한다.
- [x] 관련 AI 문서와 결과 리포트가 실제 구현과 일치한다.
