# AI 기능 수정 기획서 — 목표 보조 지연·중복·문구 개선

## 목적과 범위

- 로컬 실연동(Desktop → Backend → AI 8001)에서 보고된 목표 보조 관련 문제와 실행 안내를 `AI/` 내부 범위에서 수정한다.
  1. **이슈 1·3 (지연 6~9.8초·구체적/추천/분할 할 일의 `NEEDS_QUESTION` 오강등):** 1차 JEV에서 `CLEAR`, `NEEDS_SUGGESTION`, `NEEDS_SELECTION`으로 판정된 목표에 대해 생성 결과를 다시 2차 JEV 사후 검증(`verify_generated`)에 보내면 `preserves_user_intent` 기준과 충돌해 후보/프로필이 탈락 → `repair_*` → `generate_fallback_question`으로 Claude 3~6회 호출 및 `NEEDS_QUESTION` 오강등이 발생하는 문제를 제거한다.
  2. **이슈 2 (후보 제목 중복 HTTP 500):** `RecommendedGoalBatch`의 중복 제목(공백·대소문자 무시 포함) 검증과 결정적 중복 제거(`_validate_candidates`: 2개 이상이면 통과, 2개 미만이면 fallback 질문 전환) 및 `goal_routes.py`의 `ValidationError` 502 방어로 HTTP 500을 원천 차단한다.
  3. **이슈 4 (질문 문구 입력 방식·말투 불일치):** 백엔드가 `question.options`를 전달하지 않고 `question.text`만 표시하는 실제 UI에 맞춰, 보기 전제 표현(`"다음 중"`, `"아래에서"`, `"선택지 중"` 등)을 금지하고 한국어 해요체로 통일한다.
  4. **참고 (로컬 실행 안내):** `AI/README.md`에 Python 3.11+ 필수 및 `uv venv --python 3.12 AI/.venv` 설정 예시를 보강한다.
- **범위 밖:** `AI/src/models/screen_activity/`(이슈 5 `fix_mistral_regex` 토크나이저 경고)는 화면 분석 담당 팀원 영역이므로 코드를 수정하지 않고 원인 분석 결과(비-Mistral `XLMRobertaTokenizerFast` 오탐 경고, 정확도 영향 없음)만 공유한다.
- **수정 범위:** `AI/` 내부의 목표 보조 관련 파일과 `AI/README.md`, `AI/docs/`만 수정한다 (`server/`, `apps/`, 루트 문서·설정 및 `AI/src/models/screen_activity/` 미수정).

## 입출력 계약

- `POST /internal/v1/goals/clarify` 요청·응답 JSON 스키마(`GoalClarifyRequest`, `GoalClarifyResponse`, `ErrorResponse`)는 기존 계약을 유지한다.
- `CLEAR` 판정 목표는 `generate_profile()` 1회 호출 후 `clarityStatus: "CLEAR"`, `interpretedGoal`, `goalProfileDraft`를 반환하며 JEV 사후 검증 실패로 `NEEDS_QUESTION`으로 강등되지 않는다.
- `NEEDS_SUGGESTION` / `NEEDS_SELECTION` 후보 생성 시:
  - 2차 JEV 사후 검증 없이 Claude `generate_candidates()` 1회 + 결정적 검증(`_validate_candidates`)으로 응답한다.
  - `RecommendedGoalBatch`의 공백·대소문자 무시 중복 제목 검증과 결정적 중복 제거를 적용하며, 고유 후보가 2개 이상이면 정상 응답하고 2개 미만이면 500 예외 대신 `NEEDS_QUESTION` fallback 질문으로 전환한다(`conversationTurn >= 2`이면 추가 질문 없이 `question: null`).
  - `goal_routes.py`에서 예상치 못한 `pydantic.ValidationError`가 내부 조립 단계에서 발생해도 HTTP 500 대신 `502 INVALID_MODEL_RESPONSE`(`retryable: true`, `fallbackAllowed: true`)로 안전 반환한다.
- `ClarificationQuestion.text`는 해요체 단일 문장이며 `"다음 중"`, `"아래에서"`, `"아래 보기"`, `"선택지 중"` 등 보기 전제 표현과 하십시오체(`~습니다`, `~습니까`)를 금지한다.

## 실패·예산 정책

- `CLEAR` 경로 모델 호출 상한: JEV 1회(`assess_goal`) + Claude 1회(`generate_profile`, 스키마 오류 시 내부 최대 1회 수리). JEV 사후 검증 호출 0회.
- `NEEDS_SUGGESTION` / `NEEDS_SELECTION` 경로 모델 호출 상한: JEV 1회(`assess_goal`) + Claude 1회(`generate_candidates`, 스키마 오류 시 내부 최대 1회 수리). JEV 사후 검증 및 후보별 repair 호출 0회. 결정적 중복 제거 후 고유 후보가 2개 미만일 때만 `NEEDS_QUESTION` fallback 질문으로 전환한다(`conversationTurn >= 2`면 추가 질문 생성 없음).
- 외부 제공자 일시 오류(`ModelUnavailableError`)는 503 `retryable: true`, 설정 오류(`ModelConfigurationError`)는 503 `retryable: false`, 구조화 검증 실패(`InvalidModelResponseError`)는 502 `retryable: true`, `fallbackAllowed: true`를 유지한다.
- 로그·오류 응답에 사용자 목표 원문·화면 원문·API Key를 포함하지 않는다.

## 기계가 판별 가능한 완료 조건

1. `CLEAR`, `NEEDS_SUGGESTION`, `NEEDS_SELECTION` 판정 시 JEV `verify_generated_result` 호출 횟수가 `0`, 후보 repair 호출이 `0`이며 각각 `CLEAR` / `NEEDS_SUGGESTION` / `NEEDS_SELECTION`으로 유지됨을 단위 테스트로 검증한다.
2. 생성된 후보에 공백·대소문자 무시 중복 제목이 섞였을 때:
   - 중복 제거 후 2개 이상이면 HTTP 500 없이 고유 후보만 반환하고,
   - 중복 제거 후 2개 미만이면 HTTP 500 없이 fallback 질문(`NEEDS_QUESTION`, `conversationTurn >= 2`면 `question: null`)으로 전환함을 단위·API 테스트로 검증한다.
3. `candidate_prompt`가 한국어 2~40자·해요체·중복 제목 금지 요구사항을 포함하고, `repair_prompt`가 `existingTitles`를 지원함을 단위 테스트로 검증한다.
4. `SYSTEM_PROMPT`, `question_prompt`, `_fallback_question`이 해요체·보기 비의존 규칙을 포함하고, Claude가 `"다음 중"` 또는 하십시오체(`"확인하고 싶습니다."`)를 반환할 때 검증이 이를 거부·수리함을 단위 테스트로 검증한다.
5. `AI` 전체 회귀 테스트가 모두 통과한다:
   - 실행 명령: `LANGSMITH_TRACING=false LANGCHAIN_TRACING_V2=false LANGSMITH_TRACE_RAW_GOAL_TEXT=false PYENV_VERSION=what-a-move PYTHONPATH=AI:. pyenv exec python -m pytest AI/tests -q --ignore=AI/tests/openai_test.py`
   - 추가 검사: `git diff --check` 및 변경 범위가 `AI/` 목표 보조 관련 파일로 한정됨(`AI/src/models/screen_activity/` 미변경)을 확인.
6. 독립 검증자(`project-verifier`) 서브에이전트가 변경 파일과 테스트 실행 결과를 직접 검증해 `VERDICT: PASS`를 반환한다.
