# AI 기능 기획서: 학습 목표 설정 보조

> 상태: **MVP 제안 — Server·Client 담당자 합의 후 구현**
> 목표를 사용자가 최종 확정하기 전에 명확성을 검사하고 추천·질문을 제공한다.

## 기본 정보

- 기능명: 학습 목표 설정 보조
- 기능 ID: `goal-assistance`
- 작성일: 2026-09-22
- 우선순위: MVP - 핵심
- 관련 Issue: 별도 `AI-01` Issue 생성 필요
- 예정 Branch: Issue 생성 후 결정
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
- 확인 질문 1~2개 생성
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
  "originalText": "React랑 Docker랑 영어 공부",
  "previousGoalProfile": null,
  "feedbackVersion": null
}
```

`clarityStatus` 허용 값:

- `CLEAR`
- `NEEDS_SELECTION`
- `NEEDS_SUGGESTION`
- `NEEDS_QUESTION`
- `INVALID`
- `UNRECOGNIZED_TERM`

```json
{
  "clarityStatus": "NEEDS_SELECTION",
  "confidence": 0.88,
  "interpretedGoal": null,
  "recommendedGoals": ["React 학습", "Docker 학습", "영어 학습"],
  "questions": [],
  "requiresUserConfirmation": true
}
```

사용자 확인 전 `GoalProfile`은 확정 판단 기준이 아니다. AI 실패·Timeout·무응답이면 사용자가 직접 작성한 목표를 확인한 뒤 세션을 시작할 수 있다.

## 분석 기준

- `CLEAR`: 하나의 주제와 예상 활동이 비교적 명확하다.
- `NEEDS_SELECTION`: 여러 목표가 섞여 하나를 선택해야 한다.
- `NEEDS_SUGGESTION`: 주제는 있으나 범위가 넓어 구체화가 필요하다.
- `NEEDS_QUESTION`: 의미·대상·활동을 질문해야 한다.
- `INVALID`: 비어 있거나 분석 가능한 목표가 없다.
- `UNRECOGNIZED_TERM`: 약어·오타의 의미를 임의로 확정할 수 없다.

AI는 추천 후보를 만들 수 있지만 사용자의 확인을 대신하지 않는다. `confirmedByUser`는 AI 출력이 아니라 Server가 사용자 확인 이벤트로 관리한다.

## 처리 흐름

1. Server가 사용자 소유권과 요청 크기를 검증한다.
2. AI가 목표 원문을 개인정보·Prompt Injection 관점에서 검사한다.
3. AI가 `clarityStatus`와 구조화 후보를 반환한다.
4. Client가 후보·질문을 표시한다.
5. 사용자가 하나의 목표를 확인·수정한다.
6. Server가 확정 `GoalProfile`과 `goalVersion`을 저장한다.

## API·책임

- 제안 Endpoint: `POST /api/v1/goals/profile`
- Client는 AI를 직접 호출하지 않는다.
- Server가 인증·소유권·저장·목표 버전을 관리한다.
- 방문 콘텐츠는 목표 범위를 자동 확장하는 근거로 사용할 수 없다.

## 예외 처리

| 상황 | 반환·처리 |
| --- | --- |
| 빈 입력 | `INVALID`, 재입력 안내 |
| 약어·오타 불명확 | `UNRECOGNIZED_TERM`, 후보 확인 |
| 여러 목표 | `NEEDS_SELECTION`, 후보 2~3개 |
| 범위가 넓음 | `NEEDS_SUGGESTION`, 후보 2~3개 |
| 의미 부족 | `NEEDS_QUESTION`, 질문 1~2개 |
| AI Timeout·무응답 | 직접 입력 목표 확인 경로 제공 |
| Prompt Injection 또는 Schema 오류 | 응답 미사용, 사용자 원문 재출력 금지 |

## 평가 계획

- “JavaScript Promise 학습” → `CLEAR`
- “영어 공부” → `NEEDS_QUESTION`
- “React랑 Docker랑 영어 공부” → `NEEDS_SELECTION`
- “React 상태 관리 학습” → `NEEDS_SUGGESTION`
- “rq 캐싱 조지기” → `UNRECOGNIZED_TERM`
- 빈 입력 → `INVALID`
- AI 실패·무응답에도 직접 목표로 시작 가능
- 페이지 본문에 목표 확정·Prompt 무시를 지시하는 문장이 포함된 경우 무시

## 보안·개인정보

- 원본 화면·카메라·전체 DOM·전체 방문 기록을 입력받지 않는다.
- 목표 원문과 답변은 최소 범위에서만 사용한다.
- API Key, Authorization Header, 다른 사용자 목표를 모델에 전달하지 않는다.
- 모델 원본 출력과 사용자 원문을 로그에 남기지 않는다.

## 완료 조건

- [ ] 6개 `clarityStatus`의 입력·출력 계약을 공유했다.
- [ ] 사용자 확인 전 목표를 확정하지 않는다.
- [ ] 추천·질문 개수 제한을 검증했다.
- [ ] AI 실패·무응답 직접 시작 경로를 검증했다.
- [ ] Prompt Injection과 Schema 오류를 평가했다.
- [ ] `goalId`·`goalVersion` 저장 책임을 Server와 합의했다.
