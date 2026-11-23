# 결과 리포트 — 반복 이탈 기반 휴식 제안 생성 (#301)

## 변경 요약

- `OffTaskTimeline.breakEvidence`: 지금 이탈까지 5분 이내 간격으로 이어진 이탈 3회 이상을 근거로 계산(지금 이탈도 반복 기준을 넘겨야 함). 강도별 기준·알림 간격 계산을 기존 평가와 공유하도록 정리했다.
- `InterventionPolicyService`: 근거가 있으면 이탈 알림 대신 휴식 제안 생성(설정 OFF·같은 실행 구간 재제안 없음).
- V27 근거 컬럼, API 항목 `breakEvidence` 추가. `docs/API_CONTRACT.md`에 규칙과 필드(서버 제안) 기록.

## 테스트

- 단위(`OffTaskTimelineTests`): 0·4·8분 이탈에서 근거(3회, 9분), 반복 기준 경계 직전·알림 간격 중에는 없음, 5분 넘는 간격·짧은 이탈·딴짓 중이 아닐 때 없음, 지난 이탈 3회가 있어도 지금 이탈이 기준을 넘기 전에는 없음.
- 통합(`InterventionApiTests`): 반복 이탈에서 휴식 제안 1건과 근거·근거 판정 id, 목록 `breakEvidence`, 같은 실행 구간 재제안 없음 / 설정 OFF면 이탈 알림만, `breakEvidence` 없음.
- H2 전체 401개 통과(PostgreSQL 전용 17개 생략), Checkstyle 통과.
- 임시 PostgreSQL 17(Flyway+validate)에서 `intervention` 패키지 + `SessionApiTests` + `AnalysisApiTests` 159개 통과(#300 V26 제약 이름 포함).

## 남은 제한

- 설계서는 REVIEW 상태이고 근거 필드는 서버 제안이다. 문서 오너 확인 필요.
- 이탈 구간은 최근 10분 창 밖으로 밀리면 타임라인에서 지워지므로, 아주 긴 사슬의 첫 이탈은 근거에서 빠질 수 있다(제안이 늦어질 수는 있어도 잘못 생기지는 않음).
- Desktop은 지금 고정 문구를 보여 준다. 근거 문구(「최근 N분 동안 M회」)는 Desktop이 `breakEvidence`를 쓰도록 바꿔야 한다.
- 머지 순서: #302 → #303 → #304 → #300 PR(V26) → 이 PR(V27).
