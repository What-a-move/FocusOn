# 종료 후 학습 정리 근거 저장 QA

- [이슈 #392](https://github.com/What-a-move/FocusOn/issues/392), [기획서](learning-evidence-cutoff-PLAN.md), [독립 리뷰](learning-evidence-cutoff-code-review.md)
- 기준은 `dev` `52ce4c7`과 이 이슈 변경분이다.
- 전체 Gradle 빌드와 Checkstyle 통과. H2 전체 551건, 실패 0건, 생략 18건.
- 격리 PostgreSQL 17에서 분석·종료·개입·학습 정리 관련 233건 전부 통과. Flyway V40 적용 성공을 확인했다.

## 확인한 동작

- 종료 전에 접수한 분석의 검증된 정제 근거를 30초 내 저장한다. 목표 시간을 달성한 뒤 종료하는 세션에도 같은 구간이면 적용한다.
- 분석 API와 분석 이력의 기존 `UNCERTAIN` 응답을 확인했다. 정제 근거가 공개 분석 응답에 포함되지 않고 Worker 입력에서 읽히는 것을 검증했다.
- 마감 뒤 완료, 쓰기 중 마감 경과, 동의 철회·재동의, 일시정지·재개, 목표 변경, 새 제외 관측, 다른 목표의 판정 및 ABANDONED에서는 저장하지 않는다.
- Worker가 정리 행을 먼저 점유하면 지연 근거는 결과에 들어가지 않는다. 실행 구간 정보가 없는 이전 정리 행에도 추가하지 않는다.

## 연동 및 배포

- 실제 Desktop·AI 연동 QA와 Worker의 생성 직전 동의 재확인은 별도 작업이다.
- V39를 사용하는 [#390](https://github.com/What-a-move/FocusOn/pull/390)과 Flyway 적용 순서를 제출 시 다시 확인한다. 이 QA는 `dev` V38에서 이 작업의 V40을 적용한 결과다.
