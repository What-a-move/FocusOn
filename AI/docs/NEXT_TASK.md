# AI 다음 작업

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: 학교 임베딩 API 계약을 기존 Ollama Client에 연결하고 독립 평가 준비
- 담당 영역: AI
- 우선순위: 높음
- 상태: 실제 Qwen 벡터 생성과 임시 Adapter 기반 합성 파이프라인 확인. 표준 Client와 학교 API 계약, 공개 API·Server 연동 및 정확도 검증은 미완료

## 작업 목적

학교 검증 FastAPI의 `/health`·`/api/embed` 계약을 현재 Embedding Client가 요구하는 모델 메타데이터 검증과 연결한다. 공개 API·Server 계약은 담당 영역과 합의된 뒤 연결하고, 실제 모델 판정은 독립 검수한 평가 세트로 측정한다.

## 확인된 근거와 남은 범위

- 학교 Qwen `qwen3-embedding:4b-fp16` 실제 반환 벡터는 2560차원·유한값·정규화 상태였다. 최초 warm-up 및 반복 요청 시간은 REPORT의 2026-10-14 기록 참조.
- 임시 메모리 Adapter로 학교 `/health`·`/api/embed`를 호출해 개발용 합성 사례를 실행했다. 이는 표준 Client·공개 API 연동이나 모델 정확도 통과를 뜻하지 않는다.
- 기존 Ollama Client는 `/api/tags`, `/api/show`, `/api/version`도 조회하지만 학교 검증 FastAPI는 해당 경로를 제공하지 않는다. 모델 revision·차원·정밀도·기능을 안전하게 전달하는 계약을 먼저 맞춘다.
- 8개 합성 개발 사례에서 실제 분석된 6개 중 기대 라벨 일치 3개, 불일치 3개였다. 기대 라벨은 독립 검수 전이므로 정확도 지표로 사용하지 않는다.
- 학교 GPU 사용률·VRAM, 독립 정답 세트 기반 정확도, 운영 부하 성능은 미측정이다.
- 기존 개인정보·실패·중복·stale 단위 테스트를 계속 통과시킨다.


### 최신 dev 계약 확인 항목

- 루트 `docs/API_CONTRACT.md` 확인
- `DEVELOPMENT_RULES.md`, `state_model.md`, `data_lifecycle.md`, `evaluation_spec.md` 확인
- `goal_session_spec.md`의 식별자·회차·멱등·시간 집계 경계 확인
- `content_acquisition_spec.md`의 AI 입력과 비분석 상태 계약 확인
- `focus_session_agent_spec.md`, `feedback_personalization_spec.md`, `session_note_spec.md`는 후속 구현 경계로 사용
- Desktop·Extension에서 전달할 최소 데이터 확인
- Server의 AI 요청·결과 저장 범위 확인
- 원본 화면·카메라 영상 미저장 원칙 확인
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, AI 서버의 원본 이미지 비수신 경계 확인
- Chrome Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로를 분리 확인
- 목표 보조 6개 `clarityStatus`, 추천·질문·직접 입력 장애 대안 확인
- `ANALYSIS_ONLY`와 `ANALYSIS_AND_TIME`, `sessionStatus`, `resumeRequired` 계약 확인
- AI 내부 라벨과 제품 표시 라벨 매핑 확인
- 개인화 휴식의 최근 7일 규칙과 카메라 비필수 경로 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 관련 기능의 PLAN·ERROR·REPORT 파일 준비


## 예상 작업 순서

1. Issue #10의 확정 JSON 예시와 공유 enum을 확인한다.
2. 외부 요청 Schema에서 내부 `PreprocessingRequest`로 변환하는 Adapter를 구현한다.
3. 알 수 없는 필드·enum·잘못된 식별자와 범위를 안전하게 거부한다.
4. Adapter가 원문 값을 오류 메시지나 로그에 포함하지 않는지 확인한다.
5. Issue #2의 API 경계에 연결하고 계약 테스트를 실행한다.

### Server·Client 공통 계약 작업


