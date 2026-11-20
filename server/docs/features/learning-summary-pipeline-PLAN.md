# Backend 기능 기획서 — 학습 정리 생성 파이프라인

## 기본 정보

- 기능명: 세션 종료 후 학습 정리 생성 파이프라인 (QUEUED → GENERATING → READY/INSUFFICIENT_DATA/FAILED)
- 기능 ID: learning-summary-pipeline
- 작성자: 박진욱
- 작성일: 2026-11-19
- 우선순위: MVP - 기본
- 관련 Issue: #288 (상위 #29, 조회 API #206, 프론트 노트 #129)
- 작업 Branch: `feat/288-learning-summary-pipeline`
- 관련 Client·AI 문서: `AI/docs/features/learning-summary-PLAN.md`, `docs/API_CONTRACT.md` 리포트·학습 정리, `docs/DATA_PRIVACY.md`
- Notion 리포트 설계서 5장·8.2: 이 작업 환경의 Notion 연결로는 404여서 원문을 확인하지 못했다. 로컬 정본과 AI PLAN을 기준으로 했다.

## 기능 목적

- 해결할 문제: `GET /sessions/{id}/learning-summary`는 있지만 정리 행을 만드는 코드가 없어 종료된 세션도 항상 404다.
- Backend가 담당할 역할: 정리 행 생성, 30초 입력 마감, 단일 작업 점유, 근거 수 판정, AI 호출·재시도, 저장 직전 검증, 조회. 모델 호출 자체는 AI가 담당한다.

## API 정의

새 공개 API는 없다. 기존 `GET /api/v1/sessions/{sessionId}/learning-summary`의 동작이 바뀐다.

| 시점 | 이전 | 이후 |
| --- | --- | --- |
| 사용자 종료(`POST /end`) 직후 | 404 `NOT_FOUND` | 200 `QUEUED` |
| 종료 + 30초 뒤, 근거 2개 미만 | 404 | 200 `INSUFFICIENT_DATA`(AI 미호출) |
| 근거 2개 이상 + AI 성공 | 404 | 200 `READY`와 정리 본문 |
| AI 실패 3회 또는 출력 검증 실패 | 404 | 200 `FAILED` |
| `ABANDONED` 세션 | 404 | 404 (자동 정리 대상 아님, BE-010) |

### AI 내부 API (서버 제안안 — AI 합의 전)

`POST {AI_LEARNING_SUMMARY_URL}/internal/v1/learning-summaries`, `Authorization: Bearer {FOCUSON_AI_API_KEY}`, `X-Trace-Id`.

```json
{
  "sessionId": "12",
  "goalSnapshot": "Spring Security 공식 문서 학습",
  "evidences": [
    {"evidenceId": "31", "concept": "필터 체인", "observedAt": "2026-11-19T01:00:00Z"}
  ]
}
```

응답 200:

```json
{
  "overview": "…",
  "keyConcepts": [{"name": "…", "briefExplanation": "…", "sessionEvidence": "…"}],
  "nextStartPrompt": "…",
  "modelVersion": "선택",
  "promptVersion": "선택"
}
```

- 서버 분류: 429·5xx·연결 실패·20초 초과 → 재시도. 그 외 4xx·JSON 파싱 실패 → 즉시 `FAILED`.

## 데이터 설계 (V23)

- `learning_summaries` 추가 컬럼: `input_closes_at`(종료+30초), `next_attempt_at`, `lease_expires_at`, `attempt_count`(0~3). 상태별 CHECK(`ck_learning_summary_queue`)로 QUEUED·GENERATING·종료 상태의 필수/금지 값을 강제한다.
- `learning_review_evidences`: `session_id`, `analysis_run_id`(FK), `concept`(120자 이하·비공백), `evidence_hash`(SHA-256 hex, `(session_id, evidence_hash)` UNIQUE), `observed_at`, `relation_confidence`, `created_at`.
- 저장하지 않는 값: 화면 원문·DOM·OCR·URL·제목·검색어·AI 응답 원문.
- V19·V20 이후 생성기 없이 남은 QUEUED·GENERATING 행은 QUEUED로 되돌리고 기준 시각을 채운다. 세션 기록과 종료 상태 행은 바꾸지 않는다.

## 처리 흐름

```text
POST /end 커밋 ─(AFTER_COMMIT, 새 트랜잭션)→ learning_summaries QUEUED (next_attempt_at = 종료+30초)
Worker(5초 주기) → 조건부 UPDATE로 점유(GENERATING, attempt+1, lease 60초)
  → 입력 마감 전 근거 최대 20개 고정
  → 2개 미만: INSUFFICIENT_DATA (AI 미호출)
  → 2개 이상: AI 호출(20초) → 검증 → READY / 재시도(1분·5분) / FAILED
  → 저장은 행 잠금 후 status=GENERATING && attempt_count=점유 번호일 때만 (fencing)
```

