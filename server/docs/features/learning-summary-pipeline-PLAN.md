# Backend 기능 기획서 — 학습 정리 생성 파이프라인

## 기본 정보

- 기능명: 세션 종료 후 학습 정리 비동기 생성
- 기능 ID: learning-summary-pipeline
- 작성자: 박진욱
- 작성일: 2026-11-19
- 우선순위: MVP - 기본
- 관련 Issue: #288 (상위 #29, 조회 #289)
- 예정 Branch: `feat/288-server-learning-summary-pipeline`
- 관련 문서: Notion `집중 기록 · 학습 정리 · 리포트` 5장·8.2 (REVIEW), `AI/docs/features/learning-summary-PLAN.md`

## 기능 목적

- 지금은 조회 API만 있어 정리 행이 생기지 않고 항상 404다. 종료된 세션마다 정리 상태가 진행되게 한다.
- 모델 호출은 AI가 담당하고(AI PLAN 기준), 서버는 저장·입력 마감·단일 작업·재시도·검증·조회를 담당한다.

## 처리 흐름

1. `POST /sessions/{id}/end`가 ENDED를 확정한 같은 transaction에서 `learning_summaries`를 `QUEUED`로 만든다(`input_cutoff_at = 종료 + 30초`). ABANDONED는 만들지 않는다.
2. `LearningSummaryWorker`가 5초마다 입력 마감이 지난 작업을 찾고, 조건부 UPDATE로 한 worker만 `GENERATING`으로 점유한다(lease 60초, `attempt_count + 1`).
3. 입력 마감까지 저장된 `learning_review_evidences`를 최대 20개 읽는다. 서로 다른 근거(해시)가 2개 미만이면 모델을 부르지 않고 `INSUFFICIENT_DATA`.
4. 충분하면 AI 내부 API `POST /internal/v1/learning-summaries`를 20초 timeout으로 한 번 부른다(분석과 같은 origin·서버 전용 키, `X-Trace-Id`).
5. 응답을 저장 직전 검사한다: 필수 문구, 핵심 개념 5개 이하, 전체 1,200자 이하, URL·이메일·전화번호·토큰·키·OTP 형태. 하나라도 걸리면 결과 전체를 버린다.
6. 통과하면 행을 잠그고 같은 시도 번호인지 확인한 뒤 `READY`로 저장한다(늦은 이전 시도는 덮어쓰지 못한다).
7. 실패·거절은 1분, 5분 뒤 다시 `QUEUED`로 돌리고 세 번째 실패에서 `FAILED`. 마지막 시도 lease가 끝나도 `FAILED`.

## 데이터 설계 (V23)

- `learning_summaries` 추가 컬럼: `input_cutoff_at`, `attempt_count`(0~3), `next_attempt_at`, `lease_until`, `evidence_count`, `model_version`, `error_code`, `generated_at`, `updated_at`.
- `learning_review_evidences`: `session_id`, `concept_label`(120자), `evidence_type`, `observed_at`, `content_source`, `dedupe_hash`(char 64), `created_at`, `UNIQUE(session_id, dedupe_hash)`. 원문·URL·코드·개인정보 비저장.

## AI 팀 합의가 필요한 것 (서버 단독 완성 불가)

- **근거 공급:** 지금 분석 응답에는 `reviewEvidence`가 없어 `learning_review_evidences`가 채워지지 않는다. 합의 전까지 모든 정리는 `INSUFFICIENT_DATA`로 끝난다.
- **내부 생성 API(서버 제안):**
  - 요청 `{ requestId, sessionId, goalSnapshot, reviewEvidence: [{ evidenceId, conceptLabel, evidenceType, observedAt }] }`, 헤더 `Authorization: Bearer <FOCUSON_AI_API_KEY>`, `X-Trace-Id`
  - 응답 200 `{ overview, keyConcepts: [{ name, briefExplanation, sessionEvidence }], nextStartPrompt, modelVersion }` (`data`로 감싸도 읽는다). 200 외는 실패로 재시도한다.

## 로그

- 분석과 같은 정본 이벤트 `ai.call.start/ok/fail`, `store.write.ok/fail`를 `component=learning-summary-worker`로 남긴다. 목표·근거·모델 출력·예외 메시지는 넣지 않고 고정 오류 코드만 남긴다.

## 완료 조건

- [x] 종료된 세션마다 정리 행이 생기고 상태가 진행된다(멱등 재전송도 1행).
- [x] 근거 2개 미만 INSUFFICIENT_DATA, 모델 미호출.
- [x] AI 실패 1분·5분 재시도 후 FAILED, 민감·과다 출력 폐기 후 재시도.
- [x] lease 만료 재점유, 마지막 시도 lease 만료 FAILED, 끝난 작업 재점유 없음.
- [x] H2 전체·PostgreSQL(Flyway+validate) 테스트, Checkstyle.
- [ ] AI 합의 후 실제 근거·READY 연동 확인.
