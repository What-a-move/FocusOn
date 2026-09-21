# FocusSessionAgent 실행 계약

## 문서 상태

- 상태: 팀 검토용 계약 초안, 미구현
- 버전: `0.1.0`
- 기준일: 2026-09-12
- 관련 Issue: [#3](https://github.com/What-a-move/FocusOn/issues/3)
- 관련 규칙: `AI-AGENT-001`, `AI-LLM-001`, `AI-FAIL-001`, `AI-STALE-001`, `AI-CACHE-001`

이 문서는 규칙과 임베딩만으로 판단하기 어려운 요청을 제한적으로 처리하는 단일 상태 기반 Agent의 계약을 정의한다. Agent, LangGraph, 모델과 도구는 아직 구현되지 않았으며 라이브러리·저장소·수치 예산은 평가 후 확정한다.

## 1. Agent를 사용하는 이유

대부분의 이벤트는 고정된 파이프라인으로 처리할 수 있다. 그러나 직접 학습과 보조 학습의 경계, 최근 오류 검색과 현재 문서의 연결, 사용자 피드백의 적용 범위처럼 여러 근거를 순서대로 확인해야 하는 사례가 있다.

Agent는 이 모호한 사례에서만 허용된 읽기 도구를 조합한다. 다음 역할을 대신하지 않는다.

- 제외·권한·개인정보 Gate
- DOM·OCR 실행과 원본 이미지 처리
- 결정적인 캐시 키·유사도·시간 계산
- 최종 알림·차단·세션 명령
- DB 쓰기·삭제와 사용자 권한 검사

## 2. 진입 조건

### 2.1 진입 가능

- 규칙과 임베딩 근거가 충돌한다.
- `RELATED`와 `SUPPORTING`을 구분하려면 최근 흐름이 필요하다.
- 충분한 콘텐츠가 있지만 목표와의 관계가 여러 의미로 해석된다.
- 동일 범위 피드백인지 판단하기 위해 제한된 조회가 필요하다.

### 2.2 진입 금지

- 제외·민감 화면 또는 권한 실패다.
- 추출 결과가 `FAILED`, `EXCLUDED`, `UNSUPPORTED`다.
- 사용자·세션·목표 소유권을 확인하지 못했다.
- 현재 버전과 일치하는 확정 피드백 또는 캐시 결과가 있다.
- 규칙과 임베딩이 충분한 근거로 같은 결론을 지지한다.
- 회차가 일시정지·종료·삭제됐다.

## 3. 실행 흐름

```text
START
  → validate_context
  → load_exact_feedback
  ├─ 적용 가능 → assemble_candidate
  └─ 없음
      → calculate_relevance
      → ambiguity_gate
      ├─ 명확함 → assemble_candidate
      └─ 모호함
          → load_recent_learning_flow
          → optional_similar_feedback
          → classify_ambiguous_content
  → validate_model_output
  → calculate_drift_risk
  → recommend_intervention
  → validate_versions_and_policy
  → END

오류·예산 초과·stale → safe_abort → END
```

`optional_similar_feedback`은 후속 기능이다. 동일 목표·콘텐츠의 정확한 피드백과 명확한 캐시를 먼저 사용한다.

## 4. Agent 상태

| 분류 | 필드 | 저장 조건 |
| --- | --- | --- |
| 범위 | `goalId`, `goalVersion`, `sessionId`, `runId` | 인증된 범위만 |
| 이벤트 | `requestId`, `eventId`, `navigationId` | 현재 실행 추적 |
| 콘텐츠 | `contentRef`, `contentHash`, `evidenceIds` | 원문 대신 요청 내 참조 |
| 흐름 | 제한된 최근 활동 요약, 현재 체류 후보 | 전체 방문 기록 금지 |
| 피드백 | 적용된 피드백 ID·버전·범위 | 다른 사용자 참조 금지 |
| 판단 | 후보 라벨, 근거 코드, 상태 조합 | Schema 통과 결과만 |
| 예산 | 도구·모델 호출 수, 재시도 수, 경과 시간 | 실행마다 초기화 |
| 버전 | 분석기·모델·Prompt·정책·추출기 버전 | 결과 재현에 필요 |

페이지 문단 원문, OCR 원문, 이미지, Authorization Header, API Key와 모델 원본 응답은 체크포인트 상태에 넣지 않는다.

## 5. 도구 계약

도구 이름은 설계 식별자다. 실제 함수명과 입력 Schema는 구현 Issue에서 확정한다.

| 도구 | 입력 | 출력 | 부작용 | 호출 제한 |
| --- | --- | --- | --- | --- |
| `profile_learning_goal` | 목표 원문 또는 수정 요청 | GoalProfile 후보 | 없음 | 목표 생성·수정 시 |
| `calculate_relevance` | 검증된 GoalProfile·문단 참조 | 라벨 후보·신호·근거 ID | 캐시 조회 가능 | 이벤트당 기본 1회 |
| `load_recent_learning_flow` | 인증된 `sessionId`, `runId` | 최근 3~5개 최소 요약 | 읽기만 | 모호한 흐름에만 |
| `load_user_feedback` | 사용자 범위·목표·콘텐츠 해시 | 정확 일치 피드백 | 읽기만 | 먼저 정확 일치 |
| `search_similar_feedback` | 범위가 제한된 임베딩 후보 | 소수의 검증된 사례 | 읽기만 | 후속 기능, Top-K 제한 |
| `classify_ambiguous_content` | 구조화 목표·근거·요약 | 구조화 LLM 후보 | 외부 모델 호출 가능 | 필요한 경우 1회 후보 |
| `calculate_drift_risk` | 관련성·체류·최근 흐름 | 결정적 흐름 상태 | 없음 | 시간 이벤트 갱신 시 |
| `recommend_intervention` | 흐름·설정·쿨다운 | 행동 제안 후보 | 없음 | 실제 실행 권한 없음 |

### 5.1 공통 도구 조건

- Server가 발급하거나 검증한 실행 범위 밖의 ID를 받지 않는다.
- 입력과 출력은 엄격한 Schema로 검증한다.
- 페이지 콘텐츠가 도구명, 대상 ID, 권한이나 호출 횟수를 바꿀 수 없다.
- 오류에는 입력 원문과 비밀 값을 복사하지 않는다.
- 읽기 도구도 사용자·목표·세션 범위를 강제한다.
- 취소 신호와 전체 Deadline을 모든 도구에 전달한다.

## 6. 모델 호출 계약

모델은 다음 값만 제안한다.

- `relevanceLabelCandidate`
- 설명용 `confidence`
- `contextStatus`
- 요청에 실제 존재하는 `evidenceIds`

모델은 `driftState`, `recommendedAction`, 레거시 `FocusState`, 시간, 캐시 TTL과 재시도 정책을 결정하지 않는다. System Prompt와 출력 Schema는 `prompt_guide.md`를 따른다.

## 7. 예산과 종료 조건

정확한 수치는 부하·품질 평가 전까지 설정 이름만 정의한다.

| 설정 | 의미 | 초과 시 처리 |
| --- | --- | --- |
| `AGENT_TOTAL_TIMEOUT_MS` | 전체 실행 Deadline | `UNAVAILABLE`, 무알림 |
| `AGENT_MAX_TOOL_CALLS` | 전체 도구 호출 상한 | 안전 종료 |
| `AGENT_MAX_LLM_CALLS` | LLM 호출 상한 | 이전 안전 단계 또는 보류 |
| `AGENT_MAX_RETRIES` | 일시 오류 재시도 상한 | 오류 코드 반환 |
| `AGENT_MAX_FLOW_ITEMS` | 최근 흐름 최대 개수 | 초과 항목 조회 금지 |
| `AGENT_MAX_EVIDENCE_ITEMS` | 모델에 전달할 근거 수 | 대표 근거만 사용 |
| `AGENT_MAX_INPUT_BYTES` | 구조화 입력 크기 | 요청 축소 또는 거절 |

다음 중 하나면 즉시 종료한다.

- 개인정보 검사 실패
- 사용자 범위 검증 실패
- 현재 `goalVersion`, `runId`, `navigationId` 불일치
- 회차 종료·삭제·일시정지
- 호출·재시도·시간 예산 초과
- 모델 출력 Schema 실패
- 필요한 근거가 더 이상 없음

## 8. Checkpoint와 재개

Checkpoint는 장시간 작업을 계속 점유하지 않고 다음 이벤트에서 재개하기 위한 최소 상태다.

저장 가능 후보:

- 실행 ID와 현재 Node
- 식별자·버전·원문 없는 상태 코드
- 콘텐츠·근거의 유효한 참조
- 이미 사용한 도구와 예산
- 대기 중인 사용자 질문과 만료 시각

저장 금지:

- 페이지·OCR 원문과 이미지
- 모델 원본 출력과 숨은 추론
- 임의 사용자 데이터
- Token·Cookie·Header

사용자 응답을 기다릴 때 실행 Thread를 유지하지 않는다. 질문 상태를 Server에 저장하고 피드백 이벤트로 새 실행을 시작한다. 재개 전 소유권, 최신 버전, 만료와 삭제 상태를 다시 확인한다.

## 9. 피드백 적용

정확히 일치하는 유효한 사용자 피드백은 모델 결과보다 우선한다. Agent는 다음을 임의로 확대하지 않는다.

- 한 문단의 수정 → 페이지 전체
- 한 페이지의 수정 → 전체 Domain
- 한 목표 버전의 수정 → 다른 버전
- 한 사용자의 수정 → 다른 사용자
- `계속 보기` → 목표 관련 정답

피드백 적용 뒤 관련 캐시와 대기 알림을 무효화한다. 상세 범위는 `feedback_personalization_spec.md`를 따른다.

## 10. 실패 처리

| 실패 | 상태 | 사용자 행동 | 재시도 |
| --- | --- | --- | --- |
| 모델 Timeout | 분석 오류 또는 `UNAVAILABLE` | 이탈 알림 없음 | 예산 내 제한적 |
| 출력 Schema 위반 | `MODEL_OUTPUT_INVALID` | 결과 미사용 | 교정 1회 후보 |
| 도구 권한 거부 | `UNAUTHORIZED_SCOPE` | 결과 미사용 | 범위 수정 전 금지 |
| 최근 흐름 조회 실패 | 부분 문맥 | 충분하지 않으면 보류 | 일시 오류만 |
| 피드백 조회 실패 | 개인화 미적용 표시 후보 | 오래된 피드백 추정 금지 | 제한적 |
| stale 감지 | `STALE_EVENT` | 현재 상태 변경 없음 | 새 이벤트로만 |
| 예산 초과 | `AGENT_BUDGET_EXCEEDED` | 무알림 | 같은 요청 자동 반복 금지 |

실패 뒤 다른 페이지의 마지막 판정을 현재 결과로 유지하지 않는다.

## 11. 관측과 감사

원문 없이 다음을 기록한다.

- Agent 실행 여부와 진입 이유 코드
- Node·도구별 호출 수와 처리 시간
- 캐시 적중, LLM 호출, 재시도와 종료 이유
- 사용한 모델·Prompt·정책·분석기 버전
- Schema 실패와 stale 폐기 수
- 피드백 적용 방식과 범위 코드
- 최종 상태와 근거 ID 개수

사용자·세션 참조는 가명화하며 완전 익명이라고 표현하지 않는다.

## 12. 검증 시나리오

- 명확한 관련 페이지는 Agent와 LLM 없이 처리된다.
- `SUPPORTING` 후보에서 최근 흐름을 최대 허용 개수만 조회한다.
- 페이지의 악성 지시가 도구 대상과 호출 순서를 바꾸지 못한다.
- 다른 사용자의 `sessionId`를 넣어도 도구가 데이터를 반환하지 않는다.
- 목표 수정·탭 이동·회차 종료 뒤 결과가 적용되지 않는다.
- 같은 요청이 순환 Node나 무한 재시도를 만들지 않는다.
- 출력에 존재하지 않는 `evidenceId`가 있으면 결과를 거절한다.
- Agent 장애가 타이머·기록·DOM 기본 분석을 중단시키지 않는다.
- 사용자 응답 대기 중 실행 Worker를 점유하지 않는다.
- Checkpoint에 원문·이미지·모델 원본 응답이 저장되지 않는다.

## 13. 팀 합의 필요

- LangGraph 사용 여부와 Checkpoint 저장소
- Agent 실행과 일반 분석 API를 같은 Worker에서 처리할지
- 도구별 실제 입력·출력 Schema
- 이벤트당 시간·도구·LLM·재시도 예산
- 유사 피드백 검색의 최초 출시 포함 여부
- 부분 도구 실패에서 사용할 수 있는 안전한 결과 범위
- Checkpoint TTL과 사용자 삭제 전파 방식

## 관련 문서

- [AI Prompt와 Agent 신뢰 경계](prompt_guide.md)
- [AI 분석 규칙](analysis_rules.md)
- [사용자 피드백·개인화 명세](feedback_personalization_spec.md)
- [AI 상태 모델](state_model.md)
- [AI 평가와 회귀 검증 명세](evaluation_spec.md)
