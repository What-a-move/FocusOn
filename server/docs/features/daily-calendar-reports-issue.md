# GitHub Issue #208 등록 본문

제목: **[Server] 실제 학습 기록 기반 일간·달력 리포트 조회 API 구현**

라벨: `area:server`, `type:feature`, `priority:mvp-basic`

사용자 승인으로 [#208](https://github.com/What-a-move/FocusOn/issues/208)을 생성했다. 아래는 생성 시 등록한 본문이며 이후 진행 상태는 GitHub 이슈 본문을 기준으로 관리한다.

---

## 작업 유형

- [x] 기능 개발

## 담당 영역

- [x] Server

## 작업 목적

Desktop 홈 집중 달력과 날짜별 학습 기록에 실제 종료 세션 데이터를 제공한다. 사용자 담당 ⑦ 리포트이며 AI 연결 없이 동작한다.

## 작업 내용

- `GET /api/v1/reports/calendar?month=YYYY-MM`
- `GET /api/v1/reports/daily?date=YYYY-MM-DD`
- 본인의 ENDED·확정 ABANDONED만 고정 studyDate로 집계한다. 마지막 검증 누적 시간과 저장 목표 스냅샷을 사용한다.
- 달력 활동일은 하루 합계60000ms 이상이다. 일간 항목은 sessionId/goalTextSnapshot/startedAt/endedAt/accumulatedActiveMs를 유지한다.
- 기존 인증·기기·성공/오류 응답과 report 패키지를 재사용하고 필요한 조회 인덱스를 검토한다.

## 사용자 흐름

1. 인증된 Desktop에서 월 달력을 조회한다.
2. 날짜를 선택해 종료 세션의 목표·시각·확정 학습 시간을 조회한다.
3. 기록 없는 날짜는 빈 목록과 실제 집계0을 반환한다. 진행 중 시간·가짜 분석 수치를 포함하지 않는다.

## 완료 조건

- [ ] 두 API 정본과 실제 Desktop 파서 계약 대조.
- [ ] 본인·기기 검증과 타 사용자 데이터 격리.
- [ ] 빈 날짜, 모든 세션 상태, 활동일 경계, 여러 세션 합산, 자정·시간대·윤년 검증.
- [ ] 일간 목록과 합계 및 달력 합계 일치.
- [ ] 격리 리뷰·PostgreSQL·Backend build/Checkstyle·HTTP QA와 기록.

## 진행 단계

- [x] 0. 기존 코드·문서·이슈 사전 조사 (Notion 최신 확인은 남음)
- [ ] 1. 승인 후 이슈 생성·라벨 확인
- [x] 2. 기획서 초안 작성
- [ ] 3. 기획 검토·Notion 최신 계약 대조
- [ ] 4. 지적 및 계약 충돌 보완
- [ ] 5. 재검토
- [ ] 6. 구현 승인
- [ ] 7. 최신 dev 기준 이슈 브랜치 생성
- [ ] 8. 구현
- [ ] 9. 격리 코드 리뷰
- [ ] 10. 테스트·QA
- [ ] 11. 별도 승인 후 PR 생성·CI/리뷰·문서 반영

## 예상 변경 파일

`server/src/main/java/com/capstone/backend/report/{controller,service,dto}/`

`server/src/main/java/com/capstone/backend/session/persistence/LearningSessionRepository.java`

필요 시 새 조회 인덱스 migration(최신 dev와 PR207 V19 재대조), report 테스트, 관련 Backend 문서.

## 예정 Branch

`feat/{issue-number}-daily-calendar-reports`

## 관련 문서

- `server/docs/features/daily-calendar-reports-PLAN.md`
- `docs/API_CONTRACT.md`
- [Notion 일간](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7)
- [Notion 달력](https://app.notion.com/p/3e70b6e5e64c800298a7c62768d10853)
- 기존 #201/PR202: 세션 summary 재사용, 이번 범위에서 재구현하지 않음.
- #206/PR207: local-events·learning-summary 조회와 분리. migration 번호 충돌 검토만 필요.

## 주의사항 및 결정 필요 사항

최신 정본 접근은 미완료다(일간 connector404, 대체 도구 실행 오류). Desktop 전용 권한·달력 날짜 배열 범위·일간 정렬은 기획서 권고안을 정본과 대조해 확정한다. API를 임의 변경하지 않는다. AI·개인화 휴식·세션 명령·Frontend/shared-types는 제외한다. 사용자 개발 DB5432/기존8080·IDE 파일·stash는 보존한다. PR 생성/병합/이슈 종료는 각 별도 승인이다.