## 이슈에 없던 기준과 결정 이유

| 항목 | 정한 기준 | 이유 |
| --- | --- | --- |
| 행 생성 시점 | 종료 트랜잭션 **커밋 뒤** 별도 트랜잭션 | "종료 처리는 정리 생성과 독립" 요구. 정리 쪽 오류가 종료를 rollback하지 않는다. HTTP 응답 전에 실행되므로 종료 직후 조회도 404가 아니다 |
| 리스너 유실 대비 | Worker가 최근 1일·1분 이상 지난 ENDED 중 행 없는 세션을 복구 | 커밋 직후 서버가 죽으면 행이 영구 누락된다. 1분 지연은 리스너와의 중복을 피하고, 1일 제한은 기능 도입 전 과거 세션을 일괄 생성하지 않기 위해서다 |
| 시도 횟수 | 최초 1 + 재시도 2 = 최대 3회. 1회 실패 뒤 1분, 2회 실패 뒤 5분 | 이슈의 "1분·5분 최대 2회 재시도" |
| 재시도 대상 | 전송 실패(연결·429·5xx·20초 초과)와 AI 주소·키 미설정 | 같은 입력으로 나중에 성공할 수 있는 실패만. 설정 누락은 배포 중 일시적일 수 있다 |
| 즉시 FAILED | 4xx(429 제외)·JSON 오류·Schema/길이/민감값 위반 | 다시 보내도 결과가 같거나, 같은 근거를 외부에 반복 전송하게 된다. AI PLAN "민감값 의심 시 결과 전체 폐기" |
| lease | 60초 | AI 20초 + 저장 여유. 만료되면 다른 Worker가 다음 시도로 가져간다 |
| 마지막 시도 중 Worker 중단 | lease 만료 뒤 FAILED | 시도 수 상한(3)을 넘지 않기 위해서 |
| 늦은 결과 | 점유 번호(attempt_count)가 다르면 저장하지 않음 | lease를 빼앗긴 Worker가 새 시도의 결과를 덮어쓰지 못하게 한다 |
| 입력 근거 수 | 마감 전 수신분 중 관측 순서 최대 20개 | AI PLAN "중복 제거 후 최대 20개, 전체 2,400자"(120자 × 20) |
| READY의 개념 수 | 1~5개 | 계약 최대 5개. 개념 0개 READY는 근거 기반 정리라고 볼 수 없다 |
| 민감값 패턴 | URL·도메인·이메일·전화·JWT·주요 API Key·`key=`류·32자 이상 영숫자 토큰·6~8자리 숫자(OTP) | AI PLAN 6항. 오탐이 나면 FAILED로 끝나며, 원문 유출보다 안전한 쪽을 택했다 |
| 길이 계산 | code point 기준 1,200자 | V19 DB CHECK의 PostgreSQL `length()`와 같게 맞췄다 |

## 예외 처리

| 상황 | 결과 | 비고 |
| --- | --- | --- |
| 정리 행 생성 실패 | 종료는 유지, WARN 로그, Worker 복구 | 공개 응답 변화 없음 |
| AI 일시 실패 | QUEUED + 다음 시도 시각 | 3회째 실패면 FAILED |
| AI 출력 위반 | FAILED, 본문 미저장 | `store.write.fail` 로그 |
| Worker 예외 | WARN(`errorClass`만), 다음 작업 계속 | lease 만료 뒤 재처리 |

## 연동 범위

- Desktop: 종료 뒤 `learning-summary`를 폴링해 QUEUED·GENERATING이면 계속 조회하고 terminal 상태에서 멈춘다(#129 화면은 완성).
- AI: `POST /internal/v1/learning-summaries` 구현, 분석 응답의 `reviewEvidence` 제공 형식 합의 필요.
- 영향받는 API·Entity: `POST /sessions/{id}/end`(이벤트 발행만 추가), `GET /sessions/{id}/learning-summary`, `learning_summaries`, `learning_review_evidences`.

## 완료 조건

- [x] 종료된 세션마다 정리 행이 생기고 상태 전이가 동작한다.
- [x] 근거 2개 미만 INSUFFICIENT_DATA, 모델 미호출.
- [x] AI 실패 재시도·FAILED, 세션 기록 유지, 동시 Worker 중복 생성 없음.
- [x] 원문·URL·민감값이 DB·로그에 남지 않음.
- [ ] AI 합의 후 실제 근거·READY 연동 확인 (AI 미구현 — 가짜 AI 프로세스로만 확인).
