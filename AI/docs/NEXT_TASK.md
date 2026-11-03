# AI 다음 작업

- 2026-11-03 실험 분석 API `POST /internal/v1/analysis-runs/analyze` 연결. candidateDecision은 미보정 후보, decision은 보정 confidence가 없어 UNCERTAIN이다. 실제 Qwen DNS/연결, Backend 내부 응답 계약, confidence 독립 보정과 왕복 검증이 남았다. [결과](features/fastapi-relevance-analysis-REPORT.md).

Streamlit UI는 2026-11-03 제거했다. Hybrid runner는 `evaluation/analysis_runner.py`에 유지한다.

## 가장 먼저 진행할 작업

- 작업명: 화면 분석 입력 계약을 실제 Server 경로에 연결하고 OCR 품질 필드 전달을 합의한다.
- 담당 영역: AI + Server + Desktop/Extension 계약 담당자
- 상태: AI Adapter와 내부 FastAPI 수신·검증 구현(2026-10-29). 분석 실행·실제 Backend 왕복은 미연결. `features/fastapi-reception-REPORT.md` 참고.

## 현재 계약 기준

Client → Server 요청은 `packages/shared-types/src/index.ts`의 `AnalysisRunRequest`를 따른다. 본문은 `clientRunId`, `contentSource`, `observedAt`, `sequence`(세션·출처별 1부터 증가), `guardStatus`(필수 nullable), 선택 `metadata`, 선택 `contentPayload.text`다. `sessionId`는 URL path에 있다.

Server → AI에서는 인증된 세션·목표·실행 문맥을 조회해 `VerifiedContext`와 확정 목표를 함께 제공해야 한다. Client가 보내지 않는 `runId`, `goalId`, `goalVersion`, `navigationId`, `isActiveTarget`, `sessionStatus`, `exclusionMode`를 Client JSON에 임의로 추가하지 않는다.

## 남은 작업

1. Server가 공개 요청을 검증하고 현재 `VerifiedContext`·확정 목표를 조립해 AI Adapter에 연결한다.
   - `POST /internal/v1/analysis-runs`의 내부 envelope와 서비스 Bearer 키를 합의한다. 이후 실제 분석 실행·공개 판정 응답을 연결한다. 현재 수신 확인 `RECEIVED`는 관련성 라벨이 아니다.
2. `extractionStatus`와 Desktop OCR의 `ocr.confidence`·`captureStatus`를 Server → AI에서 어디에 전달할지 합의한다. 공개 Client 요청 타입에는 현재 이 필드가 없다.
3. Server가 제공하는 `navigationId`가 실제 탭/창 이동을 구분하는지, AI에 계속 제공할지 확인한다.
4. Server/AI 결과 연결에서 `RELATED`·`UNRELATED`·`UNCERTAIN`, `reasonCode`, stale 관측, 중복 요청, 순서 역전 및 모델 실패를 통합 검증한다.
5. 독립 검수 자료로 판정 confidence를 보정한다. 보정 전 cosine/Hybrid 점수나 OCR 품질을 판정 confidence로 사용하지 않는다.

## 현재 검증 결과와 제한

- `PYTHONPATH=AI:. python3 -m pytest AI/tests/test_screen_analysis_contract.py -q`: 47 passed.
- `AI/tests/test_graphless_hybrid.py`, `AI/tests/test_graphless_metadata.py`를 제외한 AI 회귀: 158 passed, 1 skipped, 16 subtests passed.
- 전체 회귀 실행 시 현재 Python 환경에 `kiwipiepy==0.23.1`이 없어 Kiwi 의존 평가 테스트 75건이 실행되지 못했다. 네트워크 제한으로 임시 설치도 완료하지 못했다.
- 실제 Qwen 임베딩 요청, 공개 Server endpoint, Client 캡처/OCR 왕복은 검증하지 않았다.

## 선행 문서

- `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`
- `AI/docs/api_spec.md`, `AI/docs/content_acquisition_spec.md`, `AI/docs/analysis_rules.md`
- `AI/docs/features/screen-analysis-contract-v1-REPORT.md`
