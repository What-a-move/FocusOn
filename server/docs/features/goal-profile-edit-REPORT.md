# 결과 리포트 — 목표 범위(goalProfile) 수정·다시 만들기 (#314)

## 변경 요약

- `UpdateGoalRequest.goalProfile`: 통째 덮어쓰기. 같은 문구를 다시 보내면 범위를 지우지 않는다.
- `POST /api/v1/goals/{goalId}/profile`: 저장된 문구로 AI 범위를 새로 만든다. AI 호출은 잠금·트랜잭션 밖, 실패 시 기존 범위 유지.
- `GoalService.requestProfile`: #327의 확장 호출을 실패 코드를 돌려주는 형태로 나눴다(생성은 기존처럼 실패 시 범위 없이 저장).
- `GoalEntity.replaceProfile`, `docs/API_CONTRACT.md` 목표 절.
- 할 일 상태만 바꾸는 PATCH도 사용자 잠금을 잡는다(CodeRabbit 지적, ERROR 1).

## 테스트

- `GoalProfileEditApiTests`: PATCH 범위만·문구+범위·같은 문구 재전송 / 형식 위반 6종 400·기존 값 유지 / 다시 만들기 성공·잘못된 AI 응답 503·시간 초과 504·기존 범위 유지·범위 없던 할 일 다시 만들기 / 활성 세션 409(상태 변경은 허용), 다른 사용자 404, 미인증 401.
- `GoalUpdateDeleteApiTests.postgresTodoOnlyUpdateWaitsForUserLock`: 상태만 바꾸는 요청이 사용자 잠금을 기다림(PostgreSQL).
- 기존 목표 테스트 그대로 통과. H2 전체 442개 통과(PostgreSQL 전용 18개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway V1~V32 + validate)에서 목표 패키지 + `SessionApiTests` 68개 통과(1개 생략).

## 남은 제한

- Goal AI 호출은 #327 그대로 요청당 60초 제한·최대 2회 재시도라, AI가 느리면 다시 만들기 응답이 오래 걸릴 수 있다. 본문 지연까지 포함한 전체 마감은 별도 보강 후보다.
- 정본 Notion API 명세는 연결 계정에서 열리지 않아 Desktop 요청과 `docs/API_CONTRACT.md` 기준으로 구현했다.
