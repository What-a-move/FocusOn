# 입력 검증·전처리 및 Hybrid 관련성 구현 결과

> Streamlit의 최신 상태는 맨 아래 2026-10-21 절을 따른다. 이전 화면·모델 설명은 당시 기록이다.
> 최신 로컬 구현과 2026-10-14 학교 Qwen 연결 확인은 아래 날짜별 기록을 따른다. 공개 API·Server 연결 및 독립 정확도 평가는 미완료다.

## 기본 정보

- 기능명: 입력 검증·전처리 및 학습 목표·콘텐츠 관련성 판단
- 기능 ID: relevance-analysis
- 작성자: 이재빈 작업 공간 / Codex 검증
- 작성일: 2026-10-07
- 관련 기획서: [통합 PLAN](learning-goal-content-relevance-evaluation-PLAN.md)
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- 관련 Branch: `feat/11-ai-preprocessing-relevance`
- 상태: 학교 내부 전처리·Graph·Hybrid·Ollama Client·평가 CLI 구현. 기본 Client의 임베딩 전용 학교 연결과 실제 합성 사례 RELATED 반환 확인. 공개 API·GPU 자원 및 독립 정확도 검증 미완료.

## 구현 결과

- DOM/OCR 내부 입력 모델과 문단/코드/표 순서를 보존하는 정제·청크·해시 모듈을 게시한다.
- 개인정보·추출 실패·품질 부족·중복·일부 run/목표/navigation stale 검증을 수행한다.
- 기존 GoalProfile과 합성 본문으로 후보 1~7을 비교하는 Streamlit 실험 코드를 보존한다. 현재 MVP 채택은 Hybrid 6번이며 실험 화면을 운영 판정기로 사용하지 않는다.
- 학교 FastAPI의 `/api/embed`는 기본 Embedding Client와 연결했다. 합의된 공개 API·현재 실행 문맥 전체 연결은 아직 완료가 아니다.

## 변경 파일

- `AI/src/preprocessing/models.py`, `content_parser.py`, `text_cleaner.py`, `validator.py`
- `AI/tests/test_content_parser.py`, `test_text_cleaner.py`, `test_preprocessing_validator.py`, `test_similarity_streamlit.py`
- `AI/evaluation/similarity_methods.py`, `similarity_streamlit.py`, 합성 사례·실험 의존성·HTML 비교 도구·평가 지침
- AI 문서의 현재 상태·다음 작업 및 이 결과 리포트

## 기능 테스트

Python 3.11.6 환경에서 다음 모델 없는 테스트를 실제 실행했다.

```bash
PYTHONPATH=AI python3 -m pytest AI/tests/test_content_parser.py AI/tests/test_text_cleaner.py AI/tests/test_preprocessing_validator.py AI/tests/test_similarity_streamlit.py -q
```

- 결과: 30 passed, 6 subtests passed.
- 정제·청크·중복·해시·민감 후보·추출 실패·품질 부족·버전 불일치와 실험 계산 경계를 검증한다.
- 임베딩·재정렬 관련 테스트는 Fake Client를 사용한다. 실제 모델 정확도·GPU 지연·네트워크 왕복·Server 인증을 검증한 결과가 아니다.
- Streamlit 실제 브라우저 실행·GPU 모델 추론은 이번 게시 작업에서 실행하지 않았다.

## 평가 결과·제한사항

- 고정 테스트의 안전 로직 통과는 실제 모델 정확도나 MVP 출시 기준 충족을 뜻하지 않는다.
- 내부 quality_score는 현재 입력값을 품질 검사에 사용한다. 최신 PLAN의 '전처리에서 최종 qualityScore 산출' 산식과 입력 Adapter 연결은 후속 작업이다.
- 내부 CurrentAnalysisContext는 일부 run/goalVersion/navigation 비교를 수행한다. Server 검증 문맥·sessionStatus/exclusionMode·관찰 시각·응답 직전 재검증 전체가 구현된 것은 아니다.
- Streamlit은 이전 기본 모델 `intfloat/multilingual-e5-small`과 선택적 `BAAI/bge-reranker-v2-m3`를 사용한다. 해당 코드가 Qwen3 4B·FP16 또는 현재 Hybrid 6번 운영 코드로 바뀐 것은 아니다.
- 후보 비교 도구의 제한된 직접/보조 Graph와 임시 점수 규칙은 실험 이력이다. 선수 관계·관계 근거·정규화·최종 임계값은 최신 PLAN 기준으로 보완한다.
- 로딩/실행 시간, Precision/Recall/Macro-F1·P50/P95 및 사용자 전달 328ms는 이번 로컬 테스트가 새로 검증한 수치가 아니다.
- 최근 활동 보완, 정책 계층·알림, 원격 임베딩 Client·캐시, FastAPI·Server 연동은 미완료다.

## 보안·오류·문서 확인

- 게시 대상 소스에서 알려진 비밀 토큰 패턴을 검사했으며 자격 증명 값을 출력하지 않았다.
- 기존 ZIP 패키지와 개인 LangChain 호출 스크립트는 공개 코드 커밋에 넣지 않고 로컬에 보존한다.
- 모델 파일·가상환경·.env는 게시 대상이 아니다. 테스트 Fixture는 합성 데이터다.
- 테스트는 실제 실행 결과만 기록한다. 원문·비밀 값·모의 정확도는 결과로 남기지 않는다.
- CONTEXT·NEXT_TASK에는 부분 구현과 후속 작업 경계를 유지한다.

## 최신 dev 통합