1. `/api/v1/analyze/relevance`, `/api/v1/feedback`, `/api/v1/goals/profile`의 AI 경계를 Server·Client 담당자와 확정한다.
2. AI 내부 라벨과 제품 표시 라벨, 기존 `FocusState` 호환 전략을 공유 타입 담당자와 확정한다.
3. `goalId`, `sessionId`, `runId`, `eventId`, `navigationId`와 `sessionStatus`, `exclusionMode`, `resumeRequired`의 필수 범위를 확정한다.
4. 목표 보조 6개 상태와 AI 장애 시 직접 목표 시작 경로를 확정한다.
5. FastAPI, Pydantic, pytest와 서비스 인증·설정 주입 방식을 확정한다.
6. Health Check, 공통 성공·실패 envelope, Schema 검증 테스트를 구현한다.
7. 로그에 원문·비밀 값이 남지 않는지 검사한다.
8. 현재 관련성 분석은 통합 #11에서 진행하고, Agent·노트·ColPali 등 범위 밖 작업은 별도 Issue로 관리한다.

## 이후 작업 후보

- Issue #2 FastAPI 기반 AI 분석 서버 기본 구조 구현
- Server 관련성 분석 요청·결과 저장 연동
- Chrome 내부 OCR·Desktop Apple Vision OCR 왕복 PoC와 실패 경로 검증
- ColPali 시각 콘텐츠 기준선 비교 PoC
- 페이지 이동·체류 시간 기반 학습 흐름·목표 이탈 분석
- MediaPipe 얼굴·시선·자세 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동
- 평가 자동화와 모델·Prompt Version 관리
- `AI/evaluation/SIMILARITY_EVALUATION_GUIDE.md`에 따라 실제 임베딩·재정렬 모델과 고정 정답 세트로 1~7번 방식 평가; 임계값·속도·무관 판정 근거 검증
- 로컬 Streamlit 실험 화면의 후보 모델을 RTX A4000에서 실행하고 모델 리비전·GPU 환경을 기록한 뒤, 사람 검수 정답 세트로 방식별 성능과 보류율을 측정

## 완료 조건

- [ ] Issue #10의 JSON 필드명과 enum이 Adapter에만 반영된다.
- [ ] 외부 Schema와 내부 모델의 필수값·범위가 일치한다.
- [ ] 잘못된 입력이 원문을 노출하지 않는 안전한 오류로 반환된다.
- [ ] 기존 Issue #11 단위 테스트와 신규 계약 테스트가 통과한다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DEVELOPMENT_RULES.md`와 적용 Rule ID를 확인했다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] Issue #10의 확정 계약을 확인했다.
- [ ] `content_acquisition_spec.md`의 Issue #11 내부 계약을 확인했다.
- [ ] Desktop·Extension·Server 담당자와 계약을 공유했다.

## 2026-10-07 학교 캡스톤 다음 작업 — 통합 #11

1. [학교 지침 PLAN](features/learning-goal-content-relevance-evaluation-PLAN.md)을 읽고 기존 전처리 구현을 확인·재사용하고 같은 통합 #11의 관련성 모듈로 연결한다. 미커밋 변경을 임의로 게시하지 않는다.
2. 검토된 Goal Graph·키워드/벡터 결합·역할별 근거·3라벨 로직과 Fake Client 테스트를 구현한다.
3. GPU/WSL2/vLLM 호환성·모델 리비전을 확인하고 실제 Qwen3 4B·FP16 임베딩 API를 연결한다.
4. 합성 평가 CLI를 구현해 모델 없는 테스트와 real 측정을 분리하고 개발 세트 조정 후 독립 평가한다.
5. 백엔드 필드 9개·실제 출력 Schema·최근 흐름/추가 LLM의 미합의 항목을 확인하고 결과와 제한을 기록한다.

문서에 제시한 테스트·CLI 명령은 해당 구현 파일을 만든 뒤 검증한다. 이 기록을 기능 구현 완료로 보지 않는다.

