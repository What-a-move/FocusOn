# FocusOn AI

FocusOn AI는 사용자의 학습 목표와 현재 화면·페이지 정보를 비교해 활동 관련성을 판단하고, 이후 학습 흐름과 사용자 상태를 보조 분석하는 Python 서비스다.

## 로컬 Streamlit 관련성 테스트 (2026-10-22)

저장소 루트에서 실행한다.

```bash
python3 -m pip install -r AI/evaluation/requirements-streamlit.txt
python3 -m streamlit run AI/evaluation/similarity_streamlit.py --server.address 127.0.0.1 --server.port 8501
```

브라우저에서 `http://127.0.0.1:8501`을 연다. 확정 목표와 DOM/OCR 텍스트 또는 기존 추출 JSON을 입력하고 **비교하기**를 누른다. 현재 화면은 **Graph 없는 키워드 + 벡터 Hybrid 실험**이다. 기존 운영 후보 `RelevanceAnalyzer`의 Graph 방식과 분리한 테스트 경로이며, Notion 계약을 변경하거나 정확도를 검증한 것이 아니다.

- 키워드 전용 형태소 처리는 로컬 Kiwi 0.23.1로 목표·본문을 같은 방식으로 분석한다. 명사·고유명사·보호한 영문 기술 식별자를 남기고 조사·어미·학습 지시 불용어를 제외해 토큰 단위로 매칭한다. `조건에/조건이/조건을`은 `조건`으로 비교하며 `C++`·`C#`·`Node.js`·`useState`는 보존한다. 임베딩에는 기존 정제 문장을 그대로 보낸다. 별칭·선수/보조 개념을 생성하지 않으며 GoalProfile·템플릿·화면 본문·기대 라벨로 목표 키워드를 확장하지 않는다. Graph 생성·OpenAI·ChromaDB 호출이 없다. 분석기만 프로세스에서 재사용하고 원문·키워드 목록은 영속/전역 캐시하지 않는다.
- 현재 `kiwi-core-phrases-v2`는 위 로컬 분석에서 `선택·비교·정리·확인·차이·상황`과 명시한 일부 영어 일반 표현을 핵심 용어에서 분리한다. 전공 용어 사전이나 LLM 의미 분류가 아닌 제한된 규칙이다. 목표에서 일반 표현과 핵심 용어가 인접한 순서쌍(`프로토콜 선택`, `선택 정렬`)을 구문으로 보존하고, 본문에서는 조사만 허용해 연결하며 문장·줄·동사 경계를 넘지 않는다. 핵심 용어·일반 표현·목표 구문과 문단별 단독 일반 적중을 화면에 구분한다. 구문은 문맥 진단용이며 핵심 용어 점수에 보너스로 중복 가산하지 않는다.
- 현재 버전은 `body-context-hybrid-v3` / `kiwi-weighted-terms-v3`다. `K = 발견한 목표 용어 가중치 합 / 전체 목표 용어 가중치 합`, `H = max(0,V) + 0.20×K×(1−max(0,V))`를 사용한다. 상태·객체·값·결과 등 명시한 넓은 용어는0.25, 나머지는1.0이며 작은 규칙 목록이다. 키워드 미적중으로 V를 깎지 않고, 어떤 단어도 무관 판정을 단독으로 막지 않는다.
- 실제 제공된 pageTitle(없으면 windowTitle)·Markdown h1~h3 경로와 같은 본문을 문맥 단위로 함께 비교한다. 원래 본문 V가 최소 기준을 넘어야 문맥 H를 관련 근거로 사용할 수 있다. 제목만으로 판정하지 않고, 문맥 용어를 목표 키워드/본문 K에 추가하지 않는다. 본문·문맥·제목·통과 근거 점수를 분리해 표시한다.
- 개발용 React/TCPUDP24건으로72개 후보 중 고른 현재 실험 설정은 **본문 V≥0.30, 본문/문맥 H≥0.35**, 무관 본문 V≤0.25다. 관련 커버리지는 참고값이며 무관 커버리지≥0.70·본문80자·완전 추출과 충돌 보류는 유지한다. `graphless-calibrated-config.json`은 버전/모델/차원/revision/지침과 연결하며 사이트가 실행 중 자동으로 다시 학습하거나 숫자를 바꾸지 않는다.
- Streamlit에 한해 메뉴 제거 수로 품질을 낮추는 산식을 비활성화하고 이미 추출한 DOM/OCR의 JSX·제네릭·entity를 보존한다. 반복 소제목은 문맥 경계로 보존한다. 빈 본문·저품질·잘림·민감정보·권한·stale·모델 실패 검사는 유지하고 운영 전처리 기본값은 변경하지 않는다.
- 커버리지는 원래 본문의 근거 글자 수/전체 분석 본문 글자 수로 표시하며 제목·소제목을 제외하고 문맥/설명+코드 중복을 세지 않는다. 메타데이터만 있으면 본문 요청을 표시할 수 있으나 실제 수집은 하지 않는다.
- 실제 Qwen으로 기존 React24 기대 일치는4/24→23/24였다. 별도 목표 합성24건은 보정 전18/24→보정 후20/24, Macro-F1 0.771→0.811이나 무관6건 중1건·정보부족6건 중3건을 RELATED로 오판했다. 합성·미검수 결과이며 운영 정확도가 아니다. [평가 리포트](docs/features/graphless-hybrid-calibration-REPORT.md)와 [혼합48건](evaluation/graphless-calibration-cases.json)을 참고한다.
- 보정 재실행: `PYTHONPATH=AI:. python3 -m AI.evaluation.calibrate_graphless --output-dir /private/tmp --config-out AI/evaluation/graphless-calibrated-config.json`. development 목표만 기준 선택에 사용하고 evaluation 목표는 고정 기준으로 확인한다. 평가 결과를 후속 튜닝에 쓰면 새로운 평가셋이 필요하다. 기대 라벨·실패 상태를 점수 계산과 분리한다.
- API 주소는 화면 기본값 또는 `FOCUSON_EMBEDDING_URL`과 `FOCUSON_EMBEDDING_REVISION`을 사용한다. 기존 Qwen Client의 `/api/embed`만 호출한다. 같은 브라우저의 HTTP 연결과 해시 키 기반 벡터 512개/300초를 재사용하고, 같은 고정 기준으로 캐시 벡터의 점수·커버리지·라벨을 다시 계산한다. API 설정 변경·민감/제외 입력·모델 실패 시 캐시를 비운다.
- 기존 단일 추출 JSON·배열·`cases` 최대 30개를 지원한다. 사례당 현재 화면 observation 1개를 받으며 제공된 ID는 덮어쓰지 않는다. `cases`와 함께 선언된 `goal.text`가 목표 칸과 다르면 모델 호출 전에 차단한다(공백·영문 대소문자 차이는 허용). OCR JSON에는 `ocr.confidence/captureStatus`가 필요하다. 수동 OCR confidence는 사용자 가정이다.
- 화면에 추출 키워드·문단별 K/V/H·관련/무관 후보·커버리지 분자/분모·라벨·사유·근거·API/로컬 시간·기대 일치율을 표시한다. OpenAI는 현재 경로에서 미호출이므로 `0회·0.000초`로 명시하며 임베딩 API 시간과 구분한다. Kiwi 최초 로딩은 로컬 시간에 포함된다. `expectedLabel`은 모델 입력과 판정에서 분리하고, 실패/분석 불가 UNCERTAIN을 정답으로 세지 않는다. 유사도 점수와 합성 기대 일치율을 정답 확률·독립 정확도로 해석하지 않는다.
- 화면 원문·결과는 파일·DB에 자동 저장하지 않는다. 실제 DOM/OCR 수집·Server 인증·실시간 최신 문맥 재조회·이벤트 멱등 저장·알림은 이 실험 도구의 범위가 아니다. 운영 판정기는 이번 작업에서 교체하지 않았다.