- dev의 AI 계약 문서 커밋을 함께 가져오고 로컬 구현·학교 지침을 보존했다.
- 결정 번호 중복은 dev의 015~019를 유지하고 이 Branch 기록을 020·021로 옮겨 해결했다.
- 모델 실행 방식·공유 API·품질 산식을 이번 게시 작업에서 새로 확정하지 않았다.

## 2026-10-07 통합 #11 실제 개발 결과

### 구현 범위

- Pydantic 학교 내부 입력·검증 문맥·확정 목표를 분리했다. JSON 필드·enum·형식·필수값·UTC·원문 없는 검증 오류를 검사한다. 실제 Server 인증을 수행한 결과가 아니다.
- 공통 text/ocr Adapter는 코드·표·오류 유형과 fenced code의 구조·문단 읽기 순서를 보존하며 기존 정제·청크·해시 모듈을 재사용한다.
- Client qualityScore를 최종값으로 신뢰하지 않고 원천 OCR 품질·깨진 문자·UI/중복 제거에 따라 최종 qualityScore를 계산한다. 산식은 결정 023에 남긴 미보정 초기 정책이다.
- PLAN 사례에서 시작한 5개 목표 템플릿은 DIRECT/PREREQUISITE/SUPPORTING·동의어·연결 근거·버전을 관리한다. 페이지가 목표 Graph를 확장하지 않는다. 템플릿 전체의 독립 검수는 남아 있다.
- 키워드 경계·동의어와 검증/정규화한 cosine을 역할별로 결합한다. 제목·우연한 한 문단·낮은 cosine만으로 전체 관련성이나 무관을 단정하지 않는다. 상한으로 본문을 잘라 대표성이 불확실하면 모델 미호출·UNCERTAIN이다.
- Ollama `/api/embed` Client는 배치·timeout·제한 재시도·동시 요청·입력/응답 크기 상한과 model digest/F16/차원/capability 확인을 제공한다. query에만 instruction을 넣고 본문에는 넣지 않는다. 실제 모델 설치·GPU 로딩·속도를 확인한 것은 아니다.
- 유한 TTL/LRU 벡터·결과·이벤트 캐시는 해시·벡터·최소 결과만 저장한다. 사용자·목표·run·Graph·모델·정책·추출기·전처리·instruction·설정 변경에 재사용하지 않는다. 중복 이벤트의 내용이 다르면 재사용을 거절한다.
- 분석 시작·목표 벡터 계산 후·결과 반환 직전에 최신 실행 문맥을 검사한다. 캐시 hit도 다시 검사하며 제외/상태 변경/모델 실패 시 현재 요청에 이전 판정을 반환하지 않는다.
- 평가 CLI는 mode/cases/config/split을 제공한다. 개발·평가 목표군은 겹치지 않는다. fake는 사전 지정 벡터의 로직 검사이고 real은 연결·모델 실패를 오류로 반환한다.

### 변경 파일

| 경로 | 역할 |
| --- | --- |
| `AI/src/api/schemas.py`, `preprocessing/adapter.py` | 학교 내부 입력·검증 문맥·text/ocr 경계 |
| `AI/src/preprocessing/models.py`, `validator.py`, `content_parser.py` | 최종 품질·추가 개인정보 검사·잘림 검출 |
| `AI/src/analysis/goal_graph.py`, `hybrid_scoring.py`, `relevance_analyzer.py` | 목표별 역할·근거·Hybrid·캐시·이벤트·stale 파이프라인 |
| `AI/src/models/embedding_client.py`, `utils/cache.py` | Ollama HTTP·검증·유한 TTL/LRU |
| `AI/evaluation/relevance_eval.py`, `relevance_cases.json`, `relevance_calibration.json` | 합성 CLI·목표군 분할·실험 정책 |
| `AI/tests/test_hybrid_scoring.py`, `test_relevance_pipeline.py`, `test_embedding_client.py`, `test_relevance_eval.py` | 순수 로직·파이프라인·HTTP Mock·CLI 회귀 검증 |

### 실제 실행한 검증

Python 3.11.6 / pytest 9.1.1 / Pydantic 2.13.5 / HTTPX 0.28.1에서 저장소 루트 기준 실행했다.

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q --ignore=AI/tests/langsmith_test.py
python3 -m AI.evaluation.relevance_eval --mode fake --split development --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
python3 -m AI.evaluation.relevance_eval --mode fake --split evaluation --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

- 전체 테스트: **130 passed, 6 subtests passed**. 기존 모델 없는 테스트 30개를 포함한다.
- fake 개발 세트: 8/8 기대 라벨 일치·종료 코드 0.
- fake 별도 목표군 평가 세트: 8/8 기대 라벨 일치·종료 코드 0.
- 이 16개 기대값은 PLAN 기반 합성 Fixture이며 사람의 독립 정답 검수는 대기다. fake 벡터로 얻은 일치율을 모델 정확도·Macro-F1·GPU P50/P95로 보고하지 않는다.
- 모델 미호출, 민감/제외·실패, 목표/회차/이벤트/navigation/상태 변경, 결과 반환 직전 변경, 멱등 충돌·병렬 중복, 사용자/목표/정책/모델 경계, 제목·본문 대표성, 코드·표 구조, 벡터 오류·timeout·원문 없는 오류/캐시를 확인했다.
- Ollama는 HTTP Mock으로 payload·메타데이터·배치·벡터 오류·재시도·model tag 변경을 확인했다. 실제 Ollama 서비스 호출 성공을 뜻하지 않는다.
- real 모드에 model digest를 설정하지 않으면 종료 코드 2·FAILED·fakeFallback=false임을 CLI 회귀 테스트로 확인했다.
- 기존 `langsmith_test.py`는 로컬 외부 호출 스크립트로 실행·수정하지 않았다. ZIP·모델·가상환경·.env도 이번 구현 산출물에 포함하지 않는다.

