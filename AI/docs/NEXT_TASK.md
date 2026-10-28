# AI 다음 작업

- 2026-10-26 화면 계약 연동: Server가 최신 관측/세션/정책 문맥과 추출 상태를 `adapt_screen_request`에 전달하고 결과를 `public_response`로 검증하도록 연결한다. 공개 decision confidence의 보정 근거와 Desktop OCR 품질의 전달 위치를 합의한다. 클라이언트는 메타데이터 non-RELATED의 본문 재확인, P1 Chrome OCR 제외, 2,000자/민감정보 처리, 기기 내 최신 관측 폐기를 실제 구현해야 한다. 이후 실제 수집 → Server → AI → Validator/Policy Engine 왕복을 검증한다. 모듈 함수 검증은 배포/API 통합 완료가 아니다.

- 2026-10-25 `title-hybrid-v2`로 새 주제 메타데이터 120건 중 일반 제목 30건의 무관 오판을 교정했다. 남은 54건은 공부→보류 46·공부→무관 6·딴짓→보류 2다. 별도 실제 화면·사람 검수 자료로 제목의 정보성과 낮은 코사인 무관 근거를 검증한다. 이번 120건은 개발/회귀 자료로 관리하고, 현재 수정만으로 전체 오판이 없어졌다고 보고하지 않는다. 실제 DOM/OCR 재수집 연결과 공개 요청 신호 계약은 Client·Server와 별도 확인한다.

- 2026-10-25 후속 설명 변경 후 남은600건 불일치는 메타데이터49·본문 근거 부족22·QueryDSL 무관 오판1이다. 독립 사람 검수 화면에서 새 주제·영문/한국어 혼합·OCR 오탈자·참조 없는 설명·실제 혼합 활동을 검증한다. 현재 휴리스틱은 완전한 의미 분류기가 아니다. 기존600건과 새16건은 앞으로 개발/회귀용이며 결과에 맞춘 정답·임계값 재조정을 피한다.

- 2026-10-25 전체600건 재평가에서 이번 도입 문장 정책의 개선 폭을 확인했다. `all-dummy-negative-policy-results.json`의 BODY/METADATA 및 사유별 분리를 바탕으로 다음 진단을 진행한다. 이번 알려진600건으로 숫자를 재조정하면 개발 자료로 취급하고 독립 검수 화면을 별도로 준비한다.

- 2026-10-25 `adjacent-intro-v1`의 문서 간 코사인0.50은 임시 실험값이다. 사람 검수한 실제 DOM/OCR에서 소제목·중간 주제 전환·짧은 딴짓 문구를 별도 평가한다. 기존48건의 RELATED 오판5건(무관1·모호4)은 이번 도입 문장 수정으로 해결되지 않았다. 기존 평가셋에 맞춰 임계값을 추가 조정하지 않는다. 재현 입력은 `evaluation/negative-candidate-cases.json`, 원문·벡터 없는 결과는 `evaluation/graphless-negative-policy-results.json`이다.

- 원격 전달 후 새 체크아웃에서 Streamlit 실행·설정 파일 포함 여부를 확인한다. `.env`·`scripts/langsmith.py`·전달용 ZIP은 커밋 대상이 아니며 모델 연결 값은 각 실행 환경에서 설정한다.

- 메타데이터 별도 경로의 합성240건은191건 일치했다. 제목 점수 미달 관련→보류49건을 확인했으며 본문 길이·커버리지 제한은 미적용이다. 접두사 없는 실제 제목·무관/공통어 함정·일반 제목을 사람 검수한 독립 자료로 평가하고, 메타데이터 기준을 보정할 때 DOM/OCR 보정값과 분리한다.

