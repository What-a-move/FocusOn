# Backend 기능 기획서: 일간·달력 리포트

## 기본 정보

- 기능 ID: `daily-calendar-reports`
- 작성일: 2026-11-10
- 상태: 사용자 기준 확정·구현 승인 후 로컬 구현/검증 중. Notion 최신 계약 재확인 필요.
- 우선순위: MVP 기본
- 관련 Issue: [#208](https://github.com/What-a-move/FocusOn/issues/208). 기존 #201/PR #202 세션 summary와 구분한다.
- 예정 Branch: `feat/208-daily-calendar-reports` (최신 dev에서 분기)
- 확인 기준: 원격 dev `b3d680d`(#207 병합·V20), `docs/API_CONTRACT.md`, `apps/desktop/electron/shared/report-contract.cts`.
- 정본: [일간](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7), [달력](https://app.notion.com/p/3e70b6e5e64c800298a7c62768d10853).

2026-11-10 원격 dev fetch와 기존 이슈·열린 PR 검색을 수행했다. 두 API 전용 Backend 이슈·구현은 발견하지 못했다. #201은 summary, #206/PR #207은 local-events·learning-summary 조회이므로 중복하지 않는다. 일간 Notion fetch는 object_not_found404, 대체 UI 도구는 실행 초기화 오류였다. 이 문서는 로컬 확정 기록과 실제 소비자 파서에 근거한 검토안이며 정본 최신 확인 완료를 주장하지 않는다. 사용자가 요청한 CLI 읽기를 위해 `command -v notion`도 확인했으나 실행 파일은 찾지 못했다.

## 목적·범위

실제 종료 세션에서 월별 학습 활동일과 날짜별 학습 기록을 조회한다. AI 호출, 개인화 휴식 #198, 주간 리포트, 세션 명령, summary 재구현, 학습 정리 생성, Frontend/shared-types 수정은 제외한다. 데이터가 없는 날의 0은 확정 집계 결과이며 분석·집중 성공률 등 측정하지 않은 수치를 만들지 않는다.

## API 정의

두 API 모두 `Authorization: Bearer` 및 기존 `X-Device-Id` 필수 요청 검증을 재사용한다. 2026-11-10 사용자 승인으로 Desktop 전용, 연결 Extension은403 `SESSION_QUERY_FORBIDDEN`으로 확정했다. 사용자 ID를 query로 받지 않는다. HTTP200 성공은 공통 `{ "data": ... }`, 임의 `meta.requestId`를 추가하지 않는다. Notion 두 정본은 재시도에서도404여서 현재 로컬 계약·Desktop 파서 기준 구현을 사용자가 승인했다. 정본 동기화/최신 확인은 남았다.

### GET /api/v1/reports/calendar?month=YYYY-MM

`month` 필수, 고정 4자리 연도·2자리 월 및 실재 월 검증. 조회 시 세션 생성 당시 `studyDate`를 사용한다.

```json
{"data":{"month":"2026-11","studyDayCount":1,"days":[{"date":"2026-11-01","totalActiveMs":60000,"sessionCount":1,"hasStudyActivity":true}]}}
```

예제의 `days`는 필드 설명을 위해 한 날짜만 보였다. 사용자 승인으로 월의 모든 날짜를 오름차순으로 반환하고 기록 없는 날은 `totalActiveMs:0, sessionCount:0, hasStudyActivity:false`로 채운다. `studyDayCount`는 `hasStudyActivity:true` 날짜 수다. 0ms 종료 세션도 `sessionCount`에는 포함한다.

### GET /api/v1/reports/daily?date=YYYY-MM-DD

`date` 필수, 고정 형식 및 STRICT 달력 검증(윤년 포함). 날짜·월의 연도는0001~9999이다. 사용자 승인 정렬은 `startedAt DESC, id DESC`로 동일 시작 시각에도 결정적이다.

```json
{"data":{"date":"2026-11-01","totalActiveMs":60000,"sessions":[{"sessionId":"1","goalTextSnapshot":"Spring 학습","startedAt":"2026-11-01T01:00:00Z","endedAt":"2026-11-01T01:01:00Z","accumulatedActiveMs":60000}]}}
```

빈 날짜는 HTTP200, `totalActiveMs:0, sessions:[]`. 항목은 기존 계약의 다섯 필드를 그대로 쓴다. `endedAt`은 공개 타입 `string|null`을 유지하며 null을 서버 현재 시각으로 채우지 않는다. summary의 `goalSnapshot`과 이름을 통일하지 않는다. cursor/limit은 현 소비자에 없어 임의 추가·목록 절단하지 않는다. 한 날짜 조회 범위를 유지하고 대량 기록 검증을 수행한다.

### 오류

- query 누락·빈 값·형식 오류·존재하지 않는 날짜: HTTP400 `VALIDATION_ERROR`.
- 토큰 만료·누락·기기/사용자 불일치: 기존 Security 오류 계약 유지.
- Extension 요청: 사용자 승인 기준 HTTP403 `SESSION_QUERY_FORBIDDEN`, 새 오류 코드 없음.
- 기록 없음은 404가 아니다.

## 집계·데이터 설계

- `learning_sessions`를 원천으로 사용한다. 사용자·고정 `studyDate`·최종 상태를 DB query에서 제한한다.
- 집계 대상은 `ENDED`, 서버 확정 `ABANDONED`뿐이다. RUNNING/PAUSED/AUTO_PAUSED/AWAITING_END_CONFIRMATION/ENDING을 제외한다. GET이 자동 종료를 수행하지 않는다.
- 시간은 마지막 서버 검증 `accumulatedActiveMs`를 그대로 합산한다. 벽시계 차이·목표 시간으로 추정하지 않는다. 자정 통과 세션을 분할하지 않고 시간대 변경으로 날짜를 이동하지 않는다.
- 하루 총합 `>=60000ms`이면 활동일이다. 한 세션 1분 기준이 아니라 여러 세션의 합계다.
- 목표는 저장된 `goalTextSnapshot`을 사용한다. 현재 goals 텍스트 수정과 무관하며 목표 삭제·변경으로 과거 리포트가 바뀌지 않아야 한다.
- 달력은 월 시작~다음 월 시작 미만 범위에서 날짜별 SUM/COUNT projection을 한 번 조회한다. 일간은 필요한 다섯 필드 projection을 한 번 조회하고 응답 목록에서 합계를 계산해 동시 종료 시 목록과 합계가 어긋나지 않도록 한다. 원본 분석·개입 테이블과 join하지 않는다.
- 새 집계 테이블·배치·캐시는 도입하지 않는다. 합계는 long 범위를 사용하고 PostgreSQL SUM 결과 타입 변환을 검증한다.
- 최신 dev는 V20까지다. V21에 `(user_id, study_date)` 조회 인덱스를 추가했다. V8의 기존 인덱스는 유지한다. PR 전 migration 번호를 다시 확인한다. 기존 migration 수정·사용자 DB 초기화는 금지한다.
- 원문·URL·AI 전문을 읽거나 저장하지 않는다. 목표 스냅샷은 응답 데이터로만 제공하고 로그에 남기지 않는다.

## 계층과 설계 근거

`ReportController → ReportService → LearningSessionRepository projection → PostgreSQL`로 구성한다. 기존 `report` 패키지·공통 응답·Security를 재사용한다. 조회 전용 transaction으로 처리하고 세션 잠금을 추가하지 않는다.

API 설계 원칙 적용: 기존 두 경로 유지, 각 조회 목적 분리, 공통 성공/오류 유지, 사용자·기기 검증 재사용, 월/일 범위로 제한, 콘텐츠 원문 비사용. 계약 확인 전 새 필드·공개 오류·pagination은 확정하지 않는다.

## 검증·완료 조건

- [ ] 최신 Notion 두 API와 호출자·빈 달력 형태·정렬·공통 래퍼 대조, 충돌은 구현 전에 정리.
- [ ] 제목·라벨·본문을 제시하고 이슈 생성 승인, 기획 검토·구현 승인 후 이슈 브랜치 생성.
- [ ] 두 API 정상·빈 기록·인증/기기·타인 격리·Extension 정책 검증.
- [ ] 일간 목록 합계와 달력 합계 일치, 59,999/60,000 경계 및 여러 짧은 세션 합산 검증.
- [ ] 모든 상태 포함/제외, ABANDONED 검증값, 자정/시간대/월말/윤년/동일 시작시각 검증.
- [ ] 목표 변경에도 snapshot 보존, null endedAt 계약, 많은 날짜 기록·합계 타입 검증.
- [ ] 격리 PostgreSQL Flyway+validate·인덱스 업그레이드 보존 검증. DB5432·서버8080은 건드리지 않음.
- [ ] 별도 컨텍스트 리뷰, Backend build/Checkstyle, HTTP QA, Desktop 파서와 정적 계약 비교.
- [ ] 실제 앱 연결 검증 여부를 분리 기록하고 CONTEXT/NEXT_TASK·결정·QA/REPORT 갱신.
- [ ] PR 생성·병합·이슈 종료 각각 별도 승인.

## 현재 진행

사용자 재개 지시 후 최신 dev에서 `feat/208-daily-calendar-reports`를 생성했다. 공개 정책 세 가지를 추가 승인받아 두 API·조회 projection·V21·회귀 테스트9개를 구현했다. 일반9개는 H2·PostgreSQL 모두 통과했고 업그레이드 보존1개까지 격리 PostgreSQL10개 모두 통과했다. V1~V21 적용·Hibernate validate·실제 인덱스 확인 완료. 최종 전체 build/Checkstyle은315개 실패0·생략17로 통과했으며 상세 결과와 남은 리뷰는 QA/REPORT에 기록한다. 사용자 개발 DB·Notion·Frontend/AI/shared-types는 수정하지 않았다.
