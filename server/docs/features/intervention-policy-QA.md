# #182 서버 QA

## 병합 승인 후 추가2건 최종 보완 (2026-11-09)

- 사용자가 PR205 병합을 승인했다. 병합 전3664598 자동 재리뷰에서 추가2건을 확인해 보완했다. Issue182 종료 승인은 별도이며 실행하지 않는다.
- 철회/보호로 없어진 pending을 늦은 완료가 재구성하지 않는다. 필수 동의3종의 prepare→철회→재동의→옛complete 차단 및 fresh 관측 허용 회귀를 추가했다.
- 이미 반환된 GET과 늦은 SENT 경쟁은 서버 재조회만으로 제거되지 않는다. 소비자 단일 coordinator·journal/outbox barrier·GET 응답 세대 폐기 규칙을 인계문/연결 설계에 명시했다. 소비자 구현·OS exactly-once를 완료/보장했다고 주장하지 않는다.
- 독립 재검토 추가 지적 없음. 전체 build·Checkstyle282개 실패0·오류0·생략14, 격리 PostgreSQL Flyway+validate155개 실패0·생략0. 실행 근거는 server/build/ai-integration-qa/pr205-premerge-{build,pg}.log 및 요약 JSON이다.
- 최종 수정분 원격 CI를 확인한 뒤 병합한다. 새 endpoint/사용자 정책/마이그레이션은 추가하지 않았다. 실제 AI/소비자/포키 QA는 별도다.


## PR205 피드백9건 보완 — 로컬 검증 완료 (2026-11-09)

- CodeRabbit7ab8f6e 리뷰의 actionable9건과 동의 철회 테스트 제안을 대조·반영했다. 처리표: features/intervention-policy-review-response.md. 현재 기준 PR은 생성·push 완료 상태이며 생성 승인을 다시 요청하지 않는다.
- 보호 관측의 context 범위 현재성, 재분석 후보 보존/표시 동결, 동의 철회 즉시 중단, cooldown 표시/receivedAt 경계, heartbeat connection cleanup 이후 평가를 보완했다. 공개 계약/정책 변경이나 새 migration은 없다.
- 전체 build·Checkstyle279개 실패0·오류0·생략14. 같은 CI 설정(Flyway+validate)의 격리 PostgreSQL152개 실패0·생략0. 단일 connection pool·필수 동의3종 철회/재동의·선택 카메라 비간섭·늦은 SENT·cooldown 지연 완료 등을 포함한다.
- 구현 대화 없는 새 에이전트의 독립 리뷰에서 추가 actionable finding 없음. 최종 수정분 원격 CI/자동 재리뷰 결과는 별도 확인한다. 기존7ab의 봇 체크 SUCCESS를 새 수정분 승인으로 쓰지 않는다.


## PR205 원격 시각 정밀도 보완·CI 범위 확장 (2026-11-09)

- cb2d530 원격 전체 검사에서 동시 ack 응답과 lease 종료 시각 assertion2건이 실패했다. ack 최초 시각을 PostgreSQL MICROS 정밀도로 고정하고 lease fixture 입력도 MICROS로 고정했다. 최초 사용자 결정·만료·cooldown 계약은 유지한다.
- 기존 원격 PostgreSQL job에 intervention 전체·heartbeat cleanup·SessionApiTests를 포함했다. job 이름/권한/DB 설정은 유지한다. 별도 읽기 리뷰에서 추가 actionable finding 없음.
- 보완 후 전체 build·Checkstyle270개 실패0·오류0·생략14. CI와 같은 Flyway enabled+Hibernate validate 설정 및 확장한 정확한 테스트 목록에서 PostgreSQL143개 실패0·생략0. 이전133개는 확장 전 범위다.
- 실행 근거: server/build/ai-integration-qa/pr205-precision-{build,pg-validate}.log 및 요약 JSON. 이 결과는 로컬이며 최종 push의 원격 CI/봇 리뷰 결과는 PR #205에서 따로 확인한다.


## PR #205 생성 및 최신 dev 충돌 해소 (2026-11-09)