앞으로 이 기능의 작업은 #11과 `feat/11-ai-preprocessing-relevance`에서 진행한다. 기존 #14 Issue/Branch를 새 개발 대상으로 사용하지 않는다.

## 2026-10-07 코드 게시 이후 확인

- 기존 전처리/실험 테스트 30개를 유지하면서 최종 qualityScore 계산과 text·ocr 입력 Adapter를 최신 통합 PLAN에 맞춘다.
- Server가 검증한 현재 실행 상태·미합의 필드 9개의 경계를 확인한다. 일부 내부 stale 검사가 전체 API 연동 완료를 뜻하지 않는다.
- 기존 후보 비교 화면은 실험 이력으로 보존하고 새 운영 경로는 Qwen3 4B·FP16·Hybrid 6번을 구현·평가한다. 실행 도구는 이번에 임의 변경하지 않는다.
- 실제 실행 결과와 제한은 [부분 구현 게시 결과](features/learning-goal-content-relevance-evaluation-REPORT.md)에 기록한다.

## 2026-10-07 초기 임베딩 환경 테스트

1. [학교 Ollama 설치 지침](features/school-ollama-embedding-SETUP.md)을 학교 PC에 전달하고 Qwen3 4B·FP16 다운로드·벡터 반환·GPU 사용을 확인한다.
2. 합성 입력으로 학교 로컬 첫 요청·warm P50/P95와 Mac 왕복 시간을 구분해 기록한다. Mac 연결 미실행을 학교 자체 테스트 완료와 혼동하지 않는다.
3. 이후 Mac Embedding Client를 연결하고 Hybrid 구현을 진행한다. 기존 vLLM 준비 작업은 초기 Ollama 테스트 이후의 전환 단계로 진행한다.

## 2026-10-07 실제 개발 이후 다음 작업

1. 학교 PC의 Ollama F16 모델 digest와 서비스 버전을 확보하고 SSH 터널로 `FOCUSON_EMBEDDING_URL`·`FOCUSON_EMBEDDING_REVISION`을 설정한다. Client와 Hybrid·평가 CLI는 구현되어 있다.
2. [학교 설치 지침](features/school-ollama-embedding-SETUP.md)의 real 평가 명령으로 합성 데이터만 측정한다. 학교 로컬 warm 지연과 Mac 전체 파이프라인 지연을 구분한다.
3. 초기 Graph 템플릿·무관 주제 근거·합성 정답을 사람이 검수한다. 개발 split에서 숫자 정책을 보정한 뒤 설정을 고정하고 별도 목표군 evaluation split을 평가한다.
4. 팀 합의 공개 응답 Schema·confidence 의미와 Server의 9개 문맥 필드 책임을 확보해 내부 결과를 공개 계약에 매핑한다. `VerifiedContext` 공급·현재 상태 조회·종료/삭제 시 `clear_caches()`를 Server에 연결한다.
5. 대표 입력 상한을 넘는 요청은 현재 `INCOMPLETE_REPRESENTATION`으로 보류한다. Client의 대표 문단 선정 계약을 확인한 후 순서와 본문 대표성을 보존하는 개선을 검토한다.
6. 최근 3~5개 활동 요약·추가 LLM은 제공자와 예산 합의 후 연결한다. vLLM 전환은 동일 모델·정밀도·instruction·평가 조건으로 별도 검증한다.

학교 연결·계약 정보가 확보되기 전에도 모델 없는 테스트와 fake 개발/평가 CLI는 로컬에서 실행할 수 있다. 공개 API·GPU·독립 성능 검증을 완료로 표시하지 않는다.

## 2026-10-11 제목 반영 이후 확인