### 적용 규칙

- AI-PRIV-001/002: 제외·민감 입력은 모델 미호출, 원문은 결과·오류·캐시에 포함하지 않는다.
- AI-PIPE-001/FAIL-001/STATE-001: 검증→정제→Graph/임베딩→판정 순서와 비분석·실패·관련성 상태를 분리한다.
- AI-STALE-001: 최신 문맥을 요청 시작과 중간·응답 직전에 조회하고 오래된 근거를 폐기한다.
- AI-LLM-001: 추가 LLM·Agent·도구를 호출하지 않는다. 페이지 안 지시는 텍스트 데이터로만 처리한다.
- AI-EVAL-001/VERSION-001: 합성 분할·설정/데이터 해시·모델/Graph/정책/전처리 버전을 구분한다.

### 제한·다음 검증

- 학교 서버 주소·SSH 연결·정확한 model digest·GPU 정보가 없어 실제 Qwen3 4B/F16 정확도·warm 지연·VRAM·Mac 왕복을 측정하지 않았다.
- 공개 FastAPI·팀 합의 응답/confidence·Server 인증/소유권·DB 기록·9개 문맥 컬럼의 최종 합의는 미완료다. `RelevanceResult`는 내부 결과이며 공개 응답 계약을 대체하지 않는다.
- 숫자 결합 비율·품질 산식·역할별 임계값·대표 본문 coverage·TTL은 실험 설정이다. 개발 세트에서 조정 후 동결한 독립 평가가 필요하다.
- 무관 주제 검사는 명시한 소수 주제만 다룬다. 근거가 부족한 새 주제·목표·표현은 UNCERTAIN을 유지한다. Graph의 선수·보조 관계는 독립 검수 전 템플릿 초안이다.
- 최근 3~5개 활동 요약·추가 LLM은 연결하지 않았다. 애매한 결과는 UNCERTAIN이며 후속 알림·체류 집계·사용자 수정/개인화는 이번 범위에 없다.
- 캐시는 프로세스 내부 TTL/LRU다. Server의 영속 멱등 검증·최신 상태 조회와 종료/삭제 시 `clear_caches()` 연결이 필요하다. 요청 사이의 강제 상태 변경을 관찰할 수 없는 경우 callback만으로 원자적 적용을 보장하지 못하므로 Server도 적용 직전에 검증해야 한다.
- 본문 상한을 넘는 입력은 대표 문단 선정 계약이 확인될 때까지 보류한다. vLLM Client·전환 평가는 학교 Ollama 초기 테스트 이후 후속 작업이다.
- [ERROR 기록](learning-goal-content-relevance-evaluation-ERROR.md)의 CLI 종료 판정·테스트 정리 오류를 해결했다. CONTEXT·NEXT_TASK·결정 023·PLAN·학교 설치 지침을 실제 상태로 갱신했다.
- 2026-10-19: 기존 `AI/tests/langsmith_test.py`를 수동 실행 스크립트 `AI/scripts/langsmith.py`로 이동했다. 현재 회귀 테스트 명령은 별도 제외 옵션 없이 `AI/tests`를 실행한다.
- 로컬 개발 결과이며 커밋·원격 게시·PR·배포 완료를 의미하지 않는다.

## 2026-10-11 제목·소제목·코드 정보 보완

### 변경

- `pageTitle`은 이전에 임베딩한 뒤 매칭에서 제외됐다. 이제 제목과 명시된 Markdown h1~h3에 기존 키워드·cosine·Hybrid 계산을 적용한다.
- 관련·무관 통과 근거가 제목/소제목/본문에 함께 있으면 `UNCERTAIN`으로 반환한다. 제목·소제목 길이는 본문 커버리지에서 제외하며 본문 근거가 충분할 때 같은 방향의 제목·소제목 ID도 반환한다.
- `text` 안의 코드 fence·언어·설명·들여쓰기를 보존한다. CODE의 `&lt;` 같은 문자열 값을 `<`로 바꾸던 오류를 수정했다. 개인정보 검사는 별도 decode를 유지한다.
- 기존 `appName`·`urlHost`가 달라지면 결과를 다시 계산하되 목표 벡터는 재사용한다. 제목 변경도 정제 해시를 통해 결과 캐시를 구분한다.
- 공개 JSON 컬럼·숫자 가중치·임계값은 변경하지 않았다. 내부 `HEADING` 종류, `school-text-v2`·`school-hybrid-v2` 버전과 판정 버전 캐시 구분을 반영했다.

### 이번 변경 파일

- `AI/src/analysis/hybrid_scoring.py`, `relevance_analyzer.py`
- `AI/src/preprocessing/adapter.py`, `models.py`, `text_cleaner.py`
- `AI/tests/test_hybrid_scoring.py`, `test_relevance_pipeline.py`, `test_text_cleaner.py`, `test_input_adapter.py`(신규)
- 이 REPORT, 해당 PLAN, `AI/docs/CONTEXT.md`, `NEXT_TASK.md`, `DECISION_RECORD.md` 결정 024

