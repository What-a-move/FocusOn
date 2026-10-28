# 10/26 화면 분석 계약 적용 결과

- 작업일: 2026-10-26
- 기준: 사용자가 첨부한 허재원 화면 분석·AI 분석 계약 및 10/26 변경 공지. 첨부본 상태는 REVIEW이며 실시간 Notion 조회는 수행하지 않았다.
- 대상: 원본 `what_a_move`의 AI #11 코드. 프론트/Backend/공유 타입 및 별도 Neo4j 실험 폴더는 변경하지 않았다.

## 적용한 동작

- 메타데이터 무관 후보는 UNCERTAIN/METADATA_UNRELATED_REQUIRES_BODY로 보류한다. 관련/무관 근거가 함께 있으면 기존처럼 UNCERTAIN이다. 제목 점수와 본문 점수 수치는 그대로다.
- 정상 현재 메타데이터의 non-RELATED는 사유 종류와 관계없이 TAB_META → CHROME_DOM, APP_META → DESKTOP_APP_OCR로 요청한다. 제외/민감/stale/실행 실패는 추가 수집으로 우회하지 않는다.
- CHROME_DOM 뒤 Chrome OCR 요청을 제거했다. CHROME_VIEWPORT_OCR enum은 유지하되 직접 입력해도 임베딩 없이 UNCERTAIN으로 종료한다. APP_META/Desktop OCR로 잘못 전달된 Chrome·알려진 다른 브라우저·FocusOn 자체 창은 모델 호출을 차단한다. 실제 bundle-ID/민감 화면 판단은 Client 책임이다.
- ContentInput 및 공개 ContentPayload의 text는 최대 2,000자다. 초과는 자르지 않고 입력 오류로 거부한다. 이메일/전화번호/secret 등 기존 민감정보 차단은 유지한다. Client의 보내기 전 제거·원본 캡처 범위는 별도 구현 대상이다.
- 공개 요청 `metadata`/`contentPayload.text`/`goalSnapshot`/UUID v4 `clientRunId`를 검증하는 ScreenAnalysisRequest와 Server 문맥 결합 함수 adapt_screen_request를 추가했다. Server와 목표/관측값이 다르면 거부하고, guardStatus 요청의 콘텐츠 혼합을 금지한다.
- 공개 응답 ScreenAnalysisResponse/public_response는 decision, confidence, 5종 reasonCode, modelVersion, promptVersion만 반환한다. 누락/0.75 미만 confidence는 UNCERTAIN으로 정규화한다. confidence는 호출자가 별도로 검증한 값이며 cosine/Hybrid/OCR 품질을 confidence로 계산하지 않는다. 유효하지 않은 수치/enum은 거부한다. validate_provider_response는 잘못된 Provider JSON/enum/버전/secret 필드를 폐기하고 UNCERTAIN/ANALYSIS_UNAVAILABLE로 정규화한다.
- Streamlit은 중첩 공개 JSON 입력도 지원하고 계약 reasonCode를 내부 진단과 구분한다. 공개 JSON에 없는 OCR 인식 품질을 임의로 .95로 채우지 않으므로, 해당 입력은 품질 정보가 없으면 보류한다.

## 코드 연결 방법

- 입력: `src.api.relevance_contract.ScreenAnalysisRequest.parse_safe(payload)` → Server가 검증한 ConfirmedGoal/VerifiedContext/extraction_status 및 별도 OCRMetadata로 `adapt_screen_request(...)` 호출.
- 판정: 기존 내부 분석기 호출. 현재 Streamlit의 최신 수치/점수 경로는 Graphless이며 기존 graph 기반 RelevanceAnalyzer와 별개다.
- 출력: 내부 라벨/사유와 명시적인 **판정 confidence**를 `public_response(...)`에 전달. `.model_dump(mode="json", by_alias=True)`로 공개 JSON 생성.
- guardStatus는 Server에서 저장하고 AI Adapter를 호출하지 않는다. 현재 routes.py는 비어 있으므로 이 함수 구현이 HTTP API 배포 완료를 뜻하지 않는다.

