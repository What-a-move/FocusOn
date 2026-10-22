# AI 작업 컨텍스트

- 2026-10-23 커밋 준비: 중복 `evaluation/AI 2/`를 ZIP 동일성 확인 후 로컬 삭제했다. `.env`·Chroma·LangSmith 수동 스크립트·중복 추출 폴더·전달용 ZIP은 업로드 대상에서 제외한다. AI 전체 회귀는360 passed, 16 subtests passed이며 실제 화면·공개 API 통합 완료를 의미하지 않는다.

- 2026-10-23 Streamlit 메타데이터 전용 title-hybrid-v1 추가. 제목 V0.55/H0.60·무관V0.25(미보정), 본문 길이·커버리지 미적용. 개발 주제 합성240건 실제 Qwen191/240(79.6%), 관련→보류49건, 실패0. 무관 정답이 없어 무관 성능은 검증하지 않았다. [보고서](features/graphless-hybrid-calibration-REPORT.md).

> AI 담당자가 작업 시작 시 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 2026-10-22 Streamlit 입력 문맥·키워드·전처리·기준 보정

- 사용자 승인1~4번을 `body-context-hybrid-v3`에 반영했다. 이미 추출된 텍스트의 JSX/제네릭 보존, 메뉴 제거로 정상 본문 품질을 낮추지 않기, 반복 소제목 구조 보존은 Streamlit opt-in이며 운영 기본값/공개 JSON은 유지한다.
- 넓은 목표 용어는0.25·기타1.0으로 보강하고 키워드의 무관 veto를 제거했다. 실제 pageTitle/windowTitle·명시 소제목+본문의 문맥은 원래 본문 최소 V를 통과해야 관련 근거로 사용한다. 본문/문맥/메타데이터/통과 근거 점수를 분리한다.
- 실제 Qwen48건에서 개발용 React/TCPUDP24건으로만72개 후보를 선택했다. 선택값 V0.30/H0.35·무관V0.25를 다른 목표 QueryDSL/고유값24건에 고정 적용했다. 별도 평가18/24→20/24, Macro-F1 0.771→0.811, 관련 재현율58.3%→100%. 다만 무관6건 중1건 및 정보부족6건 중3건을 RELATED로 오판했고 RELATED precision75%다.
- 기존 React24 동일 입력·기대값 회귀는4/24→23/24(95.8%)였다. 065m의 H0.3246/문맥H0.3292는 미달이다. 평가 결과에 맞춰 재조정하지 않았다. 실제 화면·독립 검수 정확도가 아닌 합성 결과다. [리포트](features/graphless-hybrid-calibration-REPORT.md) 참조.
- 관련 로컬 회귀157 passed, 16 subtests passed. 설정 파일과 원문/벡터 없는 결과 JSON을 평가 폴더에 저장한다. 키/환경 파일은 변경하지 않으며 보정은 별도 CLI의 명시 실행만 허용한다. 아래 v2·Graph 이력과 구분한다.

## 2026-10-22 Streamlit 관련 커버리지 필수 조건 제거

- 사용자 승인으로 `vector-base-keyword-boost-v2`에 관련 커버리지 60% 필수 조건을 제거했다. V≥0.55·H≥0.60인 본문 근거가 있고 무관 근거와 충돌하지 않으면 `RELATED_EVIDENCE_FOUND`다. 커버리지·분자·분모 표시는 유지한다.
- Streamlit 경로에 한해 전처리 min_text_chars=0을 사용하고 별도 30자 차단을 제거했다. 짧은 코드·오류 문구도 임베딩/근거로 판단하며 빈 본문·메뉴뿐·문장부호뿐·근거 부족·낮은 OCR 품질·추출/모델 실패는 계속 보류한다. 기존 운영 전처리 기본값은 변경하지 않았다.
- 모델 없는 회귀·Streamlit UI 테스트 79 passed. 무관 커버리지70%·80자·완전 추출, 관련/무관 충돌 시 보류는 유지한다. 관련 근거 한 개로 통과할 수 있는 실험이므로 주요 본문 대표성·무관 자료 오탐 검증이 남아 있다. 실제 Qwen 평가·정확도 개선은 이번 작업에서 측정하지 않았다. 아래 이전 버전의 임계값·평가 기록과 구분한다.

## 담당 영역

- 학습 목표와 화면·페이지 정보의 관련성 판단
- OCR·페이지 텍스트 정제와 입력 품질 판단
- 페이지 이동·체류 시간을 이용한 목표 이탈 및 학습 흐름 분석
- MediaPipe가 클라이언트에서 계산한 상태값의 보조 분석
- 분석 결과의 상태값·점수·신뢰도 반환
- AI API 입력·출력 계약 관리
- 평가 데이터와 모델·프롬프트 품질 확인

## 현재 상태

### 완료된 작업

- 2026-10-22 사용자 승인으로 Streamlit 실험의 결합식을 `vector-base-keyword-boost-v1`로 변경했다. S=max(0,V), H=S+0.20×K×(1−S)이며 키워드 미적중으로 벡터 점수를 깎지 않는다. 기존 keyword/vector 가중치 대신 keyword_boost=0.20을 표시한다. 관련 H≥0.60·V≥0.55, 커버리지·품질·충돌 검사는 유지하고 무관 후보는 핵심/구문 미적중·V≤0.25로 분리해 이전 범위를 유지한다. 문단 표에는 vectorBase/keywordBoost를 추가했다. 관련 모델 없는 테스트 72 passed. 실제 Qwen 재평가·새 정확도 검증은 하지 않았다. 이전 36건/React 24건 결과는 과거 식의 결과이며 사용자 평가용 데이터와 기대 라벨은 변경하지 않았다.

- 2026-10-22 사용자 요청으로 저장소 루트 `FocusOn더미데이터/`에 분야별 2시간 합성 학습 JSON 10개를 작성했다. React·TCP/UDP·로드 밸런싱·QueryDSL·트랜스포머 디코더·선형대수·통계·물리·역사·경제의 목표별 24화면(5분 구간, 0~120분), 총 240건이며 기대 라벨은 모두 RELATED다. CHROME_DOM·Chrome OCR·Desktop OCR 입력을 섞고 OCR confidence는 가정값으로 명시했다. 사용자가 검증을 직접 수행한다고 지정하여 파싱/전처리 테스트·모델 호출·정확도 검증을 실행하지 않았다. 실제 수집·실제 2시간 관측 자료가 아니다.

- 2026-10-22 Streamlit 실험에 `kiwi-core-phrases-v2`를 적용했다. 명시한 일반 표현은 핵심 용어 K에서 제외하고 단독 적중으로 무관 후보를 차단하지 않는다. 목표의 일반 표현+핵심 용어 순서쌍은 구문으로 보존해 화면에 표시하되 점수를 중복 가산하지 않는다. 일반 표현만 있는 목표는 UNCERTAIN으로 보류한다. 임베딩 원문·공개 계약·고정 수치는 유지한다. 관련 테스트 55 passed. 실제 Qwen으로 기존 합성 36건을 실행하고 같은 캐시 벡터로 v1 전체 명사 규칙을 재현해 비교했다: TCP/UDP 8→9/12, 로드 밸런싱 8→8/12, QueryDSL 9→9/12, 총 25→26/36. 라벨 변경은 tcpudp-35m의 UNCERTAIN→UNRELATED 1건이며 본문 K=0·V=0.2357·H=0.1886·무관 커버리지 100%다. LLM·지식 DB 호출은 없고 독립 정확도·실제 화면 검증은 아니다.