### 검증 결과

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=AI:. python3 -m pytest AI/tests -q --ignore=AI/tests/langsmith_test.py -p no:cacheprovider
git diff --check -- AI
```

- **165 passed, 6 subtests passed**. fake 개발/평가 CLI 회귀 검사도 포함한다. `git diff --check` 통과.
- 제목·소제목의 점수/근거, 양방향 제목-본문 충돌, 제목 길이에 의한 커버리지 왜곡 방지, 소제목만 있는 입력, 코드 fence·info string·literal, 개인정보 차단, 앱/도메인 변경 시 캐시와 목표 벡터 재사용을 확인했다.
- 통합 검사에서 소제목의 `##` 표시를 제거한다고 가정한 신규 테스트 1개가 실패했다. 원문 표시를 보존하는 구현에 맞춰 기대값을 수정한 뒤 전체 검사를 통과했다.
- 실제 Qwen3/Ollama/GPU 호출은 실행하지 않았다. 점수 정확도와 제목 충돌에 따른 보류율은 독립 정답 데이터로 평가해야 한다.
- 소제목 구분은 `text`에 명시된 Markdown h1~h3에 한정된다. 추출기가 전달하지 않은 창 제목·포커스·코드 언어를 생성하지 않으며 메타데이터만으로 판정하는 새 경로는 포함하지 않는다.

## 2026-10-12 최신 기준 더미 데이터 16개 갱신

- 데이터 버전 `synthetic-school-v2`, 개발 8개·평가 8개. 기존 필드 형식과 목표 템플릿 분리를 유지했다.
- 제목·소제목의 관련/무관 근거, 양방향 제목-본문 충돌, 소제목 충돌, 본문 없음, 코드 언어/entity 보존, 제목 없는 OCR, 긴 제목·소제목의 커버리지 제외를 기존 사례에 반영했다.
- fakePassageVectors를 TITLE·HEADING 포함 정제 단위의 수·순서와 맞췄다. expected는 기획/정책 기반 초안이며 real 모델의 정답 확정을 뜻하지 않는다.
- fake 개발 CLI 8/8, 평가 CLI 8/8 기대 라벨 일치·종료 코드 0. 전체 테스트 **166 passed, 6 subtests passed**. 충돌을 일반 근거 부족으로 처리하는 오검증을 막기 위해 reasonCode·evidenceIds 검사도 추가했다.
- 기존 공개 입력·출력 컬럼, 분석기·Graph·가중치·임계값은 변경하지 않았다. 실제 Ollama/Qwen3/GPU 호출은 수행하지 않았다.
- 변경: `AI/evaluation/relevance_cases.json`, `AI/tests/test_relevance_eval.py`, 관련 PLAN·이 REPORT·학교 SETUP·CONTEXT·NEXT_TASK. 기존 미커밋 변경은 보존했다.

## 2026-10-14 학교 Qwen 실제 연결 및 합성 Hybrid 실행

- 학교 Tailscale Serve의 `/health`가 HTTP 200을 반환했고 모델 `qwen3-embedding:4b-fp16`, digest `3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc`, 2560차원, F16, embedding capability를 확인했다.
- 실제 한국어 입력 1건의 임베딩은 2560개 유한값, norm 1.0으로 반환됐다. Mac 왕복 12.409초 중 Ollama 보고 시간 6.238초, 모델 로딩 6.176초였다. 이후 같은 텍스트의 요청은 왕복 2.619초·16.419초·5.704초, Ollama 내부 0.245~0.329초, 로딩 0.012~0.014초였다. 큰 왕복 편차가 확인되어 Tailscale만의 지연으로 단정할 수 없다.
- 표준 `relevance_eval --mode real`은 학교 검증 API가 `/api/tags`, `/api/show`, `/api/version`을 제공하지 않아 그대로 실행할 수 없었다. 임시 메모리 Adapter가 `/health` 메타데이터를 읽고 실제 `/api/embed`를 호출해 분석기에 주입했다. 이 Adapter는 저장소 코드에 포함되지 않는다.
- 개발 분할 8개 중 본문 부족·BLACK_SCREEN으로 2개는 분석을 건너뛰었고, 6개 분석 결과 중 기대 라벨 일치는 3개, 불일치는 3개였다. 사례 기대값은 독립 사람 검수 전이므로 정확도나 모델 품질로 해석하지 않는다.
- 전체 경과 58.39초, 임베딩 API 요청 지연 p50 6.08초·p95 23.31초, Ollama 내부 시간 p50 약 0.35초·모델 로딩 p50 약 0.013초였다. 분석 사례별 세부 시간은 실행 조건에 종속된다. GPU 사용률·VRAM은 수집하지 않았다.
- 로컬 검증은 **166 passed, 6 subtests passed**, fake 개발·평가 split은 각 8/8 기대 라벨 일치였다. 이는 합성 로직 검증이며 실제 모델 정확도를 증명하지 않는다.
- 다음 단계는 모델 revision·정밀도·차원을 기존 Client가 검증할 수 있도록 학교 `/health`·`/api/embed` 계약과 Client를 연결하는 것이다. 그 다음 독립 검수 정답 세트, GPU telemetry, 같은 입력의 warm 반복 및 Mac 왕복을 분리 측정한다.

## 2026-10-17 화면 메타데이터 우선·순서 보호 반영

