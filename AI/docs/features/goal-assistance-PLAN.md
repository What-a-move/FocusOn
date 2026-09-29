# AI 기능 기획서: 학습 목표 설정 보조

> 상태: 구현 승인 — AI 영역 한정
> JEV는 타입화된 확률 판단, OpenAI는 구조화된 자연어 생성, LangGraph는 결정적 분기와 실패 제어만 담당한다.

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

JEV 또는 OpenAI 실패를 `INVALID`로 바꾸지 않는다.

## 생성과 재검증

- OpenAI: 질문 한 개와 선택지 2~3개, 추천 목표 2~3개와 이유, GoalProfile 초안을 구조화 Schema로 생성한다.
- JEV: 각 생성 결과에 대해 `preserves_user_intent`, `contains_one_goal`, `is_specific_enough`를 Noul로 검증한다.
- 후보는 개별 요청으로 검증하며 외부 요청 급증을 피하기 위해 순차 실행한다.
- 검증 실패 시 실패 항목만 전달해 OpenAI Repair를 최대 1회 수행한다.
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

## 모델·외부 연동

- 기본 로컬 테스트 설정: `TYPESAFE_API_KEY`와 `OPENAI_API_KEY`를 사용한다.
- JEV: TypeSafe Python SDK `AsyncTypeSafeClient`, TypeSafe 공식 API 기본 주소, 모델 `jev-latest`
- 생성: `ChatOpenAI.with_structured_output(..., method="json_schema")`, OpenAI 공식 API 기본 주소, 모델 `gpt-5-mini`
- 질문·추천·GoalProfile처럼 짧은 구조화 생성은 기본 `reasoning_effort=minimal`, `verbosity=low`, 최대 600 출력 토큰으로 호출한다. 이는 상태 판단을 생략하지 않고 생성 대기만 줄이는 설정이다.
- Vercel AI Gateway 설정과 우회 경로는 지원하지 않는다.
- Timeout·Retry: Pydantic Settings와 TypeSafe `RetryPolicy`로 제한하고 인증·검증 4xx는 재시도하지 않는다.
- 기본 테스트: Fake Client만 사용하며 실제 외부 API를 호출하지 않는다.
- 실제 평가: 별도 명령과 환경 변수로 명시적으로 활성화할 때만 실행한다.
- Streamlit 비교 화면: 로컬 `convaiinnovations/laya-multilingual`에 JEV와 같은 state·네 질문을 전달해 결과만 표시한다. Laya는 상태 Router, 질문·추천 생성, GoalProfile, 저장·세션 시작에 관여하지 않는다.

## 내부 API와 오류

- Endpoint: `POST /internal/v1/goals/clarify`
- 입력 검증: HTTP 422, `VALIDATION_ERROR`, fallback 불가
- JEV·OpenAI 일시 장애: HTTP 503, `AI_UNAVAILABLE`, fallback 허용
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
- [x] OpenAI는 생성만 담당하고 clarityStatus를 바꾸지 않는다.
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