> AI 담당자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 현재 Streamlit은 `body-context-hybrid-v3`, 보정값 V0.30/H0.35·무관V0.25다. 실제 Qwen 기존 React24는23/24, 다른 목표 합성평가는20/24였지만 무관1건·정보부족3건을 RELATED로 오판했다. 다음은 사람 검수한 실제 DOM/OCR의 직접/선수/보조/무관/정보부족/같은 용어의 다른 주제 평가다. 현재 평가24건으로 숫자를 다시 조정하면 개발 자료로 전환하고 별도 평가셋을 새로 준비한다. [보정·오탐 보고서](features/graphless-hybrid-calibration-REPORT.md)를 먼저 읽는다. 사이트 실행마다 기준을 바꾸거나 기대 라벨을 결과에 맞추지 않는다.

- 사용자 요청으로 바탕화면으로 이동한 `/Users/lyg123d/Desktop/FocusOn더미데이터/`의 10개 JSON은 사용자가 직접 검증할 자료다. 각 파일의 `goal.text`를 Streamlit 목표 칸에 넣고 해당 파일 전체를 입력한다. 모두 RELATED 기대값인 합성 자료이므로 관련 화면 보류/누락을 확인하는 용도이며, 이번 변경에서 모델 실행은 하지 않았다.

- 로컬 Streamlit의 코드 재반영 중 프로세스 종료 139가 관찰됐다. [오류 기록](features/hybrid-streamlit-reload-ERROR.md)을 참고한다. 원인은 미확정이며 현재 서버는 파일 자동 감시를 끄고 새 프로세스로 실행한다. 이후 코드를 변경하면 서버를 재시작해 검증한다.

- `kiwi-core-phrases-v2`의 핵심/일반/구문 구분은 작은 명시 규칙이다. 새 목표군에서 `선택 정렬`·수학의 정리 등 일반 표현이 실제 개념의 일부인 경우와 약한 핵심 단어만 겹치는 경우를 독립 검수한다. 현재 36건 합성 기대 일치는 26/36이며 재사용한 개발 세트 결과다. TCP/UDP의 남은 관련 문단 보류 3건 등은 이번 수정으로 해결되지 않았다. 판정 임계값이나 기대 라벨은 이번 결과에 맞춰 변경하지 않는다.

- `kiwi-nouns-tech-v1` 적용 후 단일 QueryDSL 합성 화면은 실제 Qwen으로 RELATED(관련 커버리지 70.22%)를 확인했다. 기존/새 목표군의 사람 검수 데이터로 일치율·오탐·보류율을 비교하고 Kiwi 최초 초기화와 예열 로컬 지연을 분리해 측정한다. 같은 사례에 맞춰 임계값이나 기대 라벨을 바꾸지 않는다. 설치는 `evaluation/requirements-streamlit.txt`를 사용한다. 현재 OpenAI 미호출 표시는 Graph 없는 실험 경로 기준이며, 이후 호출 경로를 추가하면 실제 호출 경계에서 시간을 계측해야 한다.

- `goal-graph-v4.1`의 동일 개념 중심 별칭 지침은 실제 LLM 생성 품질·속도 평가가 남아 있다. 현재 Graph 없는 Streamlit의 목표 키워드 추출 개선과 별도이며, 이번 프롬프트 수정만으로 QueryDSL 불일치가 해결됐다고 간주하지 않는다.

- 2026-10-22 현재 Streamlit은 사용자 요청으로 Graph 없는 키워드+벡터 Hybrid 실험으로 전환했다. 목표 키워드·문단 점수·본문 커버리지·3라벨까지 연결했다. 다음은 실제 DOM/OCR와 사람 검수 정답을 이용해 가중치·관련/무관 문단 기준·커버리지를 개발 세트에서 조정하고, 고정한 기준으로 별도 평가 세트의 Macro F1·라벨별 정밀도/재현율·혼동행렬 및 최초/예열 지연을 측정하는 것이다. 특히 키워드가 없는 보조 학습을 UNRELATED로 오판하는지 확인한다. 아래 Graph 기록은 기존 분석기와 이전 실험 이력이다.

