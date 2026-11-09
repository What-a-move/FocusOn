# Issue #182 기록

## 현재 상태 (2026-11-09)

- 현재 진행 정본은 GitHub [#182](https://github.com/What-a-move/FocusOn/issues/182) 본문이며 PR은 [#205](https://github.com/What-a-move/FocusOn/pull/205)로 이미 생성했다. 이슈 생성/PR 생성 승인을 다시 요청하지 않는다.
- 내부 정책·조회/ack와 전경 PUT/GET·전송 receipt POST 및 분석/heartbeat 연결을 구현했고 Notion 정본/공통 계약을 맞췄다. 아래 '빈 adapter/receipt 없음/미합의'는 확장 전 기록이다.
- rolling/TTL30초·lease30초·최초 SENT 수신 기준 cooldown/불명 전송 보류는 사용자 승인됐다. numeric confidence0.6과 AI #190은 dev 반영 완료다.
- 남은 범위: PR205 리뷰 피드백 반영 검증, 소비자 P2/P4 연결, 실제 포키 표시 및 사용자 지시로 보류한 AI 실연결 QA. 서버 구현 완료를 실제 앱 완료로 취급하지 않는다. 병합·Issue 종료는 별도다.

<details>
<summary>등록 및 초기 검토 당시 기록 — 현행 구현 상태가 아님</summary>

# 기능 또는 작업 요청

## 작업 유형

- [x] 기능 개발

## 담당 영역

- [x] Server

## Frontend 대상 앱

해당 없음. Desktop·Extension 코드는 소비 계약 확인만 하며 직접 수정하지 않는다.

## Labels

`area:server`, `type:feature`, `priority:mvp-core`

## 작업 제목

[Server] 화면 이탈 Policy Engine 및 개입 조회·확인 API 구현

## 작업 목적

PR #172에 저장된 최신 관측을 기존 heartbeat와 연결해 시간 기반 화면 OFF_TASK 개입을 생성하고 Desktop이 조회·확인할 수 있게 한다.

## 작업 내용

- 세션 고정 정책 버전으로 연속/rolling 10분 반복 이탈 조건 평가
- 관측 순서·보호·동의·실행 구간 검증, 개입 저장·중복 방지·cooldown·동시성 처리
- 기존 heartbeat의 유효 RUNNING 신호와 분석 예약/완료에 최소 연결
- GET /api/v1/sessions/{sessionId}/interventions
- POST /api/v1/interventions/{interventionId}/acknowledgements
- 기기 간 판정 선택·실제 알림 전송 확인 및 사용자 동작에 영향을 주는 미정 정책 검토

## 사용자 흐름 또는 재현 방법

1. 실행 중인 세션에서 합성 화면 분석 결과와 유효 heartbeat를 입력한다.
2. 정책 조건 충족 시 OFF_TASK가 한 번 생성되고 기존 Desktop 계약으로 조회된다.
3. 확인/닫기 기록은 멱등 반영하며 AI 판정·세션 상태·타이머를 바꾸지 않는다.

## 완료 조건

- [ ] 미정 사용자 동작·계약에 대한 승인과 필요한 정본 동기화
- [x] 서버 내부 Policy Engine·개입 영속화·조회/ack API 구현 (production adapter/receipt 미연결)
- [x] 개인정보 비저장·관측 순서·보호·임계값·동시성·중복 회귀 검증
- [x] 격리 PostgreSQL QA, backend build·Checkstyle, 격리 리뷰
- [ ] 팀원 인계 및 합성 테스트/실제 AI·Desktop 연동 QA 결과 구분
- [ ] PR 생성·병합·Issue 종료 각각 별도 승인

## 예상 변경 파일

```text
server/src/main/java/com/capstone/backend/intervention/**
server/src/main/java/com/capstone/backend/analysis/** (최소 연결)
server/src/main/java/com/capstone/backend/session/service/SessionHeartbeatService.java (최소 연결)
server/src/main/resources/db/migration/V<최신 확인 후 확정>__*.sql
server/src/test/**
server/docs/**
```

## 예정 Branch

`feat/182-server-intervention-policy` — 최종 기획·구현 승인 후 현재 checkout에서 최신 dev 기반으로 분기한다.

## 관련 문서

- 기획서: `server/docs/features/intervention-policy-PLAN.md` (로컬 상세안, 아직 커밋/게시하지 않음)
- 상세 검토: 내부 저장·구간·잠금·멱등/outbox 안 작성 완료. 승인 대기는 전경 context 위임/전송 receipt·cooldown 수신 기준/rolling 창 clipping/PENDING 만료 및 늦은 ack 의미다.
- 2026-11-09 사용자 승인: 분석 대기/확정 UNCERTAIN 구분 및 통합 선행 관계 보완 후 내부 구현 착수. 새 공개 계약·미정 정책은 별도다.
- API 정본: [개입 조회](https://app.notion.com/p/24690cf40b1149419938913acd67d2fd), [ack](https://app.notion.com/p/fb8626ab4897450d95a6bfeddba674e6)
- 정책 정본: [화면 분석](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8)
- 선행 #171 / PR #172, AI 연동 후속 #154, 팀원 종료 #163 / PR #164 및 연장 #168
- 관측 순서 인계: `server/docs/features/analysis-observation-order-handoff.md`

## 주의사항 및 결정 필요 사항

- 사용자 승인(2026-11-09): 이슈 생성·상세 기획 검토. 이후 2건 보완 후 내부 구현·브랜치 착수를 승인받았다. PR·병합·종료 및 미정 계약은 별도다.
- 확인했어 이후 5분은 확정된 Desktop UI 동작이다. 서버 cooldown 재설계 대상으로 삼지 않는다.
- 서버 cooldown의 실제 전송 확인은 GET 조회·생성 시각으로 대체하지 않는다.
- 기기별 번호/시각을 비교해 Desktop·Extension 전체 최신 화면을 단정하지 않는다.
- 구간 저장·잠금·중복 ack는 내부 설계로 정리하고, rolling 경계·만료 등 사용자 동작에 영향을 주는 미정 항목만 승인받는다.
- #154 미완료여도 Policy/API와 합성 테스트 진행 가능. 실제 AI→포키 통합 QA 완료를 뜻하지 않는다.
- BREAK_SUGGESTION 생성·카메라·이력/local-events·리포트·학습 정리는 후속 범위다.
- 사용자·목표·세션 상태 명령은 팀원 담당. Frontend/shared-types/AI는 직접 수정하지 않는다.
- 개발 DB5432/서버8080·기존 IDE 파일·Claude outputs·stash를 보존한다.
- #171은 구현·병합·단계 완료 후 종료 승인 대기로 OPEN이다. 미완료 구현과 구분한다.

## 진행 체크리스트

- [x] 0. 최신 dev·기존 Issue/PR·코드·관련 정본 확인
- [x] 1. 사용자 승인 후 Issue 생성·실제 Label 확인
- [x] 2. 기획 초안 작성
- [x] 3. API·시간·관측·전송·담당 경계 검토
- [ ] 4. 검토 수정·필요 계약 승인/정본 동기화
- [ ] 5. 최종 기획 범위 확인
- [x] 6. 사용자 구현 진행 승인 (2건 보완 후 내부 구현, 미정 계약 제외)
- [x] 7. 최신 dev 기반 Issue Branch 생성 (`5462afd`, 현재 checkout)
- [x] 8. 서버 내부 구현 (실제 통합 P1~P7 미완료)
- [x] 9. 격리 리뷰·보완
- [x] 10. 서버 합성 테스트·QA (실제 AI/앱 QA는 미완료)
- [ ] 11. 별도 승인 후 PR·CI·리뷰와 문서 반영

## 검토 수정 및 실제 연동 선행 관계 (2026-11-09)

- 분석 처리 중에는 같은 실행/context의 구간 ID와 확정 누적을 유지하고 시간 누적·개입 생성만 동결한다. 최신 UNRELATED 완료는 같은 구간에서 재개한다. 대기 시간은 소급 합산하지 않는다. 확정 UNCERTAIN/실패/보호/RELATED·context/실행 세대 변경은 구간을 종료한다. 처리 중과 확정 UNCERTAIN을 별도 상태로 저장한다.
- [ ] P1 Backend(본인)+Desktop/Extension: 현재 관측 담당·전경 context·유효성 계약 합의/정본 반영 — production 판정 gate 해제 선행
- [ ] P2 Desktop/Extension/shared-types 담당: P1 소비자 구현·지속 counter — 현재 클라이언트에 없는 계약이며 #182에 구현된 것으로 취급하지 않음
- [ ] P3 Backend(본인)+Desktop: 전송 성공/실패·receipt 재시도 계약과 서버 수신 경로 — 정본/별도 이슈 생성 승인 후, production cooldown 해제 선행
- [ ] P4 Desktop 담당: 채널 호출 성공 receipt·유실 복구 구현 — P3 후, 사용자 ack/GET을 전송으로 대체하지 않음
- [ ] P5 Backend(본인)+세션 API 담당: #167/PR #178 heartbeat 최소 연결, #163/PR #164·#168 경계 대조
- [ ] P6 AI 담당: #154 실제 판정 응답 — 합성 테스트는 독립 진행, 실제 AI→포키 QA 선행
- [ ] P7 각 담당 공동: P1~P6 및 종료/리포트 연결 후 실제 앱 알림 포함 전체 흐름 QA

#182 서버 엔진/API·합성 QA 완료와 P1~P7 실제 통합 완료를 분리한다. 관련 작업 이슈가 등록되면 위 추적 항목에 번호를 연결한다. 아직 없는 이슈 번호를 지어내거나 승인 없이 신규 이슈를 생성하지 않는다. rolling 경계/만료는 사용자 응답 전 production 정책으로 활성화하지 않는다.

## 승인 및 정본 상태

- 2026-11-09 사용자 추가 승인: rolling 창 내부 시간 clipping·열린 구간 1회 포함, 미전송 PENDING 30초 만료 권고안 확정.
- Notion 편집 오류 후 사용자는 원문 복구만 승인했다. 해당 문단 원문 복구를 확인했으며 새 정책은 Notion에 추가하지 않았다. 정본 반영은 미완료다.
- 현재 `feat/182-server-intervention-policy`에서 내부 시간 구간/정책 엔진과 합성 회귀를 구현 중이다. #182 전체 구현/API·PG QA·실제 앱 연동은 아직 완료 아님.

## 최종 서버 검증 (2026-11-09)

- 전체 build·Checkstyle 통과: 테스트 226개 실패0·오류0·생략10.
- 전용 PostgreSQL loopback51734에서 분석/heartbeat/개입 및 migration 선택 회귀 87개 전부 통과. 실제 임의 포트 HTTP GET/ack 검증 포함. 개발5432/8080 무변경.
- 격리 리뷰 P1 untrusted 관측 정책 전이 누락을 보완하고 재검토 완료. PG CHECK NULL 누락도 보강/재검증.
- 실제 구현: typed JSONB 상태+개입 이벤트(최초 action/actedAt), 분석 transaction 연결, heartbeat commit 후 별도 평가. 별도 ack 테이블/outbox는 구현하지 않음.
- API 미존재/비소유 개입은 기존 NOT_FOUND404, EXPIRED 늦은 ack는 actedAt null. 정본 상세 대조가 남음.
- 기본 전경 adapter/관측 소속은 없음. receipt 수신 API도 없음. production 후보 생성 보류. 실제 앱 알림/실제 AI 통합 완료 아님.
- 신규 정책 Notion 동기화 미완료, PR 생성/원격 CI/봇 리뷰/병합/종료 미수행.
- QA/리뷰/결과: server/docs/features/intervention-policy-{QA,code-review,REPORT}.md

</details>
