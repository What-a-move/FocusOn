# 입력 검증·전처리 및 Hybrid 관련성 구현 결과

> 최신 로컬 구현과 2026-10-14 학교 Qwen 연결 확인은 아래 날짜별 기록을 따른다. 공개 API·Server 연결 및 독립 정확도 평가는 미완료다.

## 기본 정보

- 기능명: 입력 검증·전처리 및 학습 목표·콘텐츠 관련성 판단
- 기능 ID: relevance-analysis
- 작성자: 이재빈 작업 공간 / Codex 검증
- 작성일: 2026-10-07
- 관련 기획서: [통합 PLAN](learning-goal-content-relevance-evaluation-PLAN.md)
- 관련 Issue: [#11](https://github.com/What-a-move/FocusOn/issues/11)
- 관련 Branch: `feat/11-ai-preprocessing-relevance`
- 상태: 학교 내부 전처리·Graph·Hybrid·Ollama Client·평가 CLI 구현. 실제 Qwen 벡터 반환과 임시 Adapter 기반 합성 실행 확인. 공개 API·표준 Client 계약 연결·GPU 자원 및 독립 정확도 검증 미완료.

## 구현 결과

- DOM/OCR 내부 입력 모델과 문단/코드/표 순서를 보존하는 정제·청크·해시 모듈을 게시한다.
- 개인정보·추출 실패·품질 부족·중복·일부 run/목표/navigation stale 검증을 수행한다.
- 기존 GoalProfile과 합성 본문으로 후보 1~7을 비교하는 Streamlit 실험 코드를 보존한다. 현재 MVP 채택은 Hybrid 6번이며 실험 화면을 운영 판정기로 사용하지 않는다.
- 합의된 공개 API·현재 실행 문맥 전체 연결·학교 FastAPI와 표준 Embedding Client 계약 통합은 아직 완료가 아니다.

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