- 작업명: 임의 목표 Graph 품질·Hybrid 판정 기준 검토와 실제 입력·출력 계약 합의
- 담당 영역: AI
- 우선순위: 높음
- 상태: school-hybrid-v6 설명·코드 묶음 구현 및 실제 causal mask 단건 RELATED 확인. Streamlit 비교 화면과 전체 255 passed·8 subtests passed. 직전 버전의 합성 14건 5/14·20건 7/20은 새 버전 전체 평가가 아님. 짧은 별칭·무관 근거·독립 정확도 검증은 미완료

## 작업 목적

학교 임베딩 Client는 `/api/embed`만 호출한다. 사용자의 하향 요청으로 DIRECT 0.62·선수/보조 0.65·무관 0.75를 적용하고 Graph 생성 지침·캐시 버전을 보강했다. 기존 14건은 2/14에서 5/14로 개선됐지만 별도 합성 20건은 7/20에 그쳤고 무관 검출은 0건이었다. 다음 작업은 긴/문장형 별칭이 짧은 정식 용어·명사형·코드 표현을 놓치는 문제, 무관 설명의 활동 구체성, 설명/코드 본문 커버리지를 검토하는 것이다. 추가 조정에 이미 확인한 34건을 사용하면 개발용으로 취급하고 새 사람 검수 세트로 평가한다. 과거 생성 실패 원인은 미확정이며 재발 시 진단 코드로 구분한다. 공개 API·Server 계약은 담당 영역과 합의한다.

## 확인된 근거와 남은 범위

- 이전 Streamlit의 고정 기준 v1(키워드0.20/벡터0.80, 관련 H0.60·cosine0.55·커버리지0.60, 무관 H0.20·커버리지0.70·본문80자)이다. QueryDSL 실제 합성 평가 12건에서 5건의 관련 자료가 UNCERTAIN이었다. `조건에`·`따라` 같은 목표 표현, 용어 형태 차이와 관련 문단의 결합 점수 미달을 우선 검토한다. 커버리지 0은 문단 탈락의 결과이므로 커버리지 숫자부터 낮추지 않는다. 이번 분석에서는 기준·산식·라벨을 바꾸지 않았다. 향후 보정에 이 자료를 사용하면 개발 세트로 관리한다.

- 새 합성 TCP/UDP·로드 밸런싱·QueryDSL 1시간 관측 자료 3개(각 12건)는 Schema·전처리를 통과했다. Streamlit 목표 칸에 각 JSON의 `goal.text`를 넣고 데이터 칸에 파일 전체를 붙여 실행한다. 파일의 goal이 목표 입력 칸을 자동 교체하지 않는다. 기대 라벨은 실행 전 고정했으며 모델 결과로 정답을 수정하지 않는다. 같은 자료로 숫자를 조정하면 개발용으로 취급하고 독립 정확도에는 별도 자료를 사용한다. 5분 간격의 합성 관측을 실제 1시간 실행·지속 이탈 측정으로 해석하지 않는다.

- `goal-graph-v4` 간결한 영어 프롬프트를 동일 목표·독립 DOM/OCR 사례로 평가한다. JSON 예시와 역할 정의는 유지하고 OpenAI 입력의 `goalProfile`은 제외했다. 실제 본문으로 별칭·목표 범위를 확장하지 않는다. 입력 프롬프트 단어 수 감소를 생성 지연·최종 정확도 개선으로 보고하지 않으며 신규 Graph 생성·캐시 재사용 시간을 구분한다.

