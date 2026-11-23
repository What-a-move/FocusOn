# 결과 리포트 — 모든 목표 세분화와 goalProfile 응답·수정 (#314)

## 변경 요약

- `GoalAiClient.profile`: `/internal/v1/goals/profile`, 8초 제한, 재시도 없음. 경로별 요청 공통화.
- `GoalProfileExpander`: AI 실패·형식 오류를 모두 null로 바꾼다.
- `GoalService.create`·`update`: 트랜잭션을 나눠 AI 호출을 잠금 밖으로 뺐다. 문구 변경 시 재확장, 직접 보낸 `goalProfile` 우선, 그 사이 바뀐 목표에는 결과를 붙이지 않는다. 같은 문구를 다시 보냈는데 세분화가 비어 있으면 다시 시도한다.
- 응답 `GoalResponse.goalProfile`(저장값을 다시 검증해 싣고, 깨진 값은 null).
- `docs/API_CONTRACT.md` 목표 절 갱신.

## 테스트

- `GoalProfileExpandTests`: 직접 입력 세분화·응답·DB·단건 조회 / AI 실패 시 201 + null / 문구 수정 재확장, 문구+직접 세분화는 AI 미호출, 형식 위반 400·기존 값 유지 / 세분화 중 문구가 바뀌면 결과 버림 / 활성 세션 중 세분화 변경 409, AI 확인 목표는 세분화 AI 미호출.
- `GoalAiClientTests.profileUsesItsOwnInternalPath`.
- `GoalProfileTests`는 이름·설명만 새 동작에 맞췄다.
- H2 전체 431개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과. PostgreSQL 확인은 #315 리포트에서 함께 한다.

## 남은 제한

- AI `POST /internal/v1/goals/profile`은 dev AI에 아직 없다. 배포 전까지 직접 입력 목표의 `goalProfile`은 null이다. 요청·응답 형식은 가정이므로 AI 담당 확인이 필요하다.
- `POST /goals`는 세분화를 기다리므로 최대 약 8초 늦어질 수 있다.
