# 결과 리포트 — 세션 중 할 일 교체 (#320)

## 변경 요약

- `AddGoalRequest.replaceGoalId`(선택). 있으면 `SessionService.replaceGoal`로 같은 자리를 덮어쓰고 `goalVersion` +1.
- `SessionGoalEntity.replaceWith`: 새 할 일의 문구·세분화 사본으로 바꾼다. 사본이 없던 옛 세션은 교체 결과를 첫 사본으로 저장한다.
- 목표 수정·삭제 잠금: `LearningSessionRepository.legacyReferenceByActiveSession`(사본 없는 옛 세션만 시작 목표 기준) + 기존 사본 기준. 쓰이지 않게 된 `existsByUserIdAndGoalIdAndStatusIn` 삭제.
- `docs/API_CONTRACT.md` 세션 절.

## 테스트

- `SessionApiTests.replaceSessionGoalOverwritesSlotAndReleasesOldGoal`: 교체·같은 교체 재전송(200, 버전 그대로) / 오래된 버전 409 / 이미 있는 할 일·세션에 없는 대상·같은 ID 400 / DB 자리 확인 / 빠진 할 일 수정 200·새 할 일 수정 409 / 시작 할 일(1번 자리) 교체 뒤 삭제 가능 / 활성 세션 응답 버전·목록.
- 기존 추가 테스트(`orderedGoalsStartAndAddWithoutChangingSessionCommandVersion`)와 목표 잠금 테스트 그대로 통과.
- H2 전체 445개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway V1~V32 + validate)에서 목표·개입 패키지 + `SessionApiTests`·`AnalysisApiTests` 205개 통과(1개 생략).

## 남은 제한

- 세션 시작 목표(`goalTextSnapshot`)는 리포트 제목이라 교체해도 바뀌지 않는다.
- Notion API 명세에 `replaceGoalId`가 아직 없다. 팀 「계약 먼저」 원칙에 따라 문서 오너 반영 뒤 머지한다.
