# 결과 리포트 — 화면 분석 학습 정리 근거 저장 연결 (#379)

## 변경 요약

- `report.service.LearningEvidenceRecorder`: 정규화·해시·중복 확인 후 `learning_review_evidences` 저장.
- `AnalysisRunStore.complete`: 최종 판정에 남은 근거만 저장(완료 재검증 통과분).
- `LearningReviewEvidenceRepository`: `existsBySessionIdAndDedupeHash`, `deleteExpired`.
- `AnalysisRunRetention`: 종료 후 90일 지난 세션 근거 삭제.

## 테스트

- `AnalysisApiTests.verifiedReviewEvidenceIsStoredOncePerConcept`: 대소문자·공백만 다른 개념 1건, 다른 개념 1건 저장, 유형·출처·관측 시각.
- `reviewEvidenceIsDroppedWhenSessionStopsOrGoalIsNotMatched`: AI 호출 중 일시정지, 스냅샷 밖 목표 매칭, 제외 상태에서 미저장.
- `retentionRemovesReviewEvidenceNinetyDaysAfterSessionEnds`: 90일 경계 전 유지·경계에서 삭제·최근 세션 유지.
- 실제 HTTP 테스트(`actualHttpTracesAnalysisThroughFakeAi`): 정상 RELATED는 1건 저장, AI 503·동의 철회는 미저장, 응답·이력·로그에 라벨 없음.
- H2 전체 `./gradlew build` 통과(Checkstyle 포함), 임시 PostgreSQL 17에서 `AnalysisApiTests`·리포트 테스트 통과.

## 남은 제한

- 세션 종료 뒤 완료된 분석의 근거는 저장하지 않는다. Desktop NEXT_TASK의 "종료 후 29·30초 저장" QA는 서버 기준과 다르므로 노트 전용 경로를 따로 정해야 한다.
- 동의 철회 뒤 이미 저장된 근거 처리 범위는 팀 확정 필요.