## 담당 기능

### MVP

- 자연어 학습 목표의 명확성 검사와 사용자 확인·추천·질문 보조
- Chrome Extension DOM·내부 로컬 OCR과 macOS Desktop Apple Vision OCR에서 전달된 정제 텍스트의 품질 확인
- 목표와 현재 콘텐츠의 관련성 판단
- 최근 7일 최소 집계와 규칙 기반 개인화 휴식 제안 후보 검토
- 규칙 기반 판단, 임베딩 유사도, 필요한 경우 LLM을 사용하는 단계적 분석
- 추출·분석·관련성·이탈 흐름·권장 행동을 분리한 상태와 근거 반환

### 후속 기능 또는 출시 결정 필요

- 페이지 이동과 체류 시간을 이용한 학습 흐름·목표 이탈 분석
- 클라이언트 MediaPipe가 계산한 얼굴·시선·자세 상태값의 보조 분석
- YouTube 자막, PDF 추출 텍스트 등 외부 콘텐츠 분석
- PDF·Canvas·이미지·슬라이드 대상 ColPali 비교 실험

카메라 출시 여부는 현재 기획서 안에서 MVP 포함과 출시 미정이 함께 기록되어 있어 팀 결정이 필요하다. AI는 어떤 경우에도 원본 영상·프레임·랜드마크를 받지 않고, 출시되더라도 클라이언트가 만든 최소 상태·집계값만 선택적으로 받는다.

