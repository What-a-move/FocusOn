# FastAPI 관련성 분석 경로

2026-11-03 사용자 요청으로 기존 #11 작업에 내부 실험 분석 경로를 연결했다. 수신 확인 API는 유지한다.

- `POST /internal/v1/analysis-runs/analyze`: 기존 수신 API와 동일한 Backend Bearer 인증, `AI/examples/analysis-reception.json` 입력 및 검증을 사용한다.
- 목표·관측 문맥 검사 후 기존 Kiwi + Qwen Hybrid 분석 함수를 실행한다. 환경 변수 `FOCUSON_EMBEDDING_URL`로 학교 API 기본 주소를 설정하며, 미설정 시 `http://127.0.0.1:11435`를 사용한다. `FOCUSON_EMBEDDING_REVISION`은 모델 캐시 버전 설정이며 서버 실측 조회값은 아니다.
- `candidateDecision`은 보정 전 Hybrid 후보 라벨이며 `candidateCalibrated: false`다. `decision`은 기존 응답 규칙을 따르며 검증된 판정 confidence가 없어 현재 `UNCERTAIN`이다. 후보 라벨을 알림·이탈 확정에 사용하지 않는다. Backend와 이 내부 확장 필드의 계약 합의가 필요하다.
- 모델 통신 또는 분석 실패 시 candidateDecision=null, decision=UNCERTAIN, reasonCode=ANALYSIS_UNAVAILABLE. 오류와 요청 본문은 응답에 노출하지 않는다. 민감·제외·오래된 관측은 수신 단계에서 차단한다.
- `GET /health`의 analysisEnabled는 분석 경로가 존재한다는 뜻이며 Qwen의 준비 상태나 실제 연결 성공을 뜻하지 않는다.

요청은 수신 예제와 같으며 서버에 `FOCUSON_AI_API_KEY`, `FOCUSON_EMBEDDING_URL`을 설정하고 `/docs`에서 해당 분석 경로를 호출한다. 실행법: [FastAPI 수신 안내](fastapi-reception-REPORT.md). 기존 `.env`와 비밀값은 변경하지 않았다.

검증: 전체 AI 회귀 289 passed, 16 subtests passed. 실제 Hybrid 로직에 합성 벡터를 주어 RELATED·UNRELATED·UNCERTAIN 후보, 모델 오류·민감정보 차단을 검사했다. 실제 학교 Qwen 요청은 Tailscale 호스트 DNS 조회 실패로 후보를 얻지 못했으며 ANALYSIS_UNAVAILABLE을 확인했다. 실제 Backend 왕복, 성능, 독립 정확도와 판정 confidence 보정은 미검증이다.