- 사용자 승인으로 d5e8b2e를 push하고 [PR #205](https://github.com/What-a-move/FocusOn/pull/205)를 dev 대상으로 생성했다. 병합·Issue 종료는 승인받지 않았으며 실행하지 않는다.
- 생성 직후 dev ba58e5c(#202 세션 요약·#204 Desktop 시연 수정)가 추가돼 DECISION_RECORD 문서 충돌을 양쪽 기록 보존으로 해소했다. 통합 커밋 ef37f37이며 새 migration 충돌/자체 코드 변경은 없다.
- 최신 통합 후 전체 build·Checkstyle270개 실패0·오류0·생략14, 격리 PostgreSQL 관련133개 실패0·생략0. 실행 근거: server/build/ai-integration-qa/pr205-devba58e5c-{build,pg}.log 및 요약 JSON. 이전267/131은 당시 dev0ca256e 기준 이력이다.
- summary GET은 팀원 #201/PR #202에서 이미 추가됐다. 현재 저장된 종료 시간/연장 이력만 반환하며 분석·개입 선택 통계는 아직 집계하지 않는다. 다음 작업에서 중복 개발하지 않는다.
- PR 갱신 후 원격 CI/CodeRabbit을 확인한다. 로컬 성공을 원격 CI/봇 리뷰 완료로 취급하지 않는다. 실제 Desktop/Extension 소비자 연결·포키 실표시·AI 실연결 QA는 여전히 미완료다.


## 실제 알림 서버 연결 최종 검증 (2026-11-09)

- 기준: 원격 dev `0ca256e`까지 통합한 `c767669`, 연결 구현 `792d36f`. Backend 변경만 직접 구현했고 Frontend/shared-types/AI는 수정하지 않았다.
- 전체 build·Checkstyle 통과: **267개, 실패0·오류0·생략14**. 생략에는 명시 실행 flag가 없는 실제 AI2개 및 PostgreSQL 전용 검사가 포함된다.
- PostgreSQL17.11 격리 관련 회귀 **131개, 실패0·오류0·생략0**. 대상: intervention 전체, AnalysisApiTests/AnalysisMigrationTests, SessionHeartbeatCleanupTests, SessionApiTests. 개발5432/8080은 사용·변경하지 않았다.
- 임의 HTTP 포트에서 실제 context PUT/GET → heartbeat 정책 생성 → interventions GET → deliveries POST → 재조회 미표시를 검증했다. X-Trace-Id 왕복·data envelope·문자열 ID를 확인했다. 시간/판정은 합성이며 실제 학교 모델·Desktop 표시가 아니다.
- V16→V17 및 기존 V17 이벤트가 있는 V17→V18 보존·FK/unique/CHECK를 실제 PostgreSQL에서 검증했다. SENT PENDING은 slotNULL을 허용하고 미전송 PENDING의 slotNULL은 거절한다.
- context nonce/순번 충돌·JS 경계/소수 거절·기기 권한·Extension 연결/해제·만료 lease 비복구·유효 heartbeat만 갱신·중복/오류 신호 비갱신·옛 context의 늦은 AI·context 없는 구형 이력 비간섭을 검증했다.
- SENT receipt의 멱등/최초 수신 cooldown·GET 재노출 차단·확인 반응 유지·NOT_SENT 재시도·UNKNOWN 뒤 NOT_SENT로 보류 해제 불가·늦은 SENT의 만료 상태 보존·동시 context/receipt·lease 정확한 종료 경계를 검증했다.
- `server/build/ai-integration-qa/`의 `context-delivery-dev0ca256e-{recheck,pg}.log` 및 요약 JSON이 이번 실행 근거다. 앞선 실패 실행과 이전267개 미만 결과는 이력으로 남긴다.

### 보완한 실패와 남은 제한

- 초기 PG 확장 검사에서 테스트 fixture의 JDBC Instant 바인딩9건과 새 안전 컬럼 허용 목록1건을 수정했다. 운영 동작 실패로 단정하지 않았다. 이후129개·최신 dev 통합 후131개 전부 통과했다.
- 최신 dev 전체 검사에서 기존 CORS 실제 HTTP preflight가1회404였다. 단독 재실행 및 전체267개 재실행에서 통과했고 원인은 미확정이다. 추측으로 CORS/보안 설정을 변경하지 않았다.
- 격리 리뷰 P2(구형 이력의 현재 전경 간섭)를 수정하고 별도 재검토했다. migration 추가 리뷰도 actionable finding 없음. 봇 리뷰/원격 CI를 실행한 것은 아니다.
- Desktop/Extension/shared-types 소비자 연결, 내구성 있는 전송 시도/receipt 재시도 및 일반 모드 실표시 QA가 필요하다. 실제 AI/학교 모델 연결은 사용자 지시로 보류했다. 실제 포키 표시·전체 앱 완료로 표현하지 않는다.
- Notion 새 API3행, 기존 분석/heartbeat 요청, 개입 GET 응답·권한·cooldown, 화면 분석 추가 정책을 반영했다. ack 원본은404/401과 status/actedAt만 정의하며 서버 기존 코드/nullable 제한을 별도로 명시하지 않았다. 별도 ack 테이블 없이 이벤트에 최초 action을 저장하는 실제 구조를 문서화했다.
- PR 생성·원격 CI·봇 리뷰·병합·Issue 종료는 아직 수행하지 않았다. 각각 별도 승인 대상이다.

## 아래는 이전 실행 이력


- 일자: 2026-11-09
- 기준: `feat/182-server-intervention-policy`, origin/dev `5462afd` 기반.
- 실제 AI·Desktop 포키 표시 QA가 아닌 서버 합성 입력/인증/DB/HTTP 검증이다.

## 최종 실행 결과

| 검증 | 결과 |
| --- | --- |
| `./server/gradlew -p server build` | 성공. 전체 226개, 실패 0, 오류 0, 생략 10. Checkstyle main/test 통과 |
| 격리 PostgreSQL 17.11 + Flyway/validate | 선택 회귀 87개, 실패 0, 오류 0, 생략 0. Checkstyle main/test 통과 |
| 임의 HTTP 포트에서 개입 GET/ack POST | 실제 HTTP 200·data 배열·문자열 ID·DISMISSED 확인. MockMvc 정상/오류도 별도 검증 |
| V15→V16 | 기존 세션 누적 보존, FK/type/ack timestamp/pending NULL/원인·slot UNIQUE 제약 검증 |
| 격리 리뷰 | P1 1건 보완 후 독립 재검토, 추가 actionable finding 없음 |

PG 실행 대상: intervention 전체 + AnalysisApiTests + AnalysisMigrationTests + SessionHeartbeatCleanupTests. 전용 disposable 컨테이너 `focuson-182-qa`, loopback 51734와 독립 QA DB만 사용했다. 개발 DB5432/기존8080 서버는 중지·초기화하지 않았다. 테스트 서버는 RANDOM_PORT를 사용했다.

## 정상/오류 및 경쟁

- 같은 실행/context에서 25초마다 재분석해도 episode ID/확정 누적 유지. pending 동안 시간/후보 동결, 대기 시간 비소급, GENTLE 180초 초과 도달.
- 각 강도 연속 조건 직전/동일/초과, rolling clipping·열린 구간 1회, metadata UNRELATED 제외.
- 확정 UNCERTAIN/RELATED/EXCLUDED/PRIVACY_BLOCKED는 현재 episode 종료. 늦은 완료는 최신 보호를 덮지 않음.
- 실제 AnalysisRunStore prepare/complete 연결에서 pending 보존, 최신 보호·구형 legacy 순서 불명 이후 번호 복구의 새 episode 검증.
- 서버 시계 역행·snapshot 복구 시 대기 시간 가산/잘못된 재개 없음.
- 동시 heartbeat 생성 1회, 동시/반대 ack 첫 결정/actedAt 유지.
- 조회는 전송/사용자 반응 시각을 만들지 않음. 30초 만료·EXPIRED 늦은 ack null actedAt·전송 미확인 재생성 금지.
- 인증 없음/타인 세션·개입/기기 헤더 없음/미정 DISPLAYED action 거절. 원문 응답 없음.
- 실행 세대 변경 뒤 PENDING 표시 차단, 정책 평가 실패 뒤에도 heartbeat의 유효 누적/생존 commit 보존.
- 기본 foreground adapter 없음은 추측 생성/정책 상태 생성을 하지 않고 빈 배열 반환.

## 남은 제한

- 전경 context/소속/유효성 및 전송 receipt 계약·소비자 구현 없음. production 생성은 보류가 기본이다. #182만으로 실제 앱 알림이 동작한다고 주장하지 않는다.
- #154 실제 AI 응답 연결 및 팀원 종료/연장 API·향후 리포트 통합 QA는 별도다.
- Notion 원문 복구는 확인했으나 사용자 승인된 rolling/만료 신규 정책 정본 반영은 미완료다. 개입 미존재 404 code/EXPIRED actedAt null 상세도 최종 정본 대조가 필요하다.
- PR 생성·원격 CI·봇 리뷰·병합·Issue 종료를 수행하지 않았다.

## confidence 0.6 변경 후 최종 회귀

- 사용자 최종 변경: numeric confidence 기준 0.6. 일반·베타 각각의 0.599/0.6/0.6001/0.65/0.749/0.75/1.0과 null 예외를 검사했다.
- 전체 build/Checkstyle 성공: 테스트234, 실패0, 오류0, 생략10.
- DB/SQL 변경 없음으로 PG를 재실행하지 않았다. 위 PG87개는 confidence 변경 전 #182 검증 결과다.
- Notion AI 분석 계약4.4/7을 numeric 0.6으로 수정·저장 확인, 공통 API/Backend 규칙 동기화. AI 코드/실제 모델·앱 연동은 여전히 별도다.