관련성, 학습 흐름, 사용자 상태는 하나의 판단으로 섞지 않고 별도 분석 모듈과 API로 분리한다. `SUPPORTING`은 AI 내부의 보조 학습 라벨이며 제품 표시에서는 `RELATED`의 보조 유형으로 매핑한다. `OFF_TASK`는 제품의 `UNRELATED`, `UNAVAILABLE`은 제품의 `UNCERTAIN`과 상세 처리 실패 상태로 매핑한다.

## 분석 흐름

```text
Client별 제외·권한 검사
  → Chrome: DOM 우선, 부족할 때 Extension 내부 로컬 OCR
  → macOS Desktop: 허용된 활성 앱 화면의 Apple Vision 로컬 OCR
  → 개인정보·품질 검사
  → Spring Server의 인증·소유권·버전 검증
  → AI 입력 검증·텍스트 정제
  → 규칙 기반 판단
  → 임베딩 유사도 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 정밀 판단
  → 관련성·흐름·행동 상태 분리
  → Server가 결과 저장
  → Extension·웹 대시보드가 상태·알림·기록 표시
```

## 현재 상태

- Issue #11의 DOM·OCR 입력 검증·전처리 순수 모듈과 단위 테스트를 구현했다.
- 관련성 API, 분석 기준, Prompt 계약은 문서 초안까지 작성했다.
- 목표 보조, 세션 설정, 두 분석 제외 모드와 개인화 휴식의 입력 계약은 기획 반영 초안까지 작성했다.
- 학교 내부 입력 Pydantic Schema, 전처리→Goal Graph→Hybrid 판정, Qwen3 Ollama HTTP Client, 메모리 TTL/LRU 캐시와 합성 평가 CLI를 구현했다.
- 학교 Qwen API의 실제 벡터 반환과 임시 Adapter 기반 합성 Hybrid 실행을 확인했다. 표준 Client 계약 연결, 공개 FastAPI·Server 연동, GPU 자원 및 독립 정답 정확도 평가는 후속 작업이다.
- 최신 dev의 MVP 이탈·카메라 정책과 API 문서 동기화는 계약 문서 반영이며 관련 분석 기능의 구현 완료나 팀 최종 승인을 뜻하지 않는다.
- 원본 화면과 원본 카메라 영상은 AI 입력·저장 대상으로 사용하지 않는다.
- ColPali는 정식 기본 경로가 아니라 시각 구조 콘텐츠의 실험 경로다.

저장소 루트에서 다음 명령으로 실행한다. Python 3.11.6 기준이며 기존 가상환경에 `AI/requirements.txt` 의존성이 필요하다.

```bash
PYTHONPATH=AI:. python3 -m pytest AI/tests -q
python3 -m AI.evaluation.relevance_eval --mode fake --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

`AI/scripts/langsmith.py`는 수동 외부 API 연결 확인용이며 pytest 회귀 테스트에는 포함하지 않는다. 실행하면 `gpt-4o-mini` API 호출이 발생한다. 이 스크립트는 LangSmith 추적 SDK를 설정하지 않고 LangChain 모델 호출만 확인한다. fake 평가는 합성 벡터의 로직 검사이며 Qwen 정확도나 GPU 성능을 측정하지 않는다. 실제 학교 연결은 [Ollama 설치·실행 지침](docs/features/school-ollama-embedding-SETUP.md)을 따른다.

## 문서

- [AI 문서 안내](docs/README.md)
- [현재 컨텍스트](docs/CONTEXT.md)
- [다음 작업](docs/NEXT_TASK.md)
- [결정 기록](docs/DECISION_RECORD.md)
- [AI 개발 규칙 초안](docs/DEVELOPMENT_RULES.md)
- [목표·세션·회차 모델](docs/goal_session_spec.md)
- [콘텐츠 수집·추출 계약](docs/content_acquisition_spec.md)
- [상태 모델](docs/state_model.md)
- [API 명세](docs/api_spec.md)
- [분석 기준](docs/analysis_rules.md)
- [FocusSessionAgent 실행 계약](docs/focus_session_agent_spec.md)
- [사용자 피드백·개인화 명세](docs/feedback_personalization_spec.md)
- [AI 학습 노트 생성 명세](docs/session_note_spec.md)
- [데이터 수명](docs/data_lifecycle.md)
- [프롬프트 가이드](docs/prompt_guide.md)
- [평가 명세](docs/evaluation_spec.md)