- 최신 [5목표·20건 평가](features/hybrid-relevance-performance-metrics.md#7-확정-목표-5개-추가-실측)는 기대 일치 13/20·Macro F1 0.4924다. Graph 생성·벡터 요청은 성공했으나 React의 별칭 매칭과 모든 요리 무관 근거가 탈락했다. 다음 보완 대상으로 기록하며 이번 요청에서는 매칭·점수 기준을 바꾸지 않았다. 무관 주제 다양화·독립 사람 검수·반복 속도 계측이 필요하고, 이전 4건과는 데이터가 달라 개선율로 비교하지 않는다.

- [전달용 Hybrid 성능지표](features/hybrid-relevance-performance-metrics.md)에 개발용 4건의 잠정 집계와 정식 평가 절차를 정리했다. 다음은 사람 검수 독립 DOM/OCR 자료로 3라벨 지표를 측정하고 Graph 생성·캐시 상태를 분리해 지연 P50/P95를 반복 측정하는 것이다. 현재 25% 일치율을 출시 정확도로 사용하지 않는다.

- 사용자 요청 1번인 목표 기반 별칭 생성 보강은 v3.1로 반영했다. 별도 검증 LLM이나 매칭/점수 변경 없이 실제 JWT를 다시 평가했으나 최종 UNCERTAIN을 유지했다. 두 번째 본문은 키워드가 잡히지만 H=0.5444로 미달하며 커버리지 49.6%다. 추가 변경은 별도 범위를 정한 뒤 독립 사례로 검증한다.

- 이전 Streamlit의 Graph Hybrid 6번 평가 기록은 보존했다. 현재 화면은 2026-10-22 Graph 없는 실험 경로이며, 기존 Graph 방식과 비교할 때 같은 고정 입력·모델·예열 상태를 사용하고 Graph 신규 생성 시간은 별도로 기록한다.

- [트랜스포머 디코더 가상 1시간 평가](features/transformer-decoder-1h-evaluation-REPORT.md): 실행 전에 기대 라벨을 고정한 합성 14건을 실제 학교 API로 분석했다. 전부 UNCERTAIN, 일치 2건·불일치 12건(14.29%)이었다. LLM 요청 후 최소 Graph로 폴백했고 관련 별칭은 목표 전체 문장 1개, 무관 주제는 0개였다. 14건 모두 별칭 일치 0으로 판정 근거를 만들 수 없었다. 임베딩 연결 성공을 임의 목표 분류 품질 검증으로 간주하지 않는다.
- 학교 Qwen `qwen3-embedding:4b-fp16` 실제 반환 벡터는 2560차원·유한값·정규화 상태였다. 최초 warm-up 및 반복 요청 시간은 REPORT의 2026-10-14 기록 참조.
- 임시 메모리 Adapter로 학교 `/health`·`/api/embed`를 호출해 개발용 합성 사례를 실행했다. 이는 표준 Client·공개 API 연동이나 모델 정확도 통과를 뜻하지 않는다.
- 2026-10-21 기본 Client와 real CLI의 모델 정보 조회를 제거했다. 실제 학교 호출에서 `/api/embed` 2회로 `dev-state-direct`의 `RELATED / GOAL_CONNECTED`가 반환됐고, 관련 로컬 테스트 112개가 통과했다. revision·정밀도는 서버를 재조회하지 않는 로컬 설정이므로 모델 교체 시 설정 갱신과 프로세스 재시작이 필요하다.
- 2026-10-14의 8개 합성 개발 사례에서는 실제 분석된 6개 중 기대 라벨 일치 3개, 불일치 3개였다. 기대 라벨은 독립 검수 전이므로 정확도 지표로 사용하지 않는다.
- 학교 GPU 사용률·VRAM, 독립 정답 세트 기반 정확도, 운영 부하 성능은 미측정이다.
- 기존 개인정보·실패·중복·stale 단위 테스트를 계속 통과시킨다.
- DOM·Chrome OCR·Desktop OCR 단건 입력 예제는 `AI/evaluation/demo-*.json`과 학교 SETUP 10.3.2절을 사용한다. 목표 JSON은 별도이며 모든 식별자·OCR confidence·본문은 직접 작성한 더미 값이다. Schema·전처리는 통과했으나 실제 추출본·독립 정답 데이터로 쓰지 않는다.
- 2026-10-21 위 세 입력을 Mac에서 실제 학교 API로 실행해 DOM은 UNCERTAIN/INSUFFICIENT_EVIDENCE, 두 OCR은 RELATED/GOAL_CONNECTED를 확인했다. 모두 SUCCEEDED였고 임베딩 요청 4회만 발생했다. 다음 정확도 검수에서 DOM의 근거 부족을 확인하되 이 한 사례에 맞춰 기준을 변경하지 않는다.


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
- 공개 관련성 경로 `/api/v1/sessions/{sessionId}/analysis-runs`와 내부 AI 경계 확인; `/api/v1/feedback`은 MVP에 없는 후속 계약으로 유지
- Chrome Extension 내부 OCR과 macOS Desktop Apple Vision OCR 경로를 분리 확인
- 목표 보조 4개 `clarityStatus`, 추천·질문·직접 입력 장애 대안 확인
- `ANALYSIS_ONLY`와 `ANALYSIS_AND_TIME`, `sessionStatus`, `resumeRequired` 계약 확인
- AI 내부 라벨과 제품 표시 라벨 매핑 확인
- 개인화 휴식의 최근 7일 규칙과 카메라 비필수 경로 확인
- ColPali는 기본 서버 구조에 바로 포함하지 않고 별도 PoC·평가로 분리
- 관련 기능의 PLAN·ERROR·REPORT 파일 준비


## 예상 작업 순서

1. 루트 `docs/API_CONTRACT.md`와 현재 sessions 분석 run 경로를 기준으로 확정 필드·상태를 대조한다.
2. 완료한 `/api/embed` 전용 Client를 사용해 사람 검수 데이터로 실제 평가를 실행한다. 자동 모델 메타데이터 조회는 추가하지 않는다.
3. 내부 Schema·Adapter의 필수값과 외부 계약 간 불일치를 안전하게 거부하고 원문 비노출을 확인한다.
4. 기본 Client·real CLI의 임베딩 전용 회귀 테스트와 실제 요청 경로 확인 결과를 유지한다.
5. 독립 검수 정답 세트와 학교 GPU 측정값을 준비해 개발·평가 split을 분리한 real 평가를 수행한다.

### Server·Client 공통 계약 작업


1. 공개 관련성 경로 `/api/v1/sessions/{sessionId}/analysis-runs`와 내부 AI 경계를 Server·Client 담당자와 확정한다. `/api/v1/feedback`은 MVP에 없는 후속 계약으로 유지한다.
2. AI 내부 라벨과 제품 표시 라벨, 기존 `FocusState` 호환 전략을 공유 타입 담당자와 확정한다.
3. `goalId`, `sessionId`, `clientRunId`, `sequence`, `contentSource`, `navigationId`와 `sessionStatus`, `analysisStatus`의 필수 범위와 `UNIQUE(sessionId, clientRunId)` 저장 제약을 확인한다.
4. Client 조정자가 Desktop foreground 앱과 Chrome foreground/active tab을 결합해 `isActiveTarget`을 만드는 주체·갱신 시점을 확정한다.
5. 목표 보조 `clarityStatus` 4개 값, 질문 최대 2회, 복수 목표 단일 선택 및 AI 장애 시 직접 목표 확정 경로를 확인한다.
6. FastAPI, Pydantic, pytest와 서비스 인증·설정 주입 방식을 확정한다.
7. Health Check, 공통 성공·실패 envelope, Schema 검증 테스트를 구현한다.
8. 로그에 원문·비밀 값이 남지 않는지 검사한다.
9. 현재 관련성 분석은 통합 #11에서 진행하고, Agent·노트·ColPali 등 범위 밖 작업은 별도 Issue로 관리한다.

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
7. AI 내부 `analyze_staged`와 Client의 foreground 선택·메타데이터/DOM/OCR 수집 모듈을 구현했다. 후속 3번에서 Client↔Server↔AI 실제 API를 연결한다. Server가 소유권·확정 목표·최신 상태를 결합하고, Client는 대응 결과를 `collectNext`로 넘겨 필요한 단계만 추가 수집한다. 재시작 시 출처별 sequence 복구·실패/UNCERTAIN 매핑도 이 경계에서 연결한다. 실제 화면·학교 모델 평가는 사용자가 보류했다.
8. 완료: 확인된 GoalProfile의 `mainTopic`·`coreTopics`·`supportingTopics`·`expectedActivities`와 확정 목표 문장을 버전 고정 Graph로 컴파일한다. 생성형 LLM Graph 후보는 Schema 검증 뒤 사용하고 미연결·실패 시 최소 Graph로 내려간다. 현재 페이지로 목표 범위를 확장하거나 embedding 모델에 aliases·역할을 생성시키지 않는다.
9. 완료: OpenAI 호환 `GoalGraphLLMClient.complete()` Adapter에 `.env` 기반 API Key·모델·timeout·제한 재시도와 응답 검증을 연결하고, 생성 Graph를 로컬 ChromaDB에 저장해 프로세스 재시작 때 재호출하지 않도록 했다. 남은 작업은 실제 API Key 기반 품질 검증, 외부 전송 고지 확정, Server 소유권·삭제 전파와 운영용 저장소 연결이다.

학교 연결·계약 정보가 확보되기 전에도 모델 없는 테스트와 fake 개발/평가 CLI는 로컬에서 실행할 수 있다. 공개 API·GPU·독립 성능 검증을 완료로 표시하지 않는다.

## 2026-10-11 제목 반영 이후 확인

- 완료: 제목·명시 Markdown 소제목의 Hybrid 매칭/충돌/근거 반영, 본문 커버리지 분리, 코드 언어·문자열 보존, 앱·도메인별 결과 캐시 분리. 모델 없는 테스트 165 passed, 6 subtests passed.
- 문서 정합성 반영 완료: Notion AI 최종 기획서 (2), 이재빈 전처리·관련성·유사도 6번 설명을 현재 로컬 구현과 맞추고 재조회 검증했다. 기존 입력·출력 JSON 예시와 미합의 공개 계약 경계를 유지했다.
- 학교 실측: 같은 본문에 관련/무관/중립 제목을 각각 붙인 사례, 제목 없는 OCR, 제목만 있는 입력, 코드 언어·fence 보존 사례를 실제 Qwen3 4B·FP16으로 비교한다. 변경된 보류율과 오탐을 검수하고 기존 임계값을 확정값으로 취급하지 않는다.
- 입력 연결: 김성현 측에서 실제 보내는 합성 JSON으로 `pageTitle`·`appName`·`urlHost`와 `text`의 구조 보존을 확인한다. `windowTitle`·포커스·별도 코드 언어/소제목 필드는 전달 위치를 합의한 뒤 반영한다.
- 학교에는 개정한 [단일 MD 지침](features/school-ollama-embedding-SETUP.md)을 전달한다. 실행 전 실제 코드가 `school-text-v2`·`school-hybrid-v4`인지 확인하고, 지침서의 학교 벡터 확인과 fake/real 평가 순서를 따른다. MD 전달만으로 최신 코드 전송이나 실제 모델 검증이 완료됐다고 표시하지 않는다.

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

## 2026-10-20 학교 전달 MD 보완 이후 다음 테스트

1. 개정된 [학교 전달 지침](features/school-ollama-embedding-SETUP.md)과 최신 구현 파일을 학교 PC에 함께 전달하고 Python 의존성·구현 버전을 확인한다.
2. 지침 10.4절대로 학교 PC에서 `FOCUSON_EMBEDDING_URL=http://127.0.0.1:11434`와 검증한 digest를 설정해 real 개발 split을 실행한다. 이 경로는 검증 FastAPI의 Client 계약 변경을 기다릴 필요가 없다.
3. 개발 세트의 사람 정답 검수·설정 조정을 마친 뒤 설정을 고정해 evaluation split을 평가한다. 현 16개는 고정 템플릿 콘텐츠 단계이며 메타데이터 전체 흐름·임의 목표 Graph 품질은 별도 검증한다.
4. Mac 원격 표준 평가는 Tailscale Serve의 `/health` 메타데이터와 기존 Client 검증 계약을 연결한 뒤 진행한다. 이 작업과 공개 API·Server 연동을 완료로 표시하지 않는다.