- 2026-10-22 Streamlit 실험의 목표·본문 키워드 추출/매칭을 로컬 Kiwi 0.23.1 `kiwi-nouns-tech-v1`로 변경했다. 조사·어미·불용어를 제외하고 기술 식별자를 보호해 같은 토큰끼리 비교한다. 입력 JSON·임베딩 문장·고정 가중치/임계값은 유지한다. OpenAI는 미호출 0회/0초로 표시하고 Qwen 시간·로컬 시간을 분리한다. 관련 테스트 48 passed. 동일 합성 Desktop QueryDSL 화면을 실제 Qwen 임베딩 1회로 재검증한 결과 두 번째 문단 H 0.5956→0.6313, 관련 커버리지 12%→70.22%, UNCERTAIN→RELATED였다. 전체 6.094초(임베딩 왕복/검증 5.211초, 로컬 0.883초·분석기 최초 초기화 포함), OpenAI 0회. 실제 화면 추출·독립 정확도·P95 검증은 아니다.

- 2026-10-22 별칭 생성 지침을 `goal-graph-v4.1`로 변경했다. 동일 개념의 정식 명칭·한영 동의어·표준 약어·표기 변형만 우선하고 조사·일반어·관련 도구를 별칭으로 섞지 않는다. 확정 목표와 개념 정의만 사용하며 실제 본문·기대 라벨은 전달하지 않는다. 기존 fingerprint로 캐시를 분리한다. 현재 Graph 없는 Streamlit은 이 프롬프트를 호출하지 않으므로 이번 수정으로 키워드 추출·점수·임계값은 바뀌지 않는다. 새 프롬프트의 실제 생성 품질·속도는 미검증이다.

