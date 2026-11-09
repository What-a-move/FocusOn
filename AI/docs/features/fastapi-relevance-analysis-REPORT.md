# FastAPI 관련성 분석 베타 결과

**상태:** AI 단일 경로 구현 완료 · 실제 Backend 왕복 미검증

## 동작 경로

- `POST /internal/v1/analysis-runs`가 서비스 Bearer 인증, 입력 Schema, 세션·목표·활성 화면·제외·민감정보 검사를 마친 뒤 Kiwi + Qwen Hybrid 분석을 실행한다.
- `/internal/v1/analysis-runs/analyze`는 제거했다. 분석 제외·민감 화면은 임베딩 모델을 호출하지 않고 기존 409 상태를 반환한다.
- 정상 응답은 `decision`, `confidence`, `reasonCode`, `modelVersion`, `promptVersion` 다섯 필드다. 판정이 미보정 후보임은 `promptVersion`의 `-uncalibrated`로 식별한다.
- `FOCUSON_ANALYSIS_DECISION_MODE=candidate`에서는 미보정 Hybrid 후보 점수가 0.6 이상일 때만 후보를 `decision`으로 반환한다. 점수가 없거나 낮으면 `UNCERTAIN`이다. `promptVersion`은 `-uncalibrated`로 끝나며 이 점수는 보정된 확률이 아니다.
- 앱·탭 메타데이터만으로 나온 `UNRELATED`는 `UNCERTAIN`으로 낮춰 DOM/OCR 글자 입력을 기다린다. 임베딩·분석 실패는 `UNCERTAIN`과 `ANALYSIS_UNAVAILABLE`로 반환한다.
- 기본 `safe` 모드는 보정 confidence 없이 RELATED/UNRELATED를 확정하지 않는다. 현재 safe 경로는 보정 점수를 공급하지 않으므로 보정 이후 별도 연결이 필요하다.
- Desktop OCR 입력은 `contentPayload.text`가 있으면 충분하다. `ocr.confidence`, `captureStatus`는 선택이며 생략해도 입력 처리와 판정이 가능하다.

## 모델 준비 상태와 시간

- `GET /health`는 실제 임베딩 요청과 2560차원 응답 검증을 수행한다. 성공은 `200`, `modelConnected: true`; 실패는 `503`, `analysisEnabled: false`, `reasonCode: ANALYSIS_UNAVAILABLE`이다.
- 분석 임베딩 요청의 기본 timeout은 4초이며 재시도는 0회다. 이는 HTTP 연결·읽기 등의 개별 시간 제한이며 전체 분석의 4초 마감은 아니다. timeout/연결 실패는 `UNCERTAIN` 실패 응답으로 정규화한다. `/health`는 3초 timeout으로 별도 probe를 한다. 기존 환경 변수에 설정된 값이 있으면 기본값보다 우선한다.
- 학교 Ollama 실측은 [학교 Ollama 임베딩 SETUP](school-ollama-embedding-SETUP.md)에 기록했다. 실제 HTTP 순차 합성 40건 전체 p95 2.32초였지만 DOM 20건 p95 3.76초, 최대 4.04초로 3초 목표를 넘었다. 첫 실행은 2.24초, 전체 표본 중 3초 초과 2건이었다.
- 이 표본은 합성 목표/콘텐츠, 순차 요청, OCR 제외, 실제 Backend 미경유다. 전체 서비스 p95나 동시 사용자 성능으로 해석하지 않는다. 현재 자료 기준으로 3초 목표 달성은 확인되지 않았다.

## Server 연동 상태와 다음 일

- 최신 dev의 Backend Java Adapter는 AI 관련성 경로를 호출한다. 실제 모델·제품 알림까지의 왕복은 미검증이다.
- Server 담당자는 `AI/examples/analysis-reception.json` 및 `FOCUSON_AI_API_KEY` Bearer 인증 계약을 사용한다. 현재 dev Backend의 AI 전체 응답 제한은 5초이므로, 모델 호출 제한을 4초로 바꾸더라도 전체 처리 시간이 5초를 넘으면 Backend가 `UNCERTAIN` / `ANALYSIS_UNAVAILABLE`로 처리할 수 있다.
- Server 쪽 `confidence`를 사용자에게 보정 확률로 해석하면 안 된다. 베타 모드에서 `promptVersion`의 `-uncalibrated`를 유지해 기록하고, `confidence`가 null일 수 있도록 계약을 확인한다.
- 사용자가 전달한 Backend 협의에 따라 AI는 0.6을 적용한다. 현재 dev Backend 코드는 0.75 미만을 보류하므로 Backend 변경 후 실제 서버·모델 왕복과 알림을 확인해야 한다.
- 전체 완료 조건은 Backend가 이 경로를 실제 호출하고 `RELATED`·`UNRELATED`의 지속 시간·알림 연결까지 통합 검증하는 것이다.

검증: `PYENV_VERSION=what-a-move PYTHONPATH=AI:. pyenv exec python -m pytest AI/tests/test_fastapi_analysis.py AI/tests/test_fastapi_reception.py AI/tests/test_merged_routes.py AI/tests/test_beta_scores.py AI/tests/test_screen_analysis_contract.py -q` 결과는 최신 테스트 실행 결과를 따른다. `git diff --check`도 통과했다. 이 테스트는 합성 입력·FakeEmbedding 기반이며 실제 Qwen·Backend 연동을 검증하지 않는다.
