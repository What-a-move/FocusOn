# 목표 AI 명확성 확인 오류 기록

- Issue: #99
- 발생한 오류와 해결 과정만 기록하며 인증정보·목표 원문은 기록하지 않는다.

## 1. 목표 확인 호출이 503으로 실패 (2026-11-10)

- 증상: `/api/v1/goals/clarify`가 AI 호출 전에 `GOAL_ANALYSIS_UNAVAILABLE`(503)을 반환했다.
- 원인 1: Java `HttpClient`의 기본 `Upgrade: h2c` 헤더 때문에 uvicorn이 본문을 버리고 422를 반환했다. → `GoalAiClient`를 HTTP/1.1로 고정하고 `GoalAiClientTests`를 추가했다.
- 원인 2: `.env`로 읽은 `FOCUSON_GOAL_AI_BASE_URL`이 `@Value("${focuson.goal-ai.base-url}")`와 연결되지 않아 빈 값이었다. → `application.properties`에서 명시적으로 연결하고 `.env.example`에 키를 추가했다.
- 마이그레이션: dev의 `V11__single_active_extension.sql`과 번호가 겹쳐 `V21`로 변경했다.

## 2. 남은 문제 (AI 담당자 수정 대기)

- AI `CLEAR`의 `normalizedGoalText`가 40자를 넘는다(실측 58자·약 48자). `AI_CONFIRMED` 저장은 40자 이하·AI 값과 동일해야 해서 저장할 수 없다.
- AI가 `토익 점수 올리기`에서 평문 500을 반환한다(같은 입력이 이전에는 200). 서버는 503으로 변환한다.
