# 결과 리포트 — 분석 기록 딴짓 종류(offTaskCategory) (#381)

## 변경 요약

- `AnalysisDtos.OffTaskCategory`, `Response.offTaskCategory`.
- `OffTaskClassifier`: 고정 도메인·앱 이름 목록, 상위 도메인 순 조회, 미분류 OTHER.
- `AnalysisRunEntity.offTaskCategory`·`categorize`, `AnalysisRunStore.complete`에서 UNRELATED만 분류.
- V37 마이그레이션, `docs/API_CONTRACT.md` 분석 절.

## 테스트

- `OffTaskClassifierTests`: www 제거, 하위 도메인, 네이버 하위 서비스별 구분, 비슷한 이름(`notyoutube.com`) OTHER.
- `AnalysisApiTests.offTaskCategoryIsSetOnlyForUnrelated`: DOM host·OCR 앱·미분류 OTHER, RELATED·UNCERTAIN null, 응답·이력·DB 일치, 이력에 host·앱 이름 없음.
- `AnalysisMigrationTests`: RELATED 행 카테고리·목록 밖 값 CHECK 거절, 허용 컬럼 목록 갱신.
- `./gradlew build`(H2 전체 + Checkstyle) 통과, 임시 PostgreSQL 17에서 분석·마이그레이션·리포트·개입 228개 통과.

## 남은 제한

- Desktop 동의 상세 v1.1의 "딴짓 종류 저장" 문장은 Desktop 보류 항목이라 함께 반영해야 한다.
- 목록은 코드 상수다. 추가·조정은 PR로 한다.