- `APP_META`·`TAB_META`에서 같은 Goal Graph 개념 aliases 2개 이상이 명확히 일치하면 임베딩 없이 판정한다. 한 단어·빈 정보·관련/무관 충돌은 `UNCERTAIN`이다.
- 내부 입력의 `eventId`를 `clientRunId`로 교체해 전처리 event key에 매핑했다. `sequence`, `contentSource`, `isActiveTarget`, `windowTitle`을 추가하고 최신 문맥과 분석 전·중·반환 전에 대조한다.
- 비활성 화면은 `UNCERTAIN/INACTIVE_TARGET`, 같은 출처의 낮은 sequence는 `DISCARDED/STALE_EVENT`로 처리한다. 추출 실패·지원하지 않는 메타데이터도 `UNRELATED`로 변환하지 않는다.
- 내부 결과에 `client_run_id`, `sequence`, `content_source`를 연결하고 분석기 버전을 `school-hybrid-v3`으로 올렸다.
- 로컬 전체 테스트는 **174 passed, 8 subtests passed**다. 실제 Qwen 호출은 이번 변경에서 실행하지 않았으며 메타데이터 우선 경로의 실제 hit 비율·정확도·지연 개선은 아직 검증하지 않았다.
- 실제 foreground 앱·Chrome active tab 선택은 Client 구현 범위다. 현재 AI는 조정자가 공급한 `isActiveTarget`을 검증할 뿐 OS·브라우저를 직접 조회하지 않는다.
- 검토된 `templateId`가 없는 임의 목표는 여전히 `GOAL_GRAPH_UNAVAILABLE/UNCERTAIN`이다. 자동 Graph 생성은 이번 변경에 포함하지 않았다.

## 2026-10-17 메타데이터 → DOM → OCR 전환 신호 반영

- 콘텐츠 부족·충돌로 `UNCERTAIN`인 내부 결과에 `next_content_source`를 연결했다. Chrome은 `TAB_META → CHROME_DOM → CHROME_VIEWPORT_OCR`, 일반 Desktop 앱은 `APP_META → DESKTOP_APP_OCR` 순서다.
- 제외·민감·비활성 대상·stale·Graph 부재·모델 장애에서는 다음 수집을 요구하지 않는다. OCR 결과가 보류여도 추가 원문 수집 단계는 없다.
- 단계마다 새 `clientRunId`와 증가한 출처별 `sequence`를 사용해야 한다. 실제 foreground 선택·DOM/OCR 실행과 내부 신호 소비는 Client 연동이 남아 있다.
- `next_content_source`는 내부 결과이며 공개 응답 필드로 확정하지 않았다. 임의 목표 Graph 자동 생성은 이 시점에는 미구현이었고 아래 후속 변경에서 추가했다.

## 2026-10-17 확정된 임의 목표 Graph 생성 경로 구현

- 기존 GoalGraph·Concept·역할·Hybrid 계산을 유지하고 `templateId`를 선택값으로 변경했다. 검토 템플릿이 있으면 기존 경로를 사용하고, 없으면 확정 목표/GoalProfile 기반 Graph를 사용한다.
- 생성형 LLM Client Protocol, 목표 전용 prompt, concepts·unrelated_topics JSON Schema와 DIRECT 최소 1개·역할·개수·길이·중복·민감정보 검증을 구현했다. 현재 페이지 본문은 Graph 생성 함수 입력에 존재하지 않는다.
- LLM이 성공하면 기존 Graph 구조대로 관련 개념과 `role: null`인 양성 무관 주제를 함께 만든다. 관련·무관 aliases 중복은 거부한다. Client가 없거나 timeout·잘못된 JSON·Schema 오류이면 확정된 목표 범위만 가진 최소 DIRECT/SUPPORTING Graph로 내려가며, 이 fallback에서는 무관 주제를 추측하지 않아 근거 부족을 `UNCERTAIN`으로 유지한다.
- 동일 목표·버전·Profile·생성기 fingerprint의 Graph는 프로세스 TTL/LRU에서 재사용한다. 실제 제공자/API Key와 Server 영속 저장 Adapter는 아직 연결하지 않았다.
- 임의 목표 Graph 해석 변경을 구분하기 위해 분석기 버전을 `school-hybrid-v4`로 올렸다.
- 전체 로컬 검증은 **191 passed, 8 subtests passed**, `git diff --check`와 대상 Python compile 통과다. 외부 생성형 LLM·학교 GPU는 이번 변경에서 호출하지 않았다.

## 2026-10-19 임의 목표 Graph LLM·ChromaDB 연결

- OpenAI 호환 `/chat/completions` Client를 구현하고 Base URL·API Key·모델·timeout·제한 재시도를 `AI/.env`에서 주입하도록 연결했다. API Key는 Git 제외 대상이며 오류에는 Provider 본문이나 비밀 값을 포함하지 않는다.
- 생성 Graph를 목표 입력과 생성기 fingerprint의 해시 키로 로컬 ChromaDB에 저장하고 재시작 후 다시 읽는다. 이 Collection에는 확정 목표 기반 Graph만 저장하며 DOM/OCR 본문은 저장하지 않는다.
- API Key 또는 모델이 없으면 외부 호출과 Chroma 생성을 시작하지 않고 기존 최소 Graph를 사용한다. LLM·Chroma 장애도 현재 분석을 중단시키지 않고 최소 Graph로 내려간다.
- 현재 `.env`의 API Key와 모델은 비어 있어 실제 외부 LLM 생성 품질·지연은 아직 검증하지 않았다. 로컬 PersistentClient는 개발·단일 인스턴스 범위이며 Server 소유권·삭제 전파와 운영 저장소는 남아 있다.
- 로컬 Chroma Collection 생성과 Graph 저장·재조회·삭제, HTTP 요청·오류, 재시작 재사용, 일시적 LLM 실패 결과 미저장을 검증했다. 전체 결과는 **198 passed, 8 subtests passed**이며 `git diff --check`와 Python compile도 통과했다.

## 2026-10-19 메타데이터 우선 단계 실행

