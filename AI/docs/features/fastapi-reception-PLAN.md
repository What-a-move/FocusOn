# FastAPI 내부 화면 입력 수신 PLAN

> 2026-11-08 상태 갱신: 아래의 `RECEIVED` 전용 응답과 모델 미호출은 당시 계획 범위다. 현재 구현은 이 검증 흐름에서 Hybrid 판정을 함께 실행하며, 단일 endpoint와 응답은 `fastapi-relevance-analysis-REPORT.md`가 기준이다.

- 작성일: 2026-10-29
- 관련 작업: 기존 #11 AI 전처리·관련성 작업, 현재 사용자 추가 승인
- 범위: AI 파일의 HTTP 수신·검증까지. 현재 Branch의 기존 미커밋 계약 정합성 변경을 보존한다.
- 적용 규칙: AI-SCOPE-001, AI-PRIV-002, AI-FAIL-001, AI-STALE-001.
- 정본 조회: Notion AI 분석 계약은 접근 권한 오류(404). 저장소 공유 계약과 사용자 제공 10/26 기준을 적용한다. GitHub CLI가 없어 Issue 상태 원격 재확인은 하지 못했다.

## 입력과 책임

`POST /internal/v1/analysis-runs`는 Backend 전용이다. `request`는 기존 ScreenAnalysisRequest, `goal`은 ConfirmedGoal, `context`는 Backend가 확인한 VerifiedContext, `extractionStatus`와 선택 `ocr`는 기존 내부 Adapter 인자를 받는다. 이 envelope와 서비스 키는 내부 연결용 구현 계약이며 Client 공개 필드를 추가하지 않는다. OCR 품질 전달 위치는 Backend 합의 전의 내부 제안이다.

## 출력과 처리

Bearer 서비스 키 검증 → 64KiB 이하 JSON 수신 → 기존 엄격 Schema 검증 → Adapter 문맥/목표 대조 → 활성 상태·제외·출처·민감값 검사 → HTTP 200 수신 확인.

응답은 `status: RECEIVED`, `sessionId`, `clientRunId`, `analysisExecuted: false`다. 저장·Queue가 없으므로 202나 작업 완료 라벨을 반환하지 않는다. 모델 호출·분류·confidence·타이머·알림·이벤트 멱등 저장은 후속 범위다. 오류에는 원문이나 검증 input을 포함하지 않는다.

## 완료 조건

- [x] 앱 진입점, 인증된 수신 route와 최소 health 제공
- [x] 정상 DOM·메타데이터·Desktop OCR 수신
- [x] 키 누락/오류, JSON/길이/문맥 오류, 제외·민감·비활성·P1 출처 차단
- [x] HTTP 테스트와 기존 계약 회귀, 로그 원문 미노출 확인 (69건 통과, 전체 회귀의 기존 Kiwi 의존 실패는 REPORT 기록)
- [x] 실행법·내부 합성 예제·결과와 다음 작업 기록
