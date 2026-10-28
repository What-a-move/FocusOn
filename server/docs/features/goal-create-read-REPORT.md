# 학습 목표 저장·조회 작업 결과

- 관련 Issue: [#87](https://github.com/What-a-move/FocusOn/issues/87)
- Branch: `feat/87-goal-create-read`
- 계획: [goal-create-read-PLAN.md](goal-create-read-PLAN.md)
- 상태: 수동 저장·조회 구현 및 자동 검증 완료, AI 확인 이력 연동과 최종 검증 미완료

## 구현 결과

- `POST /api/v1/goals`: 인증된 사용자의 수동 확정 목표를 저장하고 `201`을 반환한다. 문구는 앞뒤 공백을 제거한 후 2~40자를 검사한다.
- `GET /api/v1/goals/{goalId}`: 본인의 삭제되지 않은 목표를 조회한다. 다른 사용자 소유 및 유효하지 않은 ID도 `404 GOAL_NOT_FOUND`로 처리한다.
- dev의 V5·V6과 중복되지 않도록 목표 migration을 `V7__create_goals.sql`로 변경했다.
- 사용자 승인에 따라 수동 확정에 포함된 명확화 ID는 검증·저장하지 않고 목표만 저장한다. AI 확정은 확인 이력을 검증할 수 없어 임시 `503`이다.

## 검증 결과

- `GoalApiTests`: 수동 저장·본인 조회·다른 사용자 조회 거부·무인증·문구 길이 경계·공백·확정 출처 누락·비정상 ID·검증 불가 명확화 요청을 MockMvc로 확인했다.
- 목표 API 테스트 및 Main/Test Checkstyle: 통과. OAuth 교환은 테스트 대체 구현을 사용했다.
- PostgreSQL 임시 DB: dev 통합 후 Flyway V1~V7 적용과 목표 API MockMvc 테스트를 확인하고 임시 DB를 삭제했다. 수동 ID 허용 변경은 이후 H2 테스트에서 확인했다.
- 수동 목표에 임의의 대화 ID를 포함해도 201로 저장되고 이후 조회되며, AI 확정은 503인 것을 MockMvc로 확인했다.
- 실행 JAR 빌드: `bootJar` 성공. 전체 테스트를 포함한 빌드는 기존 닉네임 동시성 테스트 실패로 통과하지 못했다.
- 사용자 확인: Swagger에서 두 API를 테스트하고 엔드포인트와 HTTP 메서드가 Notion 명세와 일치하며, DB에 `goals` 테이블이 생성된 것을 확인했다. 요청·응답 세부 값과 테스트 환경은 전달받지 않았다.
- 위 수동 확인은 사용자의 보고이며, 별도의 실제 Google 로그인 검증 결과는 확인하지 못했다.

## 남은 작업

- AI 명확성 확인 이력 검증·정상 저장 연동 및 전체 테스트 실패 원인 확인.
- 기존 목표 V5가 적용된 DB는 dev V5와 이력이 충돌하므로 데이터 보존 여부를 확인한 후 별도 처리한다.
- 수동 테스트의 구체적인 요청·응답과 환경 기록, PR 전 최신 migration 번호 확인.
- 목표 수정·삭제, 명확성 확인 API 자체와 학습 세션은 별도 범위다.