## 검증

- 모델/네트워크 없는 관련 회귀: 310 passed, 16 subtests passed. 공개 JSON·2,000자 경계·PII/차단·stale·enum/신뢰도·Chrome OCR 미호출·단계 전환·Streamlit UI를 확인했다.
- 실행 환경: 별도 기존 Python 3.13 venv의 Pydantic 2.14.1/Kiwi/Streamlit/Pytest. 원본 .env 기반 Graph LLM/DB 연결은 빈 환경 변수로 꺼서 테스트했다.
- 최초 회귀에서 테스트 런타임에 Chroma가 없어 8건이 실패했다. 외부 LLM 설정이 읽히는 것을 막은 뒤 정상 통과했다. 키 파일이나 기존 DB를 변경하지 않았다.
- 실제 Qwen/OpenAI 정확도 평가, 실제 macOS 캡처, Server/Policy Engine, 공개 HTTP 요청 왕복은 이번에 실행하지 않았다. 과거 실측 결과는 변경하지 않았으며 새 정책의 정확도로 전용하지 않는다.

## 구현 범위별 완료 체크 (AI 화면 관련성, 동일 비중 10항목)

| 항목 | 상태 |
| --- | --- |
| 목표/본문 키워드 정규화와 전처리 | 구현 |
| Qwen 벡터 기반 3라벨 판정·문단 근거/커버리지 | 구현; 실제 모델 연결은 이전 평가에서 확인 |
| 메타데이터는 RELATED만 확정 | 구현 |
| 메타데이터 non-RELATED의 후속 출처 선택 | 구현; Client 재수집 호출은 외부 연동 |
| Chrome OCR P1·Desktop Chrome 우회 방지 | 구현; 실제 Client Guard는 외부 연동 |
| 2,000자 제한과 AI 직전 잔존 민감정보 검사 | 구현 |
| Server 문맥·관측 일치 검사 및 공개 입력 변환 | 구현; Server가 기기별 최신 문맥을 공급해야 함 |
| 공개 응답 schema·reasonCode·신뢰도 정규화 경계 | 구현; 검증된 confidence 값은 외부 입력 |
| 판정 confidence 보정/독립 검증 | 미완료 |
| 실제 Client/Backend/AI 왕복과 OCR 품질 전달·공개 API 검증 | 미완료 |

위 **연결 전 AI 코드 체크리스트는 8/10(80%)**다. 동일 비중 항목 개수로 산정한 작업 관리 수치이며 제품 완성도·정확도·운영 준비도는 아니다. 타이머, 이탈 시간 집계, 경고, 사용자 인증, 실제 캡처/OCR을 포함한 전체 제품의 완성률은 이 수치로 표현하지 않는다.

## 남은 작업

1. 독립 검수 자료에서 판정 confidence를 보정/검증한다. 검증되지 않은 confidence를 상수로 채워 RELATED/UNRELATED를 확정하지 않는다.
2. 공개 계약에서 Desktop OCR 인식 품질의 위치와 Server 검증 책임을 합의한다. Client 원문 삭제와 민감 화면 전체 차단은 서로 다른 동작이므로 최신 개인정보 기준을 적용한다.
3. Server가 최신 세션·기기·출처 간 observedAt·정책 버전을 제공하고 Public Adapter를 실제 실행 경로에 연결한다. Policy Engine에서 메타데이터 UNRELATED는 이탈에 넣지 않고 UNCERTAIN 수신 시 연속 구간을 끊는다.
4. 실제 대상 창/Chrome DOM 수집, 순서 역전, 제외·민감 화면 전환, 모델 장애, 종료 세션에서 Client → Server → AI → Validator/Policy Engine 왕복을 검증한다.