- 완료: 제목·명시 Markdown 소제목의 Hybrid 매칭/충돌/근거 반영, 본문 커버리지 분리, 코드 언어·문자열 보존, 앱·도메인별 결과 캐시 분리. 모델 없는 테스트 165 passed, 6 subtests passed.
- 문서 정합성 반영 완료: Notion AI 최종 기획서 (2), 이재빈 전처리·관련성·유사도 6번 설명을 현재 로컬 구현과 맞추고 재조회 검증했다. 기존 입력·출력 JSON 예시와 미합의 공개 계약 경계를 유지했다.
- 학교 실측: 같은 본문에 관련/무관/중립 제목을 각각 붙인 사례, 제목 없는 OCR, 제목만 있는 입력, 코드 언어·fence 보존 사례를 실제 Qwen3 4B·FP16으로 비교한다. 변경된 보류율과 오탐을 검수하고 기존 임계값을 확정값으로 취급하지 않는다.
- 입력 연결: 김성현 측에서 실제 보내는 합성 JSON으로 `pageTitle`·`appName`·`urlHost`와 `text`의 구조 보존을 확인한다. `windowTitle`·포커스·별도 코드 언어/소제목 필드는 전달 위치를 합의한 뒤 반영한다.
- 학교에는 개정한 [단일 MD 지침](features/school-ollama-embedding-SETUP.md)을 전달한다. 실행 전 실제 코드가 `school-text-v2`·`school-hybrid-v2`인지 확인하고, 지침서의 학교 벡터 확인과 fake/real 평가 순서를 따른다. MD 전달만으로 최신 코드 전송이나 실제 모델 검증이 완료됐다고 표시하지 않는다.

## 2026-10-12 더미 데이터 갱신 이후

- 최신 `synthetic-school-v2` JSON 16개와 2026-10-12 개정 학교 MD를 사용한다. 기존 CLI 명령과 입력 컬럼은 유지한다.
- 독립 검수한 expected로 real 개발 split을 실행해 제목·소제목의 실제 매칭과 충돌/커버리지 결과를 확인한다. 설정을 동결한 후 목표군이 분리된 evaluation split을 평가한다.
- fake의 개발/평가 8/8 일치와 166개 테스트 통과는 로직 검증이다. 실제 모델 digest·GPU·임베딩/전체 지연과 오탐·보류율을 별도로 기록한다.
- 학교 연결 확인에서 Mac 11435가 연결 거부를 반환했다. 실제 SSH 사용자·호스트·포트를 확보해 터널을 연결하고 `/api/tags`·`/api/show`·`/api/version` 검증, 합성 텍스트 임베딩, 최신 16개 real 평가 순서로 진행한다. 학교 localhost:8000 검증용 FastAPI가 생겼다는 사실만으로 현재 Ollama Client 연결 완료로 표시하지 않는다.
- 새 SCHOOL_LAN.md 경로의 현재 선행 작업: 학교 관리자 PowerShell에서 Mac IP만 허용하는 Enable-MacAccess.ps1 실행. 사용자는 아직 미실행이라고 확인했다. 이후 Mac에서 `10.125.43.92:8000/health`·합성 `/api/embed`를 재확인한다. 학교 LAN FastAPI를 기존 Ollama Client에 연결하려면 `/health` 기반 모델 검증 계약과 제한된 LAN HTTP 경계를 별도로 확인해야 한다.

## 2026-10-13 선택한 연결 경로 — Tailscale

1. Mac의 공식 설치 파일을 사용자가 열어 설치·약관 확인·로그인·VPN 허용을 완료한다. 학교 PC도 동일 tailnet에 로그인한다.
2. 학교 로컬 `127.0.0.1:8000/health`가 정상인지 확인하고 기존 Serve 설정을 확인한 뒤 해당 API를 private HTTPS Serve로 연결한다.
3. 실제 HTTPS 주소와 `tailscale serve status`를 받으면 Mac에서 health·합성 임베딩 개수/2560차원/유효값을 검증한다.
4. 실제 health 응답으로 모델 digest·F16·capability 확인 계약을 정리하고 필요한 학교 FastAPI Client 연동 후 최신 16개 real 평가를 진행한다.

공식 설치 파일과 전달용 MD는 준비했다. Computer Use 권한 대기로 설치 UI 자동 조작을 완료하지 못했으며 설치/로그인/연결 성공은 아직 미확인이다.
