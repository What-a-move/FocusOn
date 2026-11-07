# 화면 분석 요청·결과 저장 기획

## 기본 정보

- 작성일: 2026-11-08 / 사용자 다음 작업 진행 승인
- 담당: Server(사용자 담당 6번), 우선순위: MVP 핵심
- 관련 Issue: #171 / Branch: feat/171-analysis-runs (최신 dev 91cd524 기반)
- 정본: [POST analysis-runs](https://app.notion.com/p/58cf7aade6614fc9a00e248affb052f9), [AI 계약](https://app.notion.com/p/3de0b6e5e64c80fbb0bccb985e38002c), [ERD](https://app.notion.com/p/3de0b6e5e64c80a4bf5ef7ff3ac01f17)
- 2026-11-08 브라우저로 위 문서의 관련 절을 확인했다. API 오류 속성의 과거 503 문구보다 Response의 2026-11-02 확정(200 UNCERTAIN)을 적용한다. Notion은 수정하지 않는다.

## 목적과 범위

Desktop·연결된 Extension이 수집한 메타데이터/최대 2,000자 텍스트를 일회성 AI 입력으로 받고 안전한 판정 이력만 남긴다. 이번 Issue는 POST 한 개다. 개입 생성/조회, heartbeat Policy Engine, 시간 집계, 리포트, 세션 상태 명령은 변경하지 않는다. 팀원 #167~#169와 겹치지 않도록 기존 session 클래스를 수정하지 않는다.

## API 정의

- `POST /api/v1/sessions/{sessionId}/analysis-runs`
- Bearer + X-Device-Id 필수. 인증 사용자 소유 RUNNING 세션의 runner Desktop 또는 그 Desktop에 연결된 활성 Extension만 허용한다.
- body: `{clientRunId(UUID v4), contentSource, observedAt(UTC), sequence(양수), guardStatus:null|EXCLUDED|PRIVACY_BLOCKED, metadata?, contentPayload?:{text}}`.
- MVP source: APP_META, TAB_META, CHROME_DOM, DESKTOP_APP_OCR. Chrome viewport OCR·CAMERA_LOCAL은 이 경로에서 받지 않는다. guard 요청은 metadata/contentPayload를 함께 보내면 400이다. guard 없는 TAB_META와 CHROME_DOM 본문은 metadata.urlHost가 유효해야 하며 출처가 없으면 저장·AI 호출 전에400으로 거절한다.
- `200 data.{analysisRunId,analysisStatus,relation,confidence,reasonCode,observedAt,policyVersion}`. 정책 버전은 해당 세션 값이다. guard의 confidence/reasonCode는 null이다.
- 400 VALIDATION_ERROR, 401 기존 인증 오류, 404 SESSION_NOT_FOUND(소유권 불일치 포함), 409 SESSION_STATE_INVALID, 409 IDEMPOTENCY_CONFLICT(중복 clientRunId), 429 TOO_MANY_REQUESTS + Retry-After.
- 중복은 기존 공통 409 코드를 사용하고 응답에 입력 원문을 포함하지 않는다. 분석별 한도는 소유권 확인 후 session+device 분당8회이며 일반 인증 한도와 함께 적용한다.

## 데이터 설계

- 새 analysis_runs: ERD의 id/session_id/client_run_id/content_source/observed_at/received_at/sequence/relation/relation_confidence/model_version/policy_version/error_code/created_at.
- 실제 저장에 필요한 device_id(관측 기기), analysis_status, reason_code, prompt_version을 부가 컬럼으로 명시한다. 공개 계약 변경 없이 출처·결과를 재현하기 위한 내부 컬럼이다.
- UNIQUE(session_id,client_run_id), 양수 sequence, enum·confidence CHECK, 세션/기기 FK. (session_id,device_id,observed_at DESC,id DESC) index로 source와 무관하게 최신 관측을 찾는다.
- 원문·제목·host·AI envelope/응답 전문·토큰·traceId는 저장하지 않는다. 분석 이력 보관은 세션 종료 후90일(삭제 worker는 후속).
- 초기 안전 결과(UNCERTAIN/ANALYSIS_UNAVAILABLE)를 먼저 예약하고 AI 완료 후 해당 행만 갱신한다. 실패/프로세스 종료가 정상 판정으로 남지 않는다.
- 새 관측은 received/완료 순서가 아닌 observedAt·id로 선택한다. 늦은 AI 완료는 자기 이력만 바꾸며 최신 EXCLUDED를 덮는 현재상태 upsert를 하지 않는다. 후속 Policy Engine도 이 조회를 재사용해야 한다.

## 시각 기준

- observedAt은 기기 관측 시각 원본이다. 같은 기기 안에서 source 무관 관측 순서를 비교하며 서버 startedAt/lastResumedAt/현재 시각과 직접 비교해 거절하지 않는다. receivedAt은 서버 수신 시각이다.
- AI 호출 전후 실행 구간 확인은 서버가 읽은 lastResumedAt끼리 비교한다. 서버 시간으로 확정할 세션 수명·학습 시간에 기기 시각을 그대로 사용하지 않는다.
- 현재 공개 요청에는 실행 구간 식별자가 없어, 이전 구간에서 수집했지만 재개 후에 처음 도착한 요청을 서버 단독으로 완전히 식별할 수는 없다. 기기 시각을 서버 시각에 맞춘 것으로 간주하거나 receivedAt이 실제 화면 관측 시각이라고 주장하지 않는다.

## 처리 흐름

1. DTO·기기 헤더·host 형식·guard payload를 검증한다.
2. 짧은 사용자 잠금 transaction에서 인증 family/기기·runner 연결·세션 상태를 재검증한다. 중복을 확인하고 이력을 예약한다. DB 잠금을 AI 호출 동안 유지하지 않는다.
3. guard/서버 제외 도메인·동의·민감 입력 차단이면 AI를 호출하지 않는다. 차단 이력만 남긴다.
4. 서버 세션의 목표 snapshot과 정책을 사용해 AI 내부 envelope를 만든다. 목표 snapshot은 불변이므로 내부 goalVersion=1이며 stateVersion은 서버 세션 version+1이다. runId/navigationId는 서버 분석 실행 식별자이며 실제 브라우저 navigation ID를 안다고 주장하지 않는다.
5. 고정 서비스 URL + 별도 서비스 Bearer + 같은 traceId로 POST /internal/v1/analysis-runs를 호출한다. 짧은 timeout, 자동 재시도 없음. 서비스 미설정/장애/수신확인만 반환/비정상 schema는 200 UNCERTAIN,null,ANALYSIS_UNAVAILABLE.
6. 정상 결과는 0.75 기준을 적용한다. 2026-11-03 베타 계약의 promptVersion '-uncalibrated' + confidence null 예외만 인정한다. APP_META/TAB_META UNRELATED는 후속 Policy Engine에서 시간에 넣지 않도록 별도 유효 판정 helper를 제공한다.
7. 짧은 transaction에서 권한·세션 상태/마지막 재개 시각을 다시 검증한 뒤 자기 행만 완료한다. 실행 구간이 바뀐 결과는 안전 UNCERTAIN으로 보관한다. heartbeat의 누적 시간 저장으로 version만 증가해도 정상 결과를 버리지 않도록 마지막 재개 시각을 비교한다.

## 연동과 로깅

- Frontend/shared-types/AI 코드는 수정하지 않는다. 기존 공개 필드를 받는다.
- 현재 dev의 AI 정식 경로는 RECEIVED만 반환하므로 실제 판정 성공 QA는 #154 반영 후 필요하다. 임의의 항상 RELATED 판정이나 미보정 confidence 수치를 만들지 않는다.
- 공용 SafeApiLog에 gate.rule.pass/block, ai.call.start/ok/fail, store.write.ok/fail을 추가한다. 원문·외부 예외 메시지/응답 본문은 기록하지 않는다.
- 환경변수: AI_ANALYSIS_URL(서비스 origin), FOCUSON_AI_API_KEY(공유 서비스 키). 비어 있으면 안전 fallback이며 자격증명을 만들거나 실제 .env를 덮지 않는다.

## 완료 조건과 검증

- 구현·로컬 QA·격리 리뷰 완료. 결과는 QA/REPORT/code-review 문서로 확인한다. 원격 CI·실제 AI·Desktop/Extension 통합은 미완료다.

- 정상/guard/권한/동의/형식/중복/분당8회/AI 실패/저신뢰/베타 예외/receipt 거절을 HTTP·서비스 테스트로 검증한다.
- 지연 AI 결과와 최신 EXCLUDED, 일시정지/재개·unlink 경쟁을 검증한다.
- PostgreSQL 실제 Flyway 및 제약을 별도 격리 DB에서 검증한다. 개발 DB·8080은 건드리지 않는다.
- Backend build·Checkstyle·전체 회귀, 별도 컨텍스트 코드 리뷰를 수행한다.
- ERROR/QA/REPORT/리뷰와 CONTEXT/NEXT_TASK/DECISION_RECORD를 결과에 맞게 갱신한다. PR 생성·merge·이슈 종료는 별도 승인 대상이다.
