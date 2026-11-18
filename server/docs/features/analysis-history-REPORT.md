# 세션 분석 이력 조회 결과

- 작업일: 2026-11-18
- 이슈: [#272](https://github.com/What-a-move/FocusOn/issues/272)
- 브랜치: `feat/272-server-analysis-history`
- 기획서: [분석 이력 조회 API](analysis-history-PLAN.md)
- 코드 점검: [변경 검토](analysis-history-code-review.md)

## 구현

`GET /api/v1/sessions/{sessionId}/analysis-runs`에서 인증된 Desktop에 본인 세션의 저장된 분석 결과를 제공한다. `observedAt`과 저장 ID 오름차순으로 정렬하고 기존 분석 응답 DTO로 매핑한다. 분석 기록이 비었으면 `data: []`를 반환한다. 기존 V14 이력 인덱스를 사용한다.

## 검증

| 실행 범위 | 결과 |
| --- | --- |
| Java 25 기준 `./gradlew build --no-daemon`, Checkstyle 포함 | 전체 345건, 실패 0건, 생략 17건 |
| PostgreSQL 17에서 Flyway 적용 및 Hibernate `validate`, Backend CI PostgreSQL 테스트 범위 | 전체 206건, 실패 0건, 생략 0건 |

- 실제 HTTP로 분석 결과를 저장한 뒤 Bearer만으로 이력을 조회해 기존 결과 필드와 일치하는지 확인했다.
- 종료 세션에서 Desktop과 Extension의 관측을 합쳐 정렬했다. 같은 시각의 순서, 결과별 `policyVersion`, 원문·내부 메타데이터 미노출을 확인했다.
- 빈 목록, 미인증 401, Extension 403, 없거나 타인 소유인 세션 404를 확인했다.
- 테스트에서는 AI 클라이언트를 대체했다. 실제 Desktop 표시 검증은 이후 클라이언트 연결 때 수행한다.