- AI 내부 `analyze_staged`가 메타데이터부터 분석하고, 결과가 추가 수집을 요구할 때만 Chrome DOM→viewport OCR 또는 Desktop OCR을 순서대로 호출한다. 명확한 메타데이터와 제외 화면에서는 추가 수집을 호출하지 않는다.
- 후속 관측의 출처·세션·목표·navigation·event ID·시각과 최신 문맥을 검사한다. 대상 변경이나 잘못된 단계는 `UNCERTAIN`으로 폐기하며 원문을 결과에 싣지 않는다.
- 전체 로컬 테스트는 **205 passed, 8 subtests passed**이고 `git diff --check`를 통과했다. 단계별 수집은 합성 입력 Callback으로 검증했다. 실제 foreground 선택, Chrome DOM/viewport OCR, Desktop OCR, Client·Server API 연결은 아직 구현되지 않았다.

## 2026-10-21 임베딩 요청만 사용하는 기본 Client

- Embedding Client의 verify_model 함수와 요청 전후 호출, real CLI의 초기 모델 조회를 제거했다. /health·/api/tags·/api/show·/api/version은 자동 호출하지 않는다.
- /api/embed 응답 모델명, 벡터 개수·차원·유한값·0벡터 검증, 정규화, 입력·응답 크기 제한과 timeout·제한 재시도는 유지한다. revision은 로컬 캐시 설정이며 원격 digest·정밀도 확인은 하지 않는다. 기존 평가 출력 serviceVersion은 null이다.
- 다음 로컬 회귀 명령은 112 passed였다. HTTP Mock에서 연속·배치 임베딩과 real CLI가 POST /api/embed만 호출하는 것을 확인했다.

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=AI:. GOAL_GRAPH_LLM_API_KEY= GOAL_GRAPH_LLM_MODEL= python3 -m pytest AI/tests/test_embedding_client.py AI/tests/test_relevance_eval.py AI/tests/test_relevance_pipeline.py -q -p no:cacheprovider
```

- 기본 OllamaEmbeddingClient와 기존 RelevanceAnalyzer로 실제 학교 HTTPS API의 dev-state-direct를 실행했다. RELATED / GOAL_CONNECTED, 임베딩 요청 2회(개념 질의·정제 콘텐츠), 전체 경과시간 10.499초였다. 요청 경로를 확인했으며 두 요청 모두 POST /api/embed였다. 임시 임베딩 Adapter를 사용하지 않았다.
- 실제 판정 1건은 연결 확인이며, 임계값 보정·독립 정답 세트 정확도·GPU 내부 처리시간·공개 API 연동의 검증을 뜻하지 않는다.

## 2026-10-21 DOM·OCR 예제 3건 Mac 실호출

- 공통 목표는 React useState로 객체 상태 변경하기이며 demo-goal.json과 demo-chrome-dom.json, demo-chrome-ocr.json, demo-desktop-ocr.json을 사용했다. 기존 기본 Client와 분석기를 사용했고 테스트용 VerifiedContext를 주입했다.

| 입력 | 실행 상태 | 라벨 | 이유 | 경과시간 | 임베딩 요청 |
| --- | --- | --- | --- | --- | --- |
| Chrome DOM | SUCCEEDED | UNCERTAIN | INSUFFICIENT_EVIDENCE | 6.9315초 | 2 |
| Chrome OCR | SUCCEEDED | RELATED | GOAL_CONNECTED | 0.6885초 | 1 |
| Desktop OCR | SUCCEEDED | RELATED | GOAL_CONNECTED | 3.0478초 | 1 |

- 전체 10.670초이며 실제 요청 4회는 모두 POST /api/embed였다. 모델 정보 조회는 발생하지 않았다. 첫 입력의 목표 개념 벡터를 다음 입력들이 재사용하므로 요청 수와 시간 비교 시 이를 구분한다. 순수 네트워크·GPU 시간은 별도 측정하지 않았다.
- DOM 결과는 모델/전송 오류가 아닌 Hybrid 근거 부족이다. 이번 실행은 임계값이나 데이터를 바꿔 결과를 맞추지 않았다. 입력 텍스트와 OCR 인식 품질은 합성 예제이며 실제 사용자 화면의 정확도 측정이 아니다.

## 2026-10-21 현재 기능용 Streamlit 비교 화면

- 변경: `similarity_streamlit.py`, `requirements-streamlit.txt`, 신규 로컬 harness `streamlit_runner.py`, runner/UI 회귀 테스트, AI 실행·현재 상태 문서. 제품 FE나 공개 계약은 변경하지 않았다.
- 6번은 현재 RelevanceAnalyzer school-hybrid-v6를 호출한다. 받은 벡터를 해시로 잠시 조회해 동일 판정 함수로 개념별 점수를 재구성하며 원래 라벨/사유와 일치하는지 검증한다. 추가 임베딩이나 metadata 조회는 없다. 원문/결과는 자동 저장하지 않는다.
- 1~5·7은 기존 후보 기준선을 사용한다. 4번은 Graph 개념 description별로 Qwen에 요청하고, 나머지 확장 기준선은 별칭을 사용한다. 선수·보조를 SUPPORTING으로 묶는 제한을 화면에 명시한다. 7번은 선택적 CrossEncoder 준비가 필요하며 없으면 NOT_RUN이다. 3·4·7은 보정된 라벨 기준이 없어 UNCERTAIN을 유지한다.
- 화면: 확정 목표·Graph 준비 방식·출처·제목·텍스트/JSON·OCR 수동 가정·방법 선택·비교 버튼. 결과는 라벨·원점수 종류·reasonCode·근거 텍스트/ID·다음 요청 출처·개념별 점수·시간·기대 일치율을 표시한다. 파일 입력의 기대 라벨은 계산에 넣지 않으며 오류/미실행을 정답으로 세지 않는다.
- 정적/내부/UI 검증: 전체 **250 passed, 8 subtests passed**. Streamlit AppTest로 버튼 전 무호출, 예제 채우기, 결과 표시, 입력 변경 후 기존 결과 숨김, 잘못된 JSON 차단을 확인했다. 임시 설치 Streamlit 1.66.0이며 로컬 HTTP health 응답 ok를 확인했다.
- 실제 학교 API 검증: 작성한 공부·요리 본문 2건을 같은 비교 runner로 실행했다. 6번은 공부 RELATED/GOAL_CONNECTED(최대 H 0.8632), 요리 UNCERTAIN/INSUFFICIENT_EVIDENCE(H 0.6226)이었다. 기대 UNRELATED와 달랐으며 UI가 이를 성공한 무관 판정으로 바꾸지 않는다. 당시 1~6 비교 전체 11.577초·POST /api/embed 7회였다. 이 최초 실행의 4번은 별칭 질의를 사용했고 이후 description당 1개 질의로 정리했으므로 해당 전체 시간은 최종 4번 성능 수치로 사용하지 않는다. 별도 브라우저 UI에서 현재 6번의 제목+설명+코드 공부 예제를 실행해 SUCCEEDED/RELATED, 근거 3개, H 0.8751, 전체 2.385초·임베딩 2회와 결과 상세 표시를 확인했다. 이는 합성 단건이며 정확도 보증이 아니다.
- 남은 범위: 독립 정답 평가와 Graph 별칭/무관 근거 품질, 재정렬 모델 실측, 실제 수집·Server 연동. 입력한 기대 일치율은 제품 정확도나 정답 확률이 아니다.

실행 명령은 [AI README](../../README.md)의 Streamlit 절을 따른다.

### 같은 날 사용자 정정: 채택한 Hybrid만 실행

- 기존 후보 1~5·7 선택과 BGE 준비 UI를 제거하고 6번 Graph + 키워드 + 벡터 Hybrid로 고정했다. 현재 분석 코드는 변경하지 않았다.
- DIRECT/PREREQUISITE/SUPPORTING 역할, 키워드 0.35 + 벡터 0.65, 기존 역할별 기준·커버리지·충돌 처리와 출력은 유지한다. 목표 Graph는 자동 생성이 기본이며 예제는 React 템플릿을 사용한다.
- UI 회귀 테스트에 단일 Hybrid 호출 및 후보 선택 미노출을 추가했다. 이전 절의 후보 비교는 이 변경 전 기록이다.

## 2026-10-21 Streamlit 반복 분석 지연 수정

### 확인한 원인

현재 사용자 화면에서 JWT 자동 생성 목표의 전체 시간은 19.799초, Graph 준비 12.907초, 이후 분석 6.8905초·임베딩 요청 2회였다. 모델 순수 처리와 네트워크 시간은 이 과거 결과에 분리되어 있지 않다. 코드에서는 버튼마다 새 HTTP Client·RelevanceAnalyzer를 생성하여 기존 목표 벡터·결과 캐시를 버렸다. 이것은 반복 분석에 불필요한 요청을 추가하지만 과거 6.8905초 전체의 원인이라고 단정할 수는 없다.

### 변경

- 브라우저 세션별 HybridSession으로 HTTP 연결·기존 분석기·300초 캐시를 재사용한다. 연결 설정이 바뀌면 이전 Client를 닫고 캐시를 새로 만든다. 사용자가 정한 Graph·점수·판정 규칙은 바꾸지 않는다.
- 목표만 같고 본문이 바뀌면 개념 벡터를 재사용하고 본문 벡터만 요청한다. 동일 화면은 기존 판정 캐시를 사용하며, 결과에 cacheHit를 표시한다.
- 세션에 남는 진단 벡터는 원문 없는 SHA-256 키·벡터만 512건/300초로 제한한다. 진단 벡터만 만료되어도 점수 화면을 위해 모델을 다시 호출하지 않는다. 실제 민감/제외 판정 시 진단 벡터도 지운다.
- UI가 채우는 누락 clientRunId는 매 제출마다 새 값으로 생성해 observedAt 변경으로 인한 멱등성 충돌을 방지한다. 사용자가 제공한 ID는 그대로 두고 중복·변경 충돌 검증을 유지한다.
- Graph 준비, 임베딩 API 호출·벡터 검증, 나머지 로컬 계산 시간을 구분한다. 임베딩 API 시간은 모델 처리·학교 대기·네트워크를 포함하며 GPU 순수 시간으로 표현하지 않는다.

### 검증

전체 254 passed, 8 subtests passed. 같은 입력 무호출, 변경 본문 1회, 목표 변경 2회, 명시적 event ID 충돌 유지, 진단 벡터 만료 후 무호출을 확인했다.

실제 학교 API에서 사용자와 동일한 JWT 자동 생성 Graph를 저장소에서 재사용하여 실행했다. 새 Graph 생성 비용은 포함되지 않는다.

| 실행 | 전체 | 임베딩 API/검증 | 로컬 | 실제 임베딩 요청 | 판정 | 판정 캐시 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| 첫 본문 분석 | 2.57587초 | 2.42596초 | 0.14713초 | 2회 | UNCERTAIN | false |
| 동일 본문 재실행 | 0.04944초 | 0초 | 0.04940초 | 0회 | UNCERTAIN | true |
| 같은 목표·본문 추가 | 1.00046초 | 0.85619초 | 0.14423초 | 1회 | UNCERTAIN | false |

Graph 조회는 첫 실행 2.78ms, 이후 0.04~0.05ms다. 별도 jwt-auth 템플릿에서도 같은 요청 수 패턴을 확인했지만 Graph가 다르므로 위 표와 혼합하지 않는다. 동일 본문 재실행은 새로운 추론이 아니라 캐시 결과다. 한 번씩 측정한 값이며 모델 예열·네트워크 상태가 달라 과거 6.8905초 대비 순수 코드 개선 배율이나 운영 P95로 해석하지 않는다. 생성 Graph의 근거 부족으로 UNCERTAIN인 품질 문제는 유지하며 속도 개선을 위해 라벨·임계값을 바꾸지 않았다.

UI 확인: 실제 브라우저에서도 같은 자동 생성 JWT Graph·입력을 유지해 최초 4.823초(API/검증 4.667초, 로컬 0.148초, 2회 요청), 동일 입력 재실행 0.054초(0회 요청)를 확인했다. CLI 최초 2.576초와 차이가 있으므로 원격 API 지연의 변동은 남아 있다. 판정은 둘 다 UNCERTAIN으로 유지됐다. 수정 반영 중 기존 UI 프로세스가 exit 139로 종료되어 파일 감시를 끄고 같은 localhost 주소로 재실행했으며, 위 UI 검증은 재실행 후 결과다. 종료 원인은 확정하지 않았고 속도 문제의 원인으로 단정하지 않는다.

## 2026-10-21 사용자 요청 1번: 목표 기반 별칭 생성 지침만 보강

### 범위

`goal_prompt.py`에 짧은 정식 용어·자연스러운 도메인 명사/동작 표현·검증/실패 처리 표현·전역 별칭 소유 개념 점검을 추가했다. 별도 검증 LLM, 매칭 정규화, 중복 적중 산식, 점수/커버리지/공개 JSON 계약은 변경하지 않았다. 테스트 목표는 `JWT 인증 구현`, goalProfile은 null이므로 생성에 사용하지 않았다. 화면 본문과 기대 라벨도 생성 입력에 넣지 않았다.

### 생성과 검증 이력

- v3 최초 생성 1회, 16.283초: `GOAL_ALIASES_OVERLAP`으로 기존 검증에 실패해 최소 Graph를 사용했다. 이 실행을 성공한 생성으로 간주하지 않는다. `AI/evaluation/jwt-alias-v3-attempt1.json`에 실패 실행과 평가를 보존했다.
- v3.1: 별칭 하나의 소유 개념을 하나로 제한하고 전역 중복 제거를 명확히 한 뒤 새 버전으로 다시 생성했다. HTTP 생성 요청 1회, 13.363초, 관련 개념 8개·무관 주제 3개. 추가 LLM 검증 요청은 없다.
- 같은 Prompt fingerprint 체계가 기존 v2/v3 Graph를 새 결과로 재사용하지 않게 한다. 단위/회귀 검증은 **255 passed, 8 subtests passed**다. 생성 입력과 기대 라벨 분리·기존 Prompt 변경 시 캐시 분리 테스트를 포함한다.

### 실제 학교 Qwen API 재테스트

실행 전에 기대 라벨을 고정한 개발용 합성 4건으로 이전 저장 Graph와 새 Graph를 비교했다. 이전/새 Graph 각각 임베딩 요청 5회이며 새 Graph를 생성할 때 이 본문들은 전송하지 않았다. 원래 JWT 사례는 개선 방향을 정하는 데 사용했으므로 독립 평가가 아니다.

| 사례 | 기대 | 이전 결과 | 새 결과 | 관련 커버리지 이전→새 |
| --- | --- | --- | --- | --- |
| 기존 JWT 인증 구현 | RELATED | UNCERTAIN | UNCERTAIN | 49.6% → 49.6% |
| JWT 다른 표현 | RELATED | UNCERTAIN | UNCERTAIN | 0% → 51.7% |
| 요리 본문 | UNRELATED | UNCERTAIN | UNCERTAIN | 0% → 0% |
| 로딩/정보 부족 | UNCERTAIN | UNCERTAIN | UNCERTAIN | 0% → 0% |

최종 라벨 기대 일치는 이전/새 결과 모두 1/4이다. 무관 검출 품질도 남아 있으며 정확도 개선이 입증된 것은 아니다.

기존 JWT 두 번째 본문에서 token-validation의 `토큰 검증` 별칭이 새로 적중했다. K=0.5, cosine=0.56825085, H=0.54436305로 DIRECT 기준 0.62에는 미달했다. authentication-failure-handling은 cosine=0.70860462지만 K=0, H=0.46059300으로 탈락했다. 첫 본문만 126/254자 인정되어 최종 UNCERTAIN이다. 따라서 생성 지침만 보강해 일부 별칭 누락은 줄었지만 모든 의미 차이를 해결하지는 못했다. 숫자 기준을 이번 사례에 맞춰 바꾸지 않았다.

새 Graph 기준 기존 JWT 분석 2.991초·2회 요청, 다른 표현 0.630초·1회, 요리 0.484초·1회, 로딩 0.454초·1회였다. Graph 생성과 API 지연을 구분하며 단건 수치를 운영 성능 보증으로 사용하지 않는다. 자세한 Graph·문단별 점수·고정 사례 해시는 `AI/evaluation/jwt-alias-v3-smoke.json`을 따른다.

### 남은 작업

현재 요청 범위인 Prompt 보강과 재테스트는 완료했다. 향후 별칭 의미/범위 검증과 실제 표현 변형 매칭을 별도 검토하고 독립 정답 세트로 평가해야 한다. 그 변경은 이번 실험에 포함하지 않았다.