- 2026-10-22 사용자 요청으로 Streamlit의 현재 GraphlessConfig 값을 고정하고 가중치/임계값 조정 컨트롤을 읽기 전용 표시로 교체했다. 화면에서 QueryDSL 목표와 로드 밸런싱 JSON을 비교한 3/12 결과를 확인했고, 데이터셋 `goal.text`와 목표 칸이 다르면 호출 전 차단하도록 수정했다. 올바른 QueryDSL JSON 12건을 동일 기준과 실제 학교 Qwen API로 실행한 결과는 7/12(58.33%), 관련 7건 중 2건만 RELATED였다. 불일치 5건은 모두 문단 통과 기준 미달로 UNCERTAIN이며 관련 커버리지 0은 그 결과다. 임계값·키워드 산식·기대 라벨·평가 본문은 변경하지 않았다. [진단 기록](features/hybrid-relevance-performance-metrics.md#8-고정-graphless-기준의-querydsl-불일치-진단)을 따른다.

- 2026-10-22 TCP/UDP·로드 밸런싱·QueryDSL의 확정 목표별 1시간 가상 CHROME_DOM 자료를 `evaluation/tcp-udp-1h-cases.json`, `evaluation/load-balancing-1h-cases.json`, `evaluation/querydsl-1h-cases.json`에 작성했다. 0~55분의 5분 간격 화면 관측 12건씩 총 36건이며 각 목표에 기대 RELATED 7·UNRELATED 3·UNCERTAIN 2건을 포함한다. 시간과 의도는 테스트 메타데이터 `caseId`·`purpose`, 정답은 `expectedLabel`에 두며 실제 수집 컬럼을 추가하지 않았다. Schema·전처리 36/36 통과, 모델 실행·실제 1시간 수집·독립 정답 검수는 수행하지 않았다.

- 2026-10-22 사용자 요청으로 현재 Streamlit을 **Graph 없는 키워드+벡터 Hybrid** 실험 경로로 변경했다. 목표 text에서만 키워드를 추출하고 Qwen 임베딩·문단별 K/V/H·본문 커버리지·3라벨·근거·시간을 표시한다. 기본 가중치 0.20/0.80, 관련 H 0.60·최소 cosine 0.55·커버리지 0.60, 무관 후보 H 0.20·커버리지 0.70·최소 본문 80자는 미보정 실험값이며 UI에서 변경할 수 있다. 제목/소제목 제외·묶음 원본 글자 중복 방지·혼합 근거 UNCERTAIN·품질/개인정보/모델 실패 차단·목표 벡터 재사용을 반영했다. OpenAI·ChromaDB·Graph 생성 호출은 이 화면에서 실행하지 않는다. 기존 RelevanceAnalyzer와 공개 계약은 교체하지 않았다. 관련 회귀 41개가 통과했고 학교 API 합성 공부/요리/혼합 3건에서 각각 RELATED/UNRELATED/UNCERTAIN을 확인했다. 전체 1.600초·임베딩 요청 2회이며 혼합은 벡터 캐시를 썼다. 단회 예열 측정·합성 3건으로 독립 정확도나 운영 지연을 주장하지 않는다. 실행/계산/정확도 검증의 차이는 [AI README](../README.md)의 실험 설명을 따른다.

- 2026-10-22 사용자 요청으로 Graph 생성 프롬프트를 `goal-graph-v4`로 간소화했다. 기존 JSON 예시·세 역할·목표 관련 DOM/OCR 본문에 등장할 별칭 지침을 영어 255단어로 정리했다(기존 679단어). OpenAI 입력에서 선택적 `goalProfile`을 제거하고 확정 목표 문장과 식별·버전만 사용한다. 실제 화면 본문은 생성에 전송하지 않는다. 생성 실패 시 최소 Graph의 기존 profile 지원은 이번 변경 대상이 아니며, 전체 입력 계약에서 제거된 것으로 간주하지 않는다. 새 프롬프트의 실제 생성 속도·별칭 품질·라벨 정확도는 재평가 전이다.

- 2026-10-21 확정 목표 5개로 기존 Streamlit Hybrid 실행 함수를 사용해 합성 DOM/OCR 20건을 실제 학교 API로 평가했다. Graph 5개 정상 생성·임베딩 25회/91벡터·2560차원 검증, 최종 기대 일치 13/20(65%)·3라벨 Macro F1 0.4924였다. React 관련 2건과 요리 무관 5건이 UNCERTAIN으로 불일치했다. 분석 평균 1.424초·Graph 신규 준비 평균 14.163초·전체 99.311초이며 판정 로직 변경 없이 [성능지표](features/hybrid-relevance-performance-metrics.md) 7절에 반영했다. 독립 사람 검수·실제 화면 추출·공개 API 검증은 아니다.

- 2026-10-21 [전달용 Hybrid 성능지표](features/hybrid-relevance-performance-metrics.md)를 작성했다. 최신 저장 결과 4건을 재집계해 기대 일치율 25%·3라벨 Macro F1 0.1333·UNCERTAIN 100%, 분석 평균 1.140초·중앙값 0.557초를 기록했다. Graph 생성 13.363초는 별도이며 독립 정확도·P95·운영 성능은 검증 전이다. 모델 재호출이나 로직 변경은 하지 않았다.

- AI 기본 디렉터리와 평가 디렉터리가 생성되어 있다.
- AI 전용 문서 구조와 기능별 템플릿을 구성했다.
- 공통 API 응답 초안은 루트 `docs/API_CONTRACT.md`에 정의되어 있다.
- 현재 활동 관련성 API, 분석 기준, Prompt 가이드를 초안으로 작성했다.
- 관련성, 학습 흐름, MediaPipe 사용자 상태를 분리된 기능으로 개발하는 방향을 정리했다.
- Notion AI 기획 v1.1의 목표 보조 6상태, Client별 OCR 경로, 두 분석 제외 모드, 최근 7일 개인화 휴식 입력 계약을 문서 초안에 반영했다.
- Issue #3에서 AI 개발 불변 규칙, 다중 상태 모델, 데이터 수명, 평가 기준 문서 초안을 작성했다.
- Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로, ColPali 시각 콘텐츠 비교 실험 경로를 문서로 구분했다.
- 목표·논리 세션·회차·이벤트·시간 집계 계약을 `goal_session_spec.md`로 분리했다.
- 콘텐츠 감지·Chrome 내부 OCR·Desktop Apple Vision OCR·ColPali 입력 경계를 `content_acquisition_spec.md`로 분리했다.
- FocusSessionAgent, 피드백·개인화, 비동기 근거 기반 노트의 상세 계약 초안을 추가했다.
- Issue #11의 DOM·OCR 공통 입력 모델, 텍스트 정제·청크 분할, 품질·개인정보·중복·stale 검증과 단위 테스트를 구현했다.
- AI 내부 `analyze_staged`는 메타데이터 판정부터 시작해 필요한 경우에만 Chrome DOM→viewport OCR 또는 Desktop OCR을 요청한다. 단계 전환 전 최신 문맥을 재검증하고 다른 대상·잘못된 출처는 폐기한다. Client의 foreground 선택·DOM/OCR 수집 모듈은 `apps/*/docs/features/screen-acquisition-PLAN.md`에 따라 추가했으며 실제 API 연결은 남아 있다.
- 확정된 임의 목표의 Graph 생성용 OpenAI 호환 LLM Client와 로컬 ChromaDB 영속 저장을 구현했다. API Key·모델이 설정되면 목표/GoalProfile만 전송하고, 저장 Graph를 재사용하며 실패 시 최소 Graph로 내려간다.
- 2026-10-21 Streamlit을 현재 Qwen 연결로 교체하고 사용자 재요청에 따라 채택한 **Hybrid 6번만** 실행하도록 맞췄다. 기존 후보 선택·재정렬 모델 준비 UI는 제거했다. RelevanceAnalyzer school-hybrid-v6의 기존 결합식·역할별 기준·커버리지·충돌 판정을 그대로 사용한다. 목표 Graph 준비는 자동 생성이 기본이며 예제는 React 템플릿을 사용한다.
- 목표·출처·DOM/OCR 텍스트/JSON 입력, 명시적 비교 버튼, 라벨·점수·사유·근거·시간·기대 일치율을 제공한다. 실행법은 AI/README.md를 따른다. 동일 브라우저의 HTTP Client·분석기와 5분 캐시를 재사용하도록 수정했고, API 시간·로컬 계산·캐시 여부를 표시한다. 현재 전체 254 passed·8 subtests passed. 기존 비교 기준선 함수는 이력으로 남아 있지만 현재 화면에서 호출하지 않는다. 독립 정확도 검증과 무관 근거 품질 개선은 남아 있다.

- 2026-10-21 목표 기반 별칭 생성 Prompt만 goal-graph-v3.1로 보강했다. 별도 검증 LLM·매칭/산식·임계값 변경은 없다. 최초 v3의 중복 별칭 실패 후 전역 소유 개념 규칙을 명확히 했다. 새 Graph 생성 13.363초·HTTP 1회이며 실제 JWT 재테스트는 K=0.5로 적중 개선됐지만 H=0.5444와 커버리지 49.6%로 UNCERTAIN을 유지했다. 개발용 4건 기대 일치 1/4로 정확도 개선을 입증하지 못했다. 전체 255 passed·8 subtests passed.

### 진행 중인 작업

- Issue #10 확정 후 Client·Server·AI의 JSON 필드명과 enum 통일 및 외부 입력 Adapter 연결
- 루트 `docs/ARCHITECTURE.md`의 Desktop 중심 흐름과 Notion의 Extension 중심 기본안 차이 검토
- Server·Extension·공유 타입 담당자와 다중 상태 및 API 경로 검토
- 목표·세션·회차·이벤트 관계와 사용자당 동시 활성 회차 정책 검토
- Agent 도구 Schema·예산·Checkpoint 저장소와 피드백 적용 범위 검토
- 학습 노트 Queue·근거 Schema·재생성·삭제 정책 검토
- 기능별 PLAN·ERROR·REPORT와 GitHub Issue·PR 역할 정리
- ColPali 실행 위치와 개인정보·자원 예산 검토
- Notion 내부의 카메라 MVP 포함·출시 미정 충돌을 해결하고 출시 범위를 결정

### 아직 진행하지 않은 작업

- 실행 가능한 AI 서버 진입점 구현
- 합의된 공개 Pydantic 요청·응답 Schema 구현·Server 연결 (학교 내부 입력 Schema는 구현됨)
- 학교 Qwen3 API 연결은 실제로 확인했으며, 학교 GPU 사용률·VRAM 및 정확도·성능 기준 검증은 남아 있음
- 관련성 판정용 LLM Fallback 구현. 목표 Graph 생성 LLM Adapter와 검증·폴백은 구현됐지만 실제 API Key 기반 품질 검증은 남아 있음
- Server 연동
- 학교 GPU 성능·독립 정답 세트 정확도 평가 (합성 평가 CLI는 구현됨)
- 목표·세션·회차·이벤트·집계 Schema 구현
- FocusSessionAgent 상태 그래프와 제한 도구 구현
- 화면 판정 피드백 저장·캐시·알림·집계 무효화 구현 (후속 기능, MVP 제외)
- 비동기 학습 노트 작업과 근거 검증 구현
- 페이지 이동·체류 시간 기반 학습 흐름 분석
- MediaPipe 상태값 보조 분석
- YouTube 자막·PDF 추출 텍스트 연동

### 확인된 문서·코드 차이

- `packages/shared-types`는 Notion 기준 공개 상태 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED`를 정의한다. 내부 AI 세부 상태는 API 경계에서 변환한다.
- Extension의 Content Script·Service Worker에 실제 수집 모듈이 추가됐다. `activeTab`·`nativeMessaging`은 사용하지 않으며 자동 viewport OCR은 optional `<all_urls>` 승인과 최신 세션·제외 정책 공급 뒤에만 실행할 수 있다. Server·AI 왕복은 미연결이다.
- Server에는 실행 설정과 빌드 골격만 있고 목표·세션·이벤트·AI 연동 코드가 없다.
- AI 실행 서버·공개 API 연결은 골격 상태다. Issue #11의 순수 전처리·학교 내부 Schema·Graph·Hybrid 판정·Ollama Client·합성 평가 CLI와 임의 목표용 OpenAI 호환 Graph LLM Client·ChromaDB 저장은 구현되어 있다. 2026-10-21 기본 Client가 `/api/embed`만 호출해 실제 Qwen 벡터와 합성 사례 RELATED를 반환하는 것을 확인했다. Graph LLM 실호출 품질, GPU 사용률·VRAM·독립 정확도는 검증하지 않았다.
- Issue #11의 외부 요청 필드와 enum은 Issue #10 및 Client·Server 계약 확정 전까지 제안이다. 순수 모듈은 문단당 최대 800자를 기본값으로 사용하며 다른 길이·품질 수치는 설정 가능한 초기 실험값이다.
- 최신 dev 문서에는 목표 명확화 경로와 판정값, 시간 기반 이탈 정책 및 카메라 게이트가 반영되어 있다. 이는 문서 계약 상태이며 해당 분석 코드의 구현 완료를 뜻하지 않는다. 팀원 보고 Hybrid 120건 결과도 원자료·결합식·독립 holdout 미확인 상태다.
- 루트 아키텍처는 Desktop 화면 분석을 기본으로 설명하고, 최신 AI 기획은 Chrome DOM·Extension 내부 OCR과 macOS Desktop Apple Vision OCR을 Client별로 분리해 제안한다. 팀 합의 후 공통 문서를 갱신해야 한다.

## 현재 기술

- 언어: Python
- API 서버: FastAPI 검토
- Schema 검증: Pydantic 2.13.5 (학교 내부 Schema 구현, 공개 API 합의 대기)
- 테스트: pytest 9.1.1
- 모델: 학교 Ollama의 `qwen3-embedding:4b-fp16` 실제 벡터 반환 확인; LLM 공급자·운영 연결 계약 검토 필요
- 분석 대상: 화면·페이지에서 추출한 텍스트와 메타데이터
- OCR: Chrome Extension 내부 로컬 OCR과 macOS Desktop Apple Vision OCR의 독립 경로 제안; AI는 허용된 정제 텍스트만 평가
- 시각 분석: ColPali는 PDF·Canvas·이미지·슬라이드 대상 비교 실험 단계
- 카메라 분석: 출시 여부 보류; 출시되더라도 MediaPipe 클라이언트가 계산한 최소 상태값만 사용
- 원본 저장: 원본 화면·카메라 영상은 기본 저장하지 않음

## 예상 데이터 흐름

```text
Client별 사전 제외·권한 검사
  → Chrome DOM 우선 추출 → 부족할 때 Extension 내부 로컬 OCR
  → macOS Desktop 허용 화면 → Apple Vision OCR
  → 개인정보·품질 검사
  → Spring Server 인증·소유권·최신 버전 검증
  → AI 텍스트 정제
  → 규칙 기반 판단
  → 임베딩 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 판단
  → 관련성·흐름·행동 상태 분리
  → Server 결과 저장
  → Extension·웹 대시보드 상태·알림·기록 표시
```

## 반드시 지켜야 하는 조건

- 입력 데이터에 비밀번호·결제 정보·개인 메시지가 포함되지 않도록 확인한다.
- 원본 화면·카메라 영상을 기본 저장하지 않는다.
- OCR 실행과 원본 이미지 처리는 AI 서버 담당 범위에 포함하지 않는다.
- Server 또는 외부 ColPali로 원본 이미지를 전송하려면 현재 개인정보 결정 변경이 먼저 필요하다.
- 관련성 분석 API에는 체류 시간과 MediaPipe 상태값을 섞지 않는다.
- 학습 흐름과 사용자 상태는 별도 분석 기능으로 구현한다.
- 추출·분석·관련성·흐름·행동 상태를 서로 다른 필드로 관리하는 안을 검토한다.
- `OFF_TASK`만으로 `DRIFT_RISK`, 알림 또는 차단을 결정하지 않는다.
- 판단이 어렵거나 실패한 경우 목표 무관이나 이탈로 단정하지 않는다.
- 규칙이나 임베딩으로 충분히 판단 가능한 요청에는 LLM을 호출하지 않는다.
- 모델·프롬프트·임계값 변경은 `DECISION_RECORD.md`에 기록한다.
- 분석 결과에는 최소한 상태값과 신뢰도 기준을 포함한다.
- 오래된 목표·회차·탭 결과는 적용 전에 폐기한다.
- 같은 이벤트·세션 명령·노트 작업의 중복 처리는 멱등해야 한다.
- 명시적 사용자 수정은 정확히 일치하는 목표·콘텐츠 범위에서 캐시보다 우선한다.
- 학습 시간은 Server가 계산하고 LLM이 추정하지 않는다.
- 학습 노트는 관찰 근거 없이 이해·해결 완료를 주장하지 않는다.
- 회차 종료와 시간 저장은 학습 노트 생성 성공에 의존하지 않는다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 작성한다.
- 분석 제외는 `ANALYSIS_ONLY`와 `ANALYSIS_AND_TIME`을 구분하고 두 경우 모두 콘텐츠 payload를 AI에 보내지 않는다.
- 목표 보조는 사용자의 최종 확인 전 확정 목표로 사용하지 않는다.

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 입력·출력 계약 변경
- 모델·프롬프트·임계값 결정
- 발생한 오류와 평가 결과
- 다음 작업과 선행 조건

## 2026-10-07 학교 캡스톤 지침 작성

- 이재빈 노션 전처리·관련성 문서를 기준으로 [통합 #11 PLAN](features/learning-goal-content-relevance-evaluation-PLAN.md)을 작성했다. Issue 본문은 지침의 요구사항 원본으로 사용하지 않았다.
- 현재 개발 방향은 Hybrid 6번·Qwen3-Embedding-4B·FP16·vLLM이며 숫자 결합 비율·임계값은 미확정이다. Windows GPU는 WSL2·Linux 방향으로 호환성을 확인한다.
- 김성현 → 이재빈 입력은 text·ocr 형식, 최종 qualityScore·contentHash는 이재빈 전처리 책임이다. 출력 계약은 유지하고 백엔드 필드 9개는 협의 예정이다.
- 이번 Branch에서 변경한 것은 지침·작업 기록 문서다. AI 모델·관련성 CLI·FastAPI 구현 및 학교 GPU 성능 측정은 수행하지 않았다. #11 작업 공간의 미커밋 전처리는 자동 통합하지 않았다.
- 기존 문서의 후보 미정·사용자 판정 수정/피드백 우선 제안은 이번 PLAN의 현재 구현 근거로 사용하지 않는다. 공통 API 계약의 채택 여부는 별도 확인한다.

## 2026-10-07 이슈·Branch 통합

- 기존 #14 관련성 구현·평가 범위를 #11 입력 검증·전처리와 통합한다.
- 통합 Branch는 `feat/11-ai-preprocessing-relevance`이며 학교 지침서도 이 Branch를 가리킨다.
- 전처리와 관련성은 한 Issue에서 개발하되 모듈·테스트는 분리한다. 최신 요구사항과 기존 출력 계약은 유지한다.
- 로컬 미커밋 전처리·실험 작업은 백업·복원해 보존하고 문서 통합 커밋에 포함하지 않는다. 원격 문서 준비와 실제 코드 구현·게시·GPU 검증을 구분한다.
- #14는 #11로 통합한 이력으로 남기며 완료 구현으로 닫지 않는다.

## 2026-10-07 기존 전처리·실험 코드 게시 검증

- 기존 로컬 전처리 순수 모듈과 유사도 실험 소스를 #11 통합 Branch에 게시한다.
- 모델 없는 관련 테스트는 30 passed, 6 subtests passed. 실제 모델/GPU/네트워크 측정은 이번 검증에 포함하지 않았다.
- 현재 내부 품질 검사는 입력 quality_score를 사용하며 최종 qualityScore 산출·실제 입력 Adapter·전체 현재 문맥 검증은 후속 구현이다.
- 기존 Streamlit은 E5-small/BGE reranker 실험형이고 현재 MVP Qwen3 4B·FP16·Hybrid 6번 운영 구현과 구분한다.
- 범위·검증·남은 제한은 [부분 구현 게시 결과](features/learning-goal-content-relevance-evaluation-REPORT.md)를 따른다.

## 2026-10-07 학교 Ollama 테스트 지침

- 최신 이재빈 노션과 사용자 요청에 따라 Mac에서 개발하고 학교 Windows GPU PC에서 Ollama로 Qwen3 Embedding 4B·FP16 API를 먼저 테스트한다. 추후 WSL2·Linux·vLLM으로 전환하는 방향은 유지한다.
- [설치·테스트 지침](features/school-ollama-embedding-SETUP.md)에 학교 로컬 벡터·GPU 확인, 반복 요청 측정, Mac SSH 터널·왕복 측정을 분리해 작성했다.
- 이번 작업은 지침서 작성이다. 학교 PC 설치·GPU·API·SSH 실기 검증과 실제 프로젝트 Embedding Client 구현은 수행하지 않았다.

## 2026-10-07 통합 #11 실제 개발

- `ContentInput`·`VerifiedContext`·`ConfirmedGoal`을 분리하고 공통 `text`·`ocr` 입력을 기존 전처리에 연결했다. 검증 문맥은 호출자가 Server 검증 결과 또는 합성 Fixture로 제공하며 AI가 인증을 대신하지 않는다.
- 최종 qualityScore는 원천 품질 × 정상 문자 비율 × 정제 단위 유지 비율로 계산한다. DOM은 Client qualityScore를 최종값으로 신뢰하지 않고 OCR은 인식 confidence를 상한으로 사용한다. 산식·임계값은 실험 정책이다.
- PLAN 사례 기반 초기 목표 템플릿과 DIRECT/PREREQUISITE/SUPPORTING 역할별 Hybrid 판정, 대표 본문 범위 검사·적극적 무관 주제 근거를 구현했다. 템플릿·무관 주제 범위와 임계값의 독립 검수·보정은 남아 있다.
- 최초 Ollama Client는 `qwen3-embedding:4b-fp16`·manifest digest·F16·2560차원·embedding capability를 확인하고 `/api/embed`에 batch 요청한다. HTTP timeout·제한 재시도·동시 요청·입력/응답 크기 상한을 둔다.
- 원문 없는 유한 TTL/LRU 목표 벡터·결과·이벤트 캐시, 사용자·목표·run·정책·모델·Graph·전처리/추출기 분리, 중복 충돌과 응답 직전 재검증을 구현했다. 상태 변경·제외·민감·실패 시 캐시를 정리하며 Server는 종료·삭제 시 `clear_caches()`를 연결해야 한다.
- 합성 평가 CLI의 fake/real 모드와 목표군이 겹치지 않는 개발/평가 split을 구현했다. fake 결과는 로직 검증이며 모델 정확도·GPU 성능이 아니다. real은 digest가 없거나 연결이 실패하면 오류로 종료하며 fake로 전환하지 않는다.
- 공개 FastAPI·응답 envelope/confidence·Server 연동, 백엔드 9개 컬럼 합의, 최근 흐름/추가 LLM, vLLM 전환과 학교 실측은 미완료다. 실제 테스트 결과·명령은 [구현 리포트](features/learning-goal-content-relevance-evaluation-REPORT.md)를 따른다.

## 2026-10-11 제목·소제목·코드 입력 반영

- 기존 `pageTitle`의 임베딩을 실제 Hybrid 점수·충돌 검사·근거 ID에 연결했다. `text`에 명시된 Markdown h1~h3는 내부 `HEADING`으로 구분한다. 제목·소제목은 본문 커버리지에 포함하지 않으며 이 정보만으로 관련·무관을 확정하지 않는다.
- 코드 fence의 언어·설명·들여쓰기를 보존하고 CODE 문자열의 HTML entity를 임의로 변환하지 않는다. 개인정보 검사의 별도 decode는 유지한다.
- 기존 `appName`·`urlHost`로 결과 캐시를 구분한다. 목표 벡터는 재사용하며 앱·도메인을 임의 점수로 변환하지 않는다. 공개 입력·응답 컬럼은 추가하지 않았다.
- 전처리 `school-text-v2`, 분석기 `school-hybrid-v2`. 2026-10-14 전체 테스트 166 passed, 6 subtests passed. 실제 Qwen3 벡터 생성과 합성 평가를 확인했으며 정확도·GPU 자원 검증은 별도다.
- 창 제목·포커스·구조화 소제목/코드 정보의 실제 전달 계약은 추가 확인이 필요하다. 명시적인 구조가 없는 OCR·평문에서 소제목이나 코드 언어를 추측하지 않는다.
- 사용자 요청으로 Notion AI 최종 기획서 (2)의 7.1·8.2·8.3절, 이재빈 전처리 5·7절, 관련성 7.2·10·11절과 유사도 검색 6번 설명에 이 구현 범위를 반영했다. 수정한 4개 페이지를 다시 조회해 의도한 본문, 기존 JSON 예시와 하위 페이지 참조 보존을 확인했다. 제목·소제목 처리는 현재 로컬 실험 정책으로 표시했고 숫자 가중치·임계값이나 새 공개 컬럼을 확정하지 않았다.
- 학교 전달용 [단일 MD 지침](features/school-ollama-embedding-SETUP.md)을 현재 노션·코드 기준으로 개정했다. 제목·소제목·코드 입력, 임베딩 payload, 기존 점수/커버리지/충돌, 내부/공개 출력 경계와 Windows/Mac 실행 명령을 담았다. 11개 JSON 예시·내부 Schema·정제 순서·실제 payload·설정값을 검증하고 공개 요청/성공/실패 3개 예시가 현재 노션과 일치함을 확인했다. Windows·학교 GPU·SSH 실기 검증은 미실행이다.

## 2026-10-12 최신 16개 더미 데이터

- `AI/evaluation/relevance_cases.json`을 `synthetic-school-v2`로 갱신했다. 기존 JSON 평가 형식을 유지하고 개발 8개·평가 8개와 목표 템플릿 분리를 유지했다.
- 제목·소제목·본문 일치, 제목/본문 양방향 충돌, 소제목/본문 충돌, 제목·소제목만 있는 입력, 제목 없는 OCR, 코드 언어/entity 보존, 긴 제목·소제목의 커버리지 제외를 반영했다. 선수·보조, 무관 주제, 캡처 실패, PARTIAL, 제외·민감 사례도 포함한다.
- 기존 fake 벡터를 전처리 후 전체 단위 수·순서에 맞췄다. expected는 기획/보류 정책에 따른 초안이며 사람의 독립 검수가 필요하다. real 모드가 fake 벡터를 사용하지 않는 경계는 유지한다.
- fake 개발/평가 CLI 각각 8/8 기대 라벨 일치·종료 코드 0, 전체 166 passed·6 subtests passed. 충돌/본문 부족 사유, 근거 ID, 코드 entity와 분할 순서도 검증했다. 실제 Qwen3/GPU 정확도·속도를 측정한 결과가 아니다.
- 학교 MD의 데이터 목록·개정일을 함께 갱신했다. 운영 분석 코드·공개 API·Graph·숫자 정책은 변경하지 않았다.

## 2026-10-12 학교 모델 연결 확인

- 사용자 제공 API 형식은 현재 Ollama Client의 `/api/embed` 요청과 일치한다. Mac 코드 버전 `school-text-v2`·`school-hybrid-v2`와 SSH Client를 확인했다.
- 샌드박스의 최초 localhost 호출은 네트워크 권한으로 거부되어 연결 상태 근거로 사용하지 않았다. 허용된 제한 밖 호출에서 `127.0.0.1:11435/api/tags`는 ConnectionRefusedError(errno 61)를 반환했다. 모델 metadata·벡터·real Hybrid 실행은 아직 확인하지 못했다.
- 제공된 SSH 명령은 SCHOOL_USER·SCHOOL_HOST 예시이며 실제 접속 대상·포트가 없다. 실제 대상 정보를 요청했다. 비밀번호·개인키를 문서에 기록하지 않는다.
- 이후 사용자 제공 SCHOOL_LAN.md의 학교 내부 FastAPI `10.125.43.92:8000` 경로를 확인했다. 실제 Mac Wi-Fi IP는 문서의 허용 IP와 일치했고, 제한 밖 `/health` 요청은 연결 시도 5초 후 timeout을 반환했다. 라우트는 en0·학교 Wi-Fi 게이트웨이를 사용했다.
- 사용자가 학교 관리자 PowerShell의 Enable-MacAccess.ps1을 아직 실행하지 않았다고 확인했다. 이 선행 작업 후 `/health`·합성 `/api/embed` 검증을 재개한다. timeout만으로 Windows 방화벽과 학교 유무선망 차단 중 원인을 확정하지 않는다.
- 문서의 LAN 게이트웨이는 `/health`·`/api/embed`만 제공한다. 기존 Client는 원격 HTTP를 거부하고 Ollama metadata 경로를 요구하므로 해당 Client를 이 주소로 변경해 연결 완료로 표시하지 않았다. 실제 health 응답 확인 후 필요한 연동 경계를 검토한다.

## 2026-10-13 Tailscale 연결 준비

- 사용자가 Tailscale을 선택했고 학교 PC에서 직접 설치·로그인·Serve 실행을 진행할 수 있다고 확인했다. Mac에는 Tailscale 명령과 `/Applications/Tailscale.app`이 확인되지 않았다.
- 공식 `Tailscale-1.102.4-macos.pkg`를 다운로드했고 Homebrew cask의 SHA256 일치와 Tailscale Inc. 개발자 서명·Apple 공증을 확인했다. 설치·로그인·VPN 허용은 아직 완료하지 않았다. 설치 UI 접근은 Computer Use 권한 대기 상태다.
- 기존 학교 로컬 FastAPI를 private HTTPS Serve로 연결하는 절차를 학교 SETUP과 단일 전달 MD에 준비했다. 학교 HTTPS 주소를 받은 뒤 `/health`·실제 합성 벡터를 확인한다. 모델 연결·Hybrid 실측 완료로 표시하지 않는다.
- 현재 운영 Client는 수정하지 않았다. 학교 FastAPI의 모델 metadata 계약은 실제 응답 확인 후 연동해야 한다.

## 2026-10-14 학교 Qwen 실제 연결·합성 파이프라인 확인

- Mac에서 학교 PC의 Tailscale Serve HTTPS `/health`가 HTTP 200을 반환했다. 응답은 `qwen3-embedding:4b-fp16`, digest `3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc`, F16, 2560차원, Ollama 0.40.0을 보고했다.
- 실제 단일 텍스트 임베딩이 2560차원 유한 벡터로 반환됐고 L2 norm 1.0을 확인했다. 첫 요청 Ollama 내부 시간 6.238초(모델 로딩 6.176초), 후속 단일 요청 내부 시간 0.245~0.329초였다.
- 학교 FastAPI는 `/health`·`/api/embed`만 노출한다. 기존 `OllamaEmbeddingClient`/평가 CLI의 `/api/tags`·`/api/show`·`/api/version` 검증 계약과 맞지 않아, 변경 없는 운영 CLI 실행은 되지 않았다. 실제 평가에는 응답 메타데이터를 확인하고 `/api/embed`를 호출하는 일회성 메모리 Adapter를 사용했다. 저장소 코드는 변경하지 않았다.
- 일회성 Adapter로 development 합성 사례 8개를 실행했다. 7개 임베딩 배치 요청이 성공했고 재시도는 없었다. 분석된 6건 중 임시 기대 라벨 일치 3건, 불일치 3건이며 2건은 본문 부족·BLACK_SCREEN으로 모델 호출 전에 건너뛰었다. 기대 라벨은 사람 검수가 안 된 초안이므로 정확도 지표로 해석하지 않는다.
- 전체 개발 평가 경과시간은 58.39초였다. 요청 왕복시간은 중앙값 6.08초·95백분위 23.31초, 서버 Ollama 처리시간 중앙값 약 0.35초였다. 별도 웜 단일 요청 Mac 왕복은 2.619초·16.419초·5.704초, 서버 처리 0.245~0.329초였다. 측정 상세와 단계별 제한은 [구현 리포트](features/learning-goal-content-relevance-evaluation-REPORT.md)에 기록한다.
- `tailscale status`와 `tailscale ping`은 계속 `relay "tok"`/DERP 경로였다. 해당 성능 측정은 직접 연결이 아닌 릴레이 환경이며 GPU 사용률·VRAM은 수집하지 않았다.
- 다음 선행 작업은 학교 API의 승인된 접근 경로와 기존 Ollama Client metadata 계약을 일치시키는 것이다. 그 뒤 사람 검수 개발 데이터로 보정하고 설정을 동결해 독립 evaluation split을 실행한다. 공개 FastAPI·Server 연동과 MVP 완료는 아직 아니다.

## 2026-10-17 화면 분석 REVIEW 기획의 내부 파이프라인 반영

- `APP_META`·`TAB_META`를 DOM/OCR보다 먼저 검사한다. 같은 검토 Graph 개념의 aliases가 2개 이상 명확히 일치할 때만 임베딩 없이 판정하고, 한 단어·빈 정보·관련/무관 충돌은 `UNCERTAIN`으로 유지한다.
- 내부 입력의 `eventId`를 `clientRunId`로 교체해 기존 전처리 event key에 매핑했고 `sequence`, `contentSource`, `isActiveTarget`, `windowTitle`을 최신 문맥 검사에 포함했다. 결과도 `clientRunId`, `sequence`, `contentSource`에 귀속한다.
- 비활성 창·탭과 낮은 sequence의 늦은 결과는 모델 호출 없이 `UNCERTAIN` 또는 stale 폐기한다. AI는 활성 대상을 직접 탐지하지 않고 Client 조정자가 검증한 값을 소비한다.
- 분석기 버전은 `school-hybrid-v3`이다. 관련 단위 테스트 전체 `174 passed, 8 subtests passed`를 확인했다. 실제 Qwen 정확도·메타데이터 hit 비율·지연 개선은 미측정이다.
- `templateId`가 없는 확정 목표는 목표 문장·GoalProfile 기반 최소 Graph를 자동 생성해 분석할 수 있다. 생성형 LLM Graph Client 인터페이스·프롬프트·Schema 검증·목표 버전 캐시도 구현했지만 실제 제공자/API Key와 Server 영속 저장은 미연결이다.
- 임의 목표 Graph 경로 반영 후 현재 분석기 버전은 `school-hybrid-v4`이며 전체 `191 passed, 8 subtests passed`를 확인했다. 이는 외부 생성형 LLM·학교 GPU 실측 결과가 아니다.
- 콘텐츠 근거 부족에 한해 내부 결과가 다음 수집 출처를 명시한다. Chrome은 `TAB_META → CHROME_DOM → CHROME_VIEWPORT_OCR`, 일반 Desktop 앱은 `APP_META → DESKTOP_APP_OCR`이며 정책 차단·stale·Graph/모델 오류는 추가 수집하지 않는다.

## 2026-10-20 학교 전달 MD 정합성 보완

- [학교 전달 지침](features/school-ollama-embedding-SETUP.md)에 메타데이터 우선 단계, 내부 `next_content_source`, 선택적 templateId·GoalProfile·LLM/최소 Graph·Chroma 설정을 현재 코드 기준으로 반영했다.
- Mac의 Tailscale Serve `/health`·`/api/embed` 경로와 학교 PC의 Ollama `127.0.0.1:11434` 직접 평가를 구분했다. 학교 localhost real CLI는 표준 Client를 사용할 수 있으며, Mac Serve 경로는 메타데이터 검증 계약 연결이 남아 있다.
- 로컬 모델 없는 검증은 205 passed, 8 subtests passed 및 fake 개발/평가 각 8/8이다. 학교 localhost real CLI·GPU·Graph LLM 품질을 이번 문서 작업에서 새로 측정한 것은 아니다.

## 2026-10-21 기본 임베딩 Client의 메타데이터 조회 제거

- 사용자 요청에 따라 임베딩 전후 및 CLI 초기화의 모델 정보 조회를 제거했다. 시작 시 health 조회도 하지 않으며 실제 HTTP 경로는 POST /api/embed뿐이다.
- 반환 모델명·벡터 개수·2560차원·유한값·0벡터 검증은 받은 응답만으로 수행한다. 설정 revision과 F16은 원격 검증 결과가 아니며 서버 모델 교체 시 설정 갱신과 프로세스 재시작이 필요하다.
- Client·real CLI HTTP Mock·관련성 파이프라인 회귀 112개가 통과했다. 기본 Client로 실제 학교 API를 호출한 dev-state-direct는 RELATED / GOAL_CONNECTED였고 요청은 /api/embed 2회, 전체 경과시간 10.499초였다. 독립 정확도 평가나 학교 GPU 시간 측정 결과는 아니다.

## 2026-10-21 DOM·OCR 단건 입력 예제

- AI/evaluation/demo-chrome-dom.json, demo-chrome-ocr.json, demo-desktop-ocr.json을 현재 ContentInput 형식으로 만들고 공통 ConfirmedGoal은 demo-goal.json으로 분리했다. 평가 wrapper나 공개 contentPayload 형식을 내부 입력에 섞지 않는다.
- 텍스트·식별자·활성 상태·OCR confidence는 테스트용 작성 값이며 실제 화면·김성현 실행 출력에서 수집한 값이 아니다. 세 예제는 같은 React 목표 관련 내용을 사용한다.
- 세 입력과 목표의 Schema, 목표 ID/버전 일치, 전처리 should_analyze를 확인했다. 비교 단위는 DOM 5개, Chrome OCR 4개, Desktop OCR 3개다. 학교 SETUP 10.3.2절에 기본 Client로 직접 라벨을 확인하는 명령을 추가했다. 이번 예제 작성에서는 실제 모델을 새로 호출하지 않았다.

## 2026-10-21 Mac의 DOM·OCR 예제 실제 모델 실행

- 기본 OllamaEmbeddingClient와 RelevanceAnalyzer로 demo JSON 세 입력을 학교 Tailscale HTTPS API에 전달했다. 분석 실행은 세 입력 모두 SUCCEEDED다. Chrome DOM은 UNCERTAIN / INSUFFICIENT_EVIDENCE(6.9315초), Chrome OCR은 RELATED / GOAL_CONNECTED(0.6885초), Desktop OCR은 RELATED / GOAL_CONNECTED(3.0478초)였다.
- 총 경과시간 10.670초, 실제 요청 4회 모두 POST /api/embed이며 메타데이터 조회는 없었다. 동일 목표 개념 벡터를 첫 입력 이후 재사용했다. 임시 임베딩 Adapter를 사용하지 않았다.
- 데이터·OCR confidence·활성/세션 문맥은 합성 예제다. 실제 화면 수집·인증·Server API 통합과 독립 정확도 검증 결과로 간주하지 않는다. 판정 기준은 수정하지 않았다.

## 2026-10-21 트랜스포머 디코더 가상 1시간 실제 모델 평가

- 확정 목표 “트랜스포머 디코더 공부”로 학습 8건·딴짓 4건·판단 보류 2건의 연속 가상 60분 시나리오를 작성했다. 기대 라벨은 모델 실행 전에 고정했으며 입력 SHA-256은 실행 전후 동일했다. DOM 6건, Chrome OCR 4건, Desktop OCR 4건이다.
- 기존 임의 목표 Graph 생성 경로는 LLM 요청 1회 후 최소 Graph로 폴백했다. Graph에는 목표 전체 문장을 별칭으로 가진 DIRECT 개념 1개와 무관 주제 0개만 있었다. 세부 실패 원인은 이번 실행에서 수집하지 않았다.
- 기본 Client로 학교 Qwen 실제 임베딩 요청 14회를 실행했고 메타데이터 조회는 없었다. 결과는 14건 모두 UNCERTAIN이며 기대와 2건 일치·12건 불일치(14.29%)했다. 13건 SUCCEEDED, 짧은 로딩 화면 1건 SKIPPED이며 네트워크/분석 실행 실패는 없었다. 총 43.124초에는 Graph 준비 4.464초가 포함된다.
- 저장된 Graph와 기존 키워드 코드로 14건 모두 관련 별칭 일치 0임을 확인했다. 현재 키워드 통과 조건과 무관 주제 부재로 관련·무관 근거를 만들 수 없었다. 데이터·정답·임계값을 결과에 맞춰 바꾸지 않았다.
- 사례별 일치/불일치와 제한은 [독립 MD 리포트](features/transformer-decoder-1h-evaluation-REPORT.md)에 기록했다. 실제 1시간 운영·실제 화면 데이터·집중시간·알림 정책·독립 사람 정답의 정확도 평가가 아니다. 다음 우선 작업은 LLM Graph 생성·검증 실패 원인 확인이다.

### 2026-10-21 목표 Graph 오류 진단 재실행

- Graph 폴백 경로에 `GOAL_GRAPH_FALLBACK` 경고를 추가했다. 고정 오류 코드·HTTP 상태·허용 목록의 제공자 코드/파라미터·검증 위치/규칙·JSON 오류 위치·통신 실패 종류만 기록한다. API Key, 목표/응답 원문, 제공자 메시지와 traceback은 로그에 넣지 않는다.
- 같은 확정 목표 `트랜스포머 디코더 공부`로 캐시 없이 LLM 생성을 재시도했다. 실제 HTTP 요청 1회, HTTP 200, 8.361초, 관련 개념 4개·무관 주제 2개로 Schema 검증에 성공했고 최소 Graph 폴백은 발생하지 않았다.
- 과거 실패는 재현되지 않았으며 당시 오류가 기록되지 않아 원인은 미확정이다. 이번 변경은 진단 추가이며 과거 원인 수정 완료를 의미하지 않는다. 새 Graph로 14건 관련성 평가는 재실행하지 않았다.
- 진단/기존 Graph 경로 테스트 24 passed. [진단 결과](../evaluation/transformer-decoder-graph-diagnostic.json)와 [평가 리포트 추가 기록](features/transformer-decoder-1h-evaluation-REPORT.md)을 참고한다.

### 2026-10-21 생성 성공 Graph의 품질 검토 및 관련성 재평가

- 생성 성공 당시의 관련 개념 4개·무관 주제 2개 Graph를 변경 없이 고정했다. 기존 합성 14건의 입력 해시·기대 라벨·instruction·가중치·임계값을 유지하고 기존 RelevanceAnalyzer와 실제 학교 Qwen API로 재판정했다. 새 Graph LLM 호출은 0회다.
- 실제 임베딩 14회·42개 텍스트·모든 HTTP 200·메타데이터 조회 0회. 관련성 판정 총 30.697초. 13건 분석 성공·로딩 1건 TOO_SHORT로 생략했으며 14건 모두 UNCERTAIN, 기대 라벨 일치 2/14건(14.29%)으로 이전과 동일했다.
- 관찰용 함수로 실제 판정 반환값을 바꾸지 않고 216개 문단-개념 점수·통과 조건을 수집했다. 통과 쌍 0개, 모든 분석 사례의 관련/무관 커버리지 0이었다.
- masked self-attention 본문: 키워드 0.5·코사인 0.7622·결합 0.6704로 선수 기준 0.76 미달. cross-attention 본문: 코사인 0.7476이지만 별칭 미검출로 결합 0.4860. 무관 주제 제빵·원예는 게임·영화·쇼핑·김치볶음밥 조리에 매칭되지 않았다.
- Graph Schema 성공과 의미·분류 품질은 다르다. 핵심 구성 요소의 역할 배정, 별칭 범위, 무관 주제 선정과 현재 키워드/결합 기준을 함께 검토해야 한다. 이번 작업에서 코드·Prompt·Graph·기준은 변경하지 않았다. 이 합성 일치율은 독립 검수된 운영 정확도가 아니다.
- [재평가 JSON과 문단별 점수](../evaluation/transformer-decoder-1h-generated-graph-results.json), [품질 검토·활동별 일치/불일치 MD](features/transformer-decoder-1h-evaluation-REPORT.md)에 기록했다.

### 2026-10-21 Graph 지침 v2·임계값 하향 적용 및 실제 비교

- 사용자 요청으로 생성 지침의 직접/선수/보조 역할·핵심 범위·한/영 정식 용어·일반 무관 활동 범주를 보강했다. 생성기 fingerprint에 Prompt 버전과 지침 내용을 넣어 이전 Graph 캐시 재사용을 분리한다. 입력/Graph JSON 구조는 유지했다.
- DIRECT 0.62, PREREQUISITE/SUPPORTING 0.65, 양성 무관 0.75를 실험값으로 적용했다. 나머지 산식·가중치·최소 코사인 0.55·커버리지·충돌·품질·실패 처리는 유지한다. 버전은 school-hybrid-v5 / school-experimental-v2, 목표 지침은 goal-graph-v2다.
- 실제 LLM으로 디코더 Graph 관련 9개·무관 4개, SQL Graph 관련 6개·무관 4개를 생성했다. 현재 화면·평가 라벨은 Graph 입력에 사용하지 않았다. 기존 14건과 실행 전에 기대 라벨을 고정한 별도 합성 20건에서 같은 실제 Qwen 벡터에 이전/하향 기준을 비교했다.
- 기존 14건: 과거 지침+과거 기준 2/14 → 새 지침+과거 기준 3/14 → 새 지침+하향 기준 5/14. 별도 20건: 새 지침+과거 기준 5/20 → 하향 기준 7/20. UNRELATED 검출은 양쪽 모두 0건이다. 관련·무관 상호 오판은 없었지만 보류가 많아 운영 품질 충족을 의미하지 않는다.
- 본문 커버리지 부족과 문장형/긴 별칭 미검출이 남았다. SQL GROUP BY·요리 등의 짧은 실제 표현을 놓쳤다. 이후 품질 보완과 독립 사람 검수가 필요하며 이번 결과를 보고 기준이나 기대 라벨을 다시 변경하지 않았다.
- Graph 생성 2회·21.004초. 임베딩 38회 시도 중 HTTP 응답 이벤트 36회는 200, 응답 이벤트가 없는 시도 2회 뒤 재시도했다. 비교 627.842초, 최종 분석 실패 0건. 메타데이터 조회 0회, 로컬 테스트 219 passed·8 subtests passed.
- [실제 비교·Graph·점수](../evaluation/graph-quality-v2-results.json), [별도 합성 20건](../evaluation/graph-quality-validation-cases.json), [최신 MD 결과](features/transformer-decoder-1h-evaluation-REPORT.md)에 기록했다. 두 합성 세트 모두 이제 결과를 확인했으므로 최종 평가용 새 세트로 재사용하지 않는다.

### 2026-10-21 설명·코드 문맥 묶음 구현

- school-hybrid-v6는 같은 입력에서 인접한 설명 BODY/OCR와 명시 CODE를 추가 묶음으로 검색·임베딩한다. 원문 인접 관계도 확인하고 8,000 UTF-8 바이트/Client 상한을 적용한다. 기존 문단·전처리 해시·입출력 JSON은 유지한다.
- 원래 개별 분석과 반대 근거를 보존하고, 묶음 근거는 원래 설명·코드 ID로 환산한다. 커버리지에 같은 원본 길이나 묶음 구분자를 중복 가산하지 않는다. 표식 없는 평문 OCR에서 코드를 자동 추론하지 않는다.
- 전체 로컬 테스트 235 passed, 8 subtests passed. 구조 경계·삭제된 중복 소제목 경계·입력 크기·단독 코드·반대 근거·중복 커버리지·배치 전달을 검증했다.
- 같은 고정 Graph와 실제 학교 벡터로 causal mask 사례를 비교했다. 묶음 없이 UNCERTAIN/INSUFFICIENT_EVIDENCE, 적용 시 RELATED/GOAL_CONNECTED, 근거 passage-003/004였다. 묶음 K=1·코사인 0.7421·결합 0.8323. 원본 100+174자 전부가 관련 커버리지에 한 번씩 포함됐다. API 2회 모두 200, 전체 7.583초, Graph LLM/메타데이터 호출 0회.
- [단건 실제 결과](../evaluation/code-context-grouping-smoke.json). 기존 14/20건 전체를 새 버전으로 재평가한 결과는 아니며, 무관 검출·별칭 품질 문제는 남아 있다. 별칭 생성 Prompt는 이번 묶음 구현에서 변경하지 않았다.
