# 트랜스포머 디코더 공부 — 가상 1시간 실제 모델 평가

> 2026-10-28: 이 문서의 Graph 생성·분석기·1~7방식 비교·relevance_eval 명령은 이전 실험 이력이다. 미사용 실행 코드를 제거했으므로 현재 실행 안내는 [AI README](../../README.md)를 따른다. 과거 입력·기대 라벨·평가 산출물은 이력 확인용으로 보존한다.

> 최신 단건 확인: school-hybrid-v6의 설명·코드 묶음 적용으로 causal mask 사례가 UNCERTAIN에서 RELATED로 바뀌었다. 전체 14/20건의 최신 전체 평가는 이전 버전 결과(5/14, 7/20)이며 아래 각 실행 기록을 구분한다.

- 확정 목표: 트랜스포머 디코더 공부
- 시나리오: 00분부터 60분까지 연속된 14개 활동 구간. 구간마다 관측 1건을 분석했다.
- 실행: Mac의 기존 RelevanceAnalyzer와 기본 OllamaEmbeddingClient → 학교 Qwen 임베딩 API.
- 입력·OCR confidence·세션 문맥은 직접 작성한 합성 데이터이며 실제 화면 수집본이 아니다.
- 기대 라벨은 실행 전에 작성해 JSON에 고정했다. 기대 라벨·시간 구간·활동 설명은 모델에 보내지 않았다.
- Graph에는 확정 목표만 전달했고 사례 내용·기대 라벨을 사용해 개념이나 무관 주제를 추가하지 않았다.
- 판정 임계값·가중치·텍스트·기대 라벨을 실행 결과에 맞춰 수정하지 않았다.

## 결과 요약

- 기대 라벨 일치: 2/14건 (14.29%).
- 불일치: 12건. 분석 실행 실패: 0건.
- 실제 벽시계 경과시간: 43.124초. 가상 1시간을 실제로 기다리지 않았다.
- 임베딩 API 요청: 14회. 임베딩 메타데이터 조회: 0회.
- 목표 Graph 준비: 4.464초. 방식: LLM 요청 후 최소 Graph 폴백. Graph LLM HTTP 요청: 1회.

## 활동별 기대 라벨과 실제 결과

| 가상 구간 | 화면 활동 | 입력 출처 | 기대 | 실제 | 비교 | 반환 이유 | 분석 시간 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 00–05분 | Transformer Decoder 구조 정리 | CHROME_DOM | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 10.612초 |
| 05–10분 | 미래 토큰을 가리는 causal mask | CHROME_VIEWPORT_OCR | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.316초 |
| 10–14분 | Scaled dot-product attention의 Q K V | CHROME_DOM | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 1.276초 |
| 14–18분 | 게임 랭크전 하이라이트 | CHROME_VIEWPORT_OCR | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.955초 |
| 18–24분 | 인코더 출력을 읽는 디코더 | DESKTOP_APP_OCR | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.691초 |
| 24–28분 | PyTorch에서 causal mask 연산 확인 | CHROME_DOM | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.219초 |
| 28–31분 | 트랜스포머 영화 예고편 감상 | DESKTOP_APP_OCR | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 5.540초 |
| 31–36분 | 자기회귀 생성에서 KV cache 재사용 | CHROME_DOM | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 4.396초 |
| 36–40분 | 겨울 옷 할인 상품 비교 | CHROME_VIEWPORT_OCR | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 6.061초 |
| 40–45분 | 다음 토큰 예측과 teacher forcing | CHROME_DOM | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 1.149초 |
| 45–49분 | 학습 자료와 게임 추천이 섞인 화면 | CHROME_VIEWPORT_OCR | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE | 0.585초 |
| 49–52분 | 자료를 여는 중 | DESKTOP_APP_OCR | UNCERTAIN | UNCERTAIN | **일치** | TOO_SHORT | 0.001초 |
| 52–56분 | 김치볶음밥 레시피 찾아보기 | CHROME_DOM | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.396초 |
| 56–60분 | 디코더 구성과 생성 과정 복습 | DESKTOP_APP_OCR | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.434초 |

## 사례별 판정 검토

### decoder-01-decoder-structure — 불일치

- 기대: RELATED. 디코더의 직접 구성 요소를 공부하는 화면이다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-02-masked-self-attention — 불일치

- 기대: RELATED. 디코더의 인과적 자기주의와 미래 토큰 차단을 설명한다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-03-attention-prerequisite — 불일치

- 기대: RELATED. 디코더 자기주의를 이해하는 데 필요한 선수 개념이다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-04-gaming-video — 불일치

- 기대: UNRELATED. 게임 플레이 영상과 공략을 보고 있으며 디코더 학습 내용이 없다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-05-cross-attention — 불일치

- 기대: RELATED. 원래 Transformer 디코더의 cross-attention을 공부한다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-06-causal-mask-code — 불일치

- 기대: RELATED. 디코더 attention의 미래 위치 마스킹을 구현·점검하는 코드이다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-07-transformers-movie — 불일치

- 기대: UNRELATED. 같은 단어가 등장하지만 영화 감상이며 신경망 디코더 학습이 아니다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-08-kv-cache — 불일치

- 기대: RELATED. 디코더 추론 과정과 attention 재계산을 이해하기 위한 보조 학습이다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-09-shopping — 불일치

- 기대: UNRELATED. 의류 쇼핑 화면이며 학습 목표와 관련된 내용이 없다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-10-next-token-training — 불일치

- 기대: RELATED. 디코더 학습 입력과 출력 분포·손실 계산을 설명한다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-11-mixed-screen — 일치

- 기대: UNCERTAIN. 학습 내용과 게임 내용이 함께 있어 현재 활동의 주제를 단정하기 어렵다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.

### decoder-12-loading-screen — 일치

- 기대: UNCERTAIN. 읽힌 글자가 로딩 안내뿐이라 관련·무관을 판단할 근거가 부족하다.
- 실제: UNCERTAIN / TOO_SHORT, 실행 상태 SKIPPED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.

### decoder-13-cooking — 불일치

- 기대: UNRELATED. 음식 조리법을 찾아보는 화면이며 디코더 학습과 무관하다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: CHROME_VIEWPORT_OCR.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

### decoder-14-decoder-review — 불일치

- 기대: RELATED. 처음의 학습 목표로 복귀해 디코더 핵심 개념을 정리한다.
- 실제: UNCERTAIN / INSUFFICIENT_EVIDENCE, 실행 상태 SUCCEEDED.
- 반환 근거 ID: 없음. 추가 수집 요청: 없음.
- 불일치 검토: 현재 키워드·코사인·결합 점수·커버리지 조건에서 충분한 판정 근거가 성립하지 않았다. 개별 코사인값은 수집하지 않았으며, 공통 별칭 매칭 실패는 아래 진단을 따른다.

## 혼동 행렬

| 기대 / 실제 | RELATED | UNRELATED | UNCERTAIN |
| --- | --- | --- | --- |
| RELATED | 0 | 0 | 8 |
| UNRELATED | 0 | 0 | 4 |
| UNCERTAIN | 0 | 0 | 2 |

## 설정과 Graph

- 임베딩 모델 설정: qwen3-embedding:4b-fp16, 2560차원.
- 설정 revision: 3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc. 정밀도 F16·revision은 로컬 설정이며 이번 실행에서 원격 조회하지 않았다.
- Graph 생성 모델 설정: gpt-6-luna. Graph 버전: confirmed-goal-profile-v1.
- 관련 개념 1개, 무관 대조 주제 0개.
- 분석기: school-hybrid-v4. 가중치·임계값은 기존 relevance_calibration.json을 그대로 사용했다.

## 판정이 전부 보류된 이유

- 저장된 실제 Graph에는 DIRECT 개념 1개만 있으며 aliases는 목표 문장 전체인 “트랜스포머 디코더 공부” 하나다. 무관 대조 주제는 0개다.
- 저장된 Graph와 기존 전처리·키워드 검색 코드를 다시 대조했을 때 14/14건 모두 관련 개념의 별칭 일치 수가 0이었다. 이 진단에는 모델을 다시 호출하지 않았다.
- 현재 관련 근거 조건은 keyword_score > 0을 요구한다. 또한 키워드 점수 0이면 코사인이 1이어도 결합 점수는 최대 0.65여서 DIRECT 임계값 0.72에 도달하지 못한다. 따라서 이번 최소 Graph로는 학습 사례의 관련 근거가 통과할 수 없었다.
- 무관 대조 주제가 없으므로 딴짓 사례의 양성 무관 근거도 만들어질 수 없다. 이는 임베딩 API 연결 성공과 별개의 문제다.
- mixed-screen의 UNCERTAIN은 기대 라벨과 문자열은 일치하지만, 반환 이유는 CONFLICTING_EVIDENCE가 아닌 INSUFFICIENT_EVIDENCE였다. 이 일치를 의도한 충돌 처리가 검증된 것으로 해석하지 않는다.
- LLM 생성 또는 검증의 어느 단계에서 실패했는지 세부 오류는 이번 실행에서 수집되지 않았다. 원인을 특정하지 않고 “LLM 요청 후 최소 Graph 폴백”으로 기록한다.
- 다음 작업은 기존 LLM Graph 생성·검증 경로의 실패 원인을 확인하는 것이다. 본문에 목표 문장 전체를 반복해서 넣거나 결과를 보고 기대 라벨을 바꾸는 방식으로 일치율을 높이지 않는다.

## 해석 범위

- 이 수치는 작성자가 정한 임시 기대 라벨에 대한 합성 사례 일치율이며 독립 사람이 검수한 실제 서비스 정확도가 아니다.
- 각 구간의 이미 추출된 DOM/OCR 관측 1건을 분석했다. DOM 결과가 추가 OCR을 요청한 경우에도 실제 캡처나 추가 OCR 관측을 자동 공급하지 않았다. 출처별 추가 요청은 사례별 검토와 원본 결과의 nextContentSource에 기록했다.
- 가상 구간은 시나리오 배치다. 관측 1건이 해당 구간 전체를 대표한다는 것을 검증하지 않았으며 집중시간·지속 이탈·알림 정책을 평가하지 않았다.
- Graph 준비시간과 사례별 분석 시간은 모델·네트워크·로컬 처리 등을 포함한다. 순수 GPU 시간과 네트워크 시간을 분리하지 않았다.
- Graph 생성 LLM 요청은 임베딩 메타데이터 조회와 별도다. Qwen은 벡터를 반환하고 로컬 Hybrid 코드가 라벨을 계산했다.
- SKIPPED 상태에서 근거 부족을 UNCERTAIN으로 반환하는 경로는 보류 동작으로 기록한다. FAILED·DISCARDED는 라벨 문자열이 같더라도 일치로 세지 않는다.
- 결과를 보고 기대 라벨을 바꾸거나 임계값을 조정하면 이 세트는 개발용으로만 사용하고 별도 평가 세트로 재검증해야 한다.

## 재현 자료

- [입력과 실행 전 기대 라벨](../../evaluation/transformer-decoder-1h-cases.json)
- [실제 결과·시간·요청 경로·설정](../../evaluation/transformer-decoder-1h-results.json)
- [실행에 사용한 목표 Graph](../../evaluation/transformer-decoder-1h-goal-graph.json)

## 후속: 목표 Graph 오류 진단 재실행

- 실행 시각: 2026-10-21 14:10 KST. 같은 확정 목표와 goalId/goalVersion을 사용하고 저장소·메모리 캐시 없이 생성 경로를 호출했다.
- `GOAL_GRAPH_FALLBACK` 경고에 고정 오류 코드, HTTP 상태, 허용 목록의 제공자 오류 코드/파라미터, Schema 검증 위치·규칙, JSON 오류 위치, 통신 실패 종류를 추가했다. 제공자 메시지·응답 원문·입력·API Key·traceback은 기록하지 않는다.
- 실제 생성 요청: `POST /v1/chat/completions` 1회, HTTP 200, 8.361초.
- 생성 및 Schema 검증 성공: 관련 개념 4개(DIRECT 1, PREREQUISITE 2, SUPPORTING 1), 무관 주제 2개. 최소 Graph 대체 없음, 오류 진단 이벤트 0개.
- 관련 개념: 트랜스포머 디코더, 디코더 self-attention, 디코더 cross-attention, 자기회귀 생성. 무관 주제: 제빵, 원예. 게임·쇼핑 등 모든 무관 활동을 포괄하는 Graph라는 뜻은 아니다.
- 과거 오류는 재현되지 않았다. 이전 실행의 HTTP 상태나 검증 오류가 보존되지 않았으므로 당시 실패 원인을 확정하거나 수정 완료라고 주장할 수 없다.
- 이번에는 Graph 생성만 실제 호출했다. 학교 임베딩 API와 14건 관련성 평가는 재호출하지 않았으므로 위의 기존 2/14 결과는 그대로 유지한다.
- 오류 진단·Graph 관련 테스트: 24 passed. HTTP 오류 정보와 검증 위치 확인, 비밀 값·거부된 데이터의 로그 비노출을 포함한다.
- [재실행 진단 및 생성 Graph JSON](../../evaluation/transformer-decoder-graph-diagnostic.json)
- 실행 전후 동일한 입력 SHA-256: ab7b41195c3d7e20d371951e9db2db87c861a15cda76e93fb8031473c7a99289

이번 Graph에는 무관 대조 주제가 없으므로, 현재 Hybrid 구조에서 양성 무관 근거를 만들 수 없다. 무관 사례가 UNCERTAIN으로 남는 경우 이 구조적 제한을 함께 검토해야 한다.

## 후속: 생성된 Graph 품질 검토 및 동일 14건 재평가

### 결론

**Schema 검증은 통과했지만 분류에 충분한 Graph 품질은 확보되지 않았다.** 이전에 성공 생성한 Graph를 수정 없이 재사용해 기존 분석기로 판정한 결과, 14건 모두 UNCERTAIN이었다. 기대 라벨 일치는 2/14건(14.29%)으로 최소 Graph 사용 때와 같았다. 이 수치는 합성 사례의 임시 기대 라벨 일치율이며 독립 검수된 운영 정확도가 아니다.

### Graph 내용 검토

| 검토 항목 | 확인 결과 | 평가 |
| --- | --- | --- |
| JSON·역할·ID·별칭 검증 | GeneratedGoalGraph 재검증 성공, 관련 4개·무관 2개, 목표 일치, 중복 별칭 없음 | 구조 정상 |
| 목표 직접 개념 | 트랜스포머 디코더 설명 및 한·영 별칭 | 목표에 부합 |
| self-attention / cross-attention | 디코더 내부 동작을 설명하지만 두 노드가 PREREQUISITE로 배정됨 | 목표의 핵심 구성 요소를 선수 지식과 구분하는 역할 기준 검토 필요 |
| 자기회귀 생성 | 다음 토큰 생성 설명과 한·영 별칭, SUPPORTING 배정 | 의미는 관련 있으나 역할 기준 검토 필요 |
| 관련 범위 | Q/K/V·FFN·잔차/정규화 등의 명시 노드가 없으며 causal mask·cross-attention 등 흔한 짧은 표현도 일부 별칭에 없음 | 광범위한 디코더 공부를 안정적으로 포괄한다고 볼 수 없음 |
| 무관 주제 | 제빵·원예만 생성 | 실제 다양한 이탈을 대표하지 못함. 게임·영화·쇼핑·김치볶음밥 조리 본문에서 무관 별칭이 검출되지 않음 |
| 문자 검색과의 적합성 | 한 노드의 별칭이 언어별 대체 표현으로 구성되어 한 문단에서 1개만 검출되거나 검출되지 않음 | 현재 2개 검출을 강하게 요구하는 점수 체계와 함께 검토해야 함 |
| 연결 구조 | connection은 목표와의 관계 설명 문장 | 기존 MVP 구조를 유지했으며 명시 간선 그래프 품질을 검증한 것은 아님 |

원래 Transformer의 디코더에는 마스킹된 self-attention, 인코더 출력을 읽는 attention, FFN 및 잔차/정규화가 포함된다. 따라서 이들 내부 구성 요소의 역할·범위를 검토해야 한다. 역할을 DIRECT로 분류해야 한다는 평가는 제품의 목표 범위 해석에 관한 검토 의견이며 논문이 제품 역할 enum을 정하는 것은 아니다. [Attention Is All You Need, 3.1](https://arxiv.org/html/1706.03762v7#S3.SS1)

### 재실행 조건과 시간

- 2026-10-21 실행. 생성 성공 당시의 Graph JSON을 그대로 로드하고 검증했으며 이번 Graph 생성 LLM 호출은 0회다. 생성 당시의 8.361초는 이번 판정 시간에 합산하지 않는다.
- Graph fingerprint: `d0f849019dfb9cbedf84763ef04add6378e8629aac429f9c3742f68a70835e9b`.
- 입력 데이터 SHA256: `ab7b41195c3d7e20d371951e9db2db87c861a15cda76e93fb8031473c7a99289`. 실행 전·후 동일하고 최초 평가의 해시와도 일치한다.
- 입력·기대 라벨·확정 목표·Graph·instruction·가중치·임계값을 수정하지 않았다. 현재 페이지 또는 기대 라벨을 Graph 생성에 입력하지 않았다.
- 기존 RelevanceAnalyzer와 기본 OllamaEmbeddingClient를 사용했다. 평가용 관찰 함수는 실제 decide_relevance 반환값을 변경하지 않고 문단별 점수·통과 조건만 수집했다.
- 실제 학교 Qwen API: POST /api/embed 14회, 텍스트 42개(개념 6개 + 전처리 문단 36개), 모든 응답 HTTP 200. 메타데이터 조회 0회. 설정 2560차원 검증과 정규화 유지.
- 이번 관련성 판정 총 경과시간: **30.697초**. 모델 내부 처리시간과 네트워크 시간을 분리 측정한 값은 아니다.
- 분석 SUCCEEDED 13건, 로딩 안내 1건은 TOO_SHORT로 SKIPPED. 요청 실패 0건. 모든 분석된 사례의 관련/무관 커버리지는 0이었다.
- 입력별 관측을 분석한 테스트다. DOM에서 요청한 추가 OCR을 자동 수집하지 않았고 실제 화면 캡처·Server 연결·지속 이탈 알림·실제 1시간 운영을 검증하지 않았다.

### 활동별 재평가 결과

| 시간 | 활동 | 기대 | 이전 실제 | 이번 실제 | 일치 여부 | 이유 | 시간 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 00–05분 | Transformer Decoder 구조 정리 | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 13.877초 |
| 05–10분 | 미래 토큰을 가리는 causal mask | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.753초 |
| 10–14분 | Scaled dot-product attention의 Q K V | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.730초 |
| 14–18분 | 게임 랭크전 하이라이트 | UNRELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.690초 |
| 18–24분 | 인코더 출력을 읽는 디코더 | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.480초 |
| 24–28분 | PyTorch에서 causal mask 연산 확인 | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 1.161초 |
| 28–31분 | 트랜스포머 영화 예고편 감상 | UNRELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.852초 |
| 31–36분 | 자기회귀 생성에서 KV cache 재사용 | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.133초 |
| 36–40분 | 겨울 옷 할인 상품 비교 | UNRELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 3.438초 |
| 40–45분 | 다음 토큰 예측과 teacher forcing | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 1.011초 |
| 45–49분 | 학습 자료와 게임 추천이 섞인 화면 | UNCERTAIN | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE | 1.160초 |
| 49–52분 | 자료를 여는 중 | UNCERTAIN | UNCERTAIN | UNCERTAIN | **일치** | TOO_SHORT | 0.000초 |
| 52–56분 | 김치볶음밥 레시피 찾아보기 | UNRELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 2.027초 |
| 56–60분 | 디코더 구성과 생성 과정 복습 | RELATED | UNCERTAIN | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE | 0.381초 |

### 본문에서 가장 높은 관련 결합 점수와 탈락 원인

아래는 제목·소제목을 제외한 문단-개념 쌍 중 관련 결합 점수가 가장 높은 항목이다. 코사인 최고 항목과 항상 같은 것은 아니며, 점수는 4자리로 반올림했다. 전체 216개 문단-개념 비교 중 통과한 쌍은 0개였다.

| 사례 | 개념 | 키워드 | 코사인 | 결합 점수 | 해당 역할 기준 | 발견 별칭 | 탈락 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| decoder-01-decoder-structure | transformer_decoder (DIRECT) | 0.5 | 0.7228 | 0.6448 | 0.72 | Transformer decoder | HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-02-masked-self-attention | decoder_self_attention (PREREQUISITE) | 0.5 | 0.7622 | 0.6704 | 0.76 | masked self-attention | HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-03-attention-prerequisite | decoder_cross_attention (PREREQUISITE) | 0.0 | 0.4906 | 0.3189 | 0.76 | 없음 | NO_RELATED_ALIAS, COSINE_BELOW_MINIMUM, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-04-gaming-video | autoregressive_generation (SUPPORTING) | 0.0 | 0.2412 | 0.1568 | 0.76 | 없음 | NO_RELATED_ALIAS, COSINE_BELOW_MINIMUM, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-05-cross-attention | decoder_cross_attention (PREREQUISITE) | 0.0 | 0.7476 | 0.4860 | 0.76 | 없음 | NO_RELATED_ALIAS, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-06-causal-mask-code | decoder_self_attention (PREREQUISITE) | 0.5 | 0.7594 | 0.6686 | 0.76 | masked self-attention | HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-07-transformers-movie | decoder_self_attention (PREREQUISITE) | 0.0 | 0.2875 | 0.1869 | 0.76 | 없음 | NO_RELATED_ALIAS, COSINE_BELOW_MINIMUM, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-08-kv-cache | autoregressive_generation (SUPPORTING) | 0.5 | 0.6284 | 0.5835 | 0.76 | autoregressive decoding | HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-09-shopping | decoder_cross_attention (PREREQUISITE) | 0.0 | 0.1796 | 0.1167 | 0.76 | 없음 | NO_RELATED_ALIAS, COSINE_BELOW_MINIMUM, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-10-next-token-training | autoregressive_generation (SUPPORTING) | 0.0 | 0.6279 | 0.4082 | 0.76 | 없음 | NO_RELATED_ALIAS, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-11-mixed-screen | decoder_self_attention (PREREQUISITE) | 0.5 | 0.7934 | 0.6907 | 0.76 | masked self-attention | HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-12-loading-screen | 전처리에서 TOO_SHORT | — | — | — | — | — | 분석 생략 |
| decoder-13-cooking | autoregressive_generation (SUPPORTING) | 0.0 | 0.1944 | 0.1264 | 0.76 | 없음 | NO_RELATED_ALIAS, COSINE_BELOW_MINIMUM, HYBRID_BELOW_ROLE_THRESHOLD |
| decoder-14-decoder-review | decoder_self_attention (PREREQUISITE) | 0.5 | 0.7743 | 0.6783 | 0.76 | masked self-attention | HYBRID_BELOW_ROLE_THRESHOLD |

### 구체적 원인

1. **별칭 1개와 현재 점수 기준의 조합:** masked self-attention 학습 사례는 키워드 0.5, 코사인 0.7622로 결합 점수가 `0.35 × 0.5 + 0.65 × 0.7622 ≈ 0.6704`였다. PREREQUISITE 기준 0.76에 미달했다. DIRECT로 역할만 바꿔도 0.72 미만이므로 역할 변경만으로 해결되지 않는다.
2. **의미가 가까워도 별칭 미검출:** cross-attention 학습 본문의 코사인은 0.7476이지만 해당 표현이 생성된 별칭과 정확히 맞지 않아 키워드 0, 결합 0.4860이었다. 별칭이 없으면 관련 근거로 인정하지 않는 별도 조건도 걸린다.
3. **무관 주제의 좁은 범위:** 네 가지 딴짓 사례 모두 제빵·원예 별칭과 일치하지 않아 무관 근거를 만들지 못했다. 제빵의 description이 조리 일반의 의미를 약간 담더라도 김치볶음밥 레시피를 그 별칭으로 포괄하지는 못한다. 낮은 관련 유사도만으로 UNRELATED를 만드는 정책은 현재 코드에 없다.
4. **제목과 본문은 다른 근거:** 다음 토큰 학습 사례의 제목에서 `다음 토큰 예측`이 발견됐지만 본문에서는 발견되지 않았다. 제목은 커버리지 분모·분자에 포함되지 않으며 제목 쌍도 점수 기준에 미달했다.
5. **일치 2건의 한계:** 혼합 화면은 기대 UNCERTAIN과 같았지만 실제 이유는 CONFLICTING_EVIDENCE가 아닌 INSUFFICIENT_EVIDENCE였다. 관련·무관 충돌 처리의 성공으로 볼 수 없다. 로딩 화면의 TOO_SHORT는 의도한 보류와 일치했다.

별칭 1개(키워드 0.5)일 때 DIRECT 기준을 통과하려면 코사인이 약 0.8385 이상, PREREQUISITE/SUPPORTING이면 0.9000 이상이어야 한다. 이번 실측은 생성 별칭과 현재 실험 기준이 함께 정상 학습을 보류시키는 사례를 보여준다. 임계값이 보편적으로 틀렸다고 단정하거나 같은 14건에 맞춰 낮추지는 않는다.

### 다음 보완 순서

1. 목표 Graph의 의미 검수 기준을 정한다. 직접 구성 요소/선수 지식의 역할, 핵심 범위, 문자 검색에 쓸 정식 용어·표현 변형, 무관 주제 선정 범위를 검토한다. JSON 검증 성공만으로 품질 승인하지 않는다.
2. 생성 별칭을 문단에서 몇 개나 만나는지에 맞춰 키워드 점수·결합식·조건을 함께 검증한다. 이번 결과를 보고 본문에 목표 문구를 추가하거나 기대 라벨을 바꾸지 않는다.
3. 조정은 개발 세트에서 진행하고 별도의 사람 검수 평가 세트로 학습/이탈/혼합/추출 실패를 확인한다. 현재 14건은 이미 진단에 사용했으므로 독립 최종 정확도 증거로 취급하지 않는다.
4. 이번 작업은 검토·실제 재평가와 기록만 수행했다. Graph 생성 Prompt·판정 조건·제품 계약은 변경하지 않았다.

- [고정 Graph로 실행한 재평가 원본 및 문단별 점수](../../evaluation/transformer-decoder-1h-generated-graph-results.json)
- [사용한 생성 Graph와 생성 진단 기록](../../evaluation/transformer-decoder-graph-diagnostic.json)

## 후속: Graph 지침 v2 및 임계값 하향 비교

### 적용한 변경

- 사용자 요청에 따라 Graph 생성 지침을 goal-graph-v2로 보강했다. 목표의 핵심 구성 요소는 DIRECT, 별도 선행 지식은 PREREQUISITE, 점검·도구는 SUPPORTING으로 구분하고, 정식 용어·약어·한/영 표현·짧은 도메인 표현과 일반적인 목표 밖 활동 범주를 요청한다.
- 생성기 fingerprint는 제공자 fingerprint·지침 버전·실제 지침 문자열을 포함한다. 기존 생성 Graph의 메모리/Chroma 재사용과 그에 연결된 관련성 캐시를 분리한다. JSON 컬럼은 추가하지 않았다.
- 임계값은 실행 전에 한 번 선택했다. 결과를 보고 재조정하거나 기대 라벨·입력을 수정하지 않았다. 생성 지침은 모델 출력 품질 보장을 대신하지 않는다.

| 기준 | 이전 | 이번 실험값 |
| --- | --- | --- |
| DIRECT | 0.72 | 0.62 |
| PREREQUISITE / SUPPORTING | 0.76 | 0.65 |
| 양성 무관 근거 | 0.80 | 0.75 |
| 최소 코사인 | 0.55 | 0.55 유지 |
| 키워드/벡터 비율 | 0.35/0.65 | 유지 |
| 관련/무관 커버리지 | 0.60/0.70 | 유지 |

키워드 점수 0/0.5/1, 관련 별칭 필수·무관 별칭 2개 이상, 충돌 보류·품질/민감/실패 처리는 유지했다. 새 버전은 school-hybrid-v5 / school-experimental-v2다. 노션 관련성 계획의 숫자 조정 방침에 따른 내부 실험이며 운영 승인값은 아니다. [관련성 계획](https://app.notion.com/p/3e1357537c31807ba146ebda318f025a)에서 확인한 마지막 수정 표시는 2026-10-10이었다.

### 생성된 Graph 품질

| 목표 | 관련 노드·역할 | 무관 활동 | 검토 |
| --- | --- | --- |
| 트랜스포머 디코더 공부 | 9개: {'DIRECT': 5, 'PREREQUISITE': 3, 'SUPPORTING': 1} | casual_gaming, movie_entertainment, shopping, cooking | Schema 통과, 노드/별칭 중복 없음 |
| SQL JOIN과 GROUP BY 이해하기 | 6개: {'DIRECT': 3, 'PREREQUISITE': 2, 'SUPPORTING': 1} | entertainment-viewing, casual-gaming, shopping, cooking | Schema 통과, 노드/별칭 중복 없음 |

- 디코더의 self-attention·cross-attention·자기회귀 생성이 DIRECT로 정리되고 블록·선수 attention·토큰 임베딩·위치 표현·코드 점검이 추가됐다. 기존 4개보다 역할과 핵심 범위는 개선됐다.
- 디코더의 무관 범주는 게임·영상 오락·쇼핑·요리로 바뀌었다. 그러나 aliases는 `온라인 쇼핑`, `장바구니 쇼핑`, `레시피 조리`처럼 긴 표현이 많아 실제 본문의 `쇼핑몰`, `장바구니`, `레시피`, `조리`를 자주 놓쳤다.
- SQL에서도 aliases에 `SQL GROUP BY`, `GROUP BY 절`은 있지만 `GROUP BY` 자체가 없었다. 지침을 보강해도 canonical term 품질이 자동으로 보장되지 않는 실측 사례다.
- 일부 무관 description에 목표와 무관하다는 일반 설명이 섞였다. 다음 검토에서는 의미 임베딩용 description에 실제 활동을 구체적으로 담고 목표 연결 설명은 connection으로 분리하는 품질 기준을 검토한다.
- 판정 품질은 노드 수만으로 평가하지 않는다. 이번 결과는 생성 지침 개선의 부분 효과와 남은 문자 표현 문제를 보여준다.

### 고정 데이터와 비교 방법

- 기존 14건은 이미 진단에 사용한 개발용 사례다. 별도 합성 20건은 두 목표(디코더 10건·SQL 10건)로 작성하고 모델 실행 전에 기대 라벨을 고정했다. 모두 같은 작성자가 부여한 임시 정답이며 독립 사람 검수는 하지 않았다.
- Graph 생성에는 확정 목표만 보냈다. 현재 화면·기대 라벨·검증 사례는 보내지 않았다. 디코더는 개발/검증 세트에서 동일한 확정 목표 ID·Graph를 사용했다.
- 목표별로 생성한 Graph를 고정하고 기존 분석기를 두 번 실행했다. 이전 임계값과 하향 임계값은 같은 실제 Qwen 벡터를 공유했다. 벡터 재사용 캐시는 텍스트 해시와 벡터만 메모리에 보관했으며 원문·벡터를 결과 파일에 저장하지 않았다.
- 새 20건도 이번에 결과를 확인했으므로 다음 추가 튜닝에서 재사용하면 더 이상 새로운 검증 세트가 아니다. 최종 평가는 별도 목표·콘텐츠·사람 정답으로 수행해야 한다.

### 결과 비교

| 데이터 | 이전 생성 지침·이전 기준 | 새 지침·이전 기준 | 새 지침·하향 기준 |
| --- | --- | --- | --- |
| 기존 14건 | 2/14 (14.29%) | 3/14 (21.43%) | 5/14 (35.71%) |
| 별도 합성 20건 | 측정하지 않음 | 5/20 (25.00%) | 7/20 (35.00%) |

- 하향 기준에서 기존 14건은 RELATED 3·UNCERTAIN 11, 검증 20건은 RELATED 2·UNCERTAIN 18이었다. **UNRELATED 반환은 두 세트 모두 0건**이다.
- 기존 세트의 실제 학습 8건 중 3건, 검증 세트의 실제 학습 9건 중 2건을 RELATED로 반환했다. 무관 10건을 RELATED로 오판한 사례와 관련 17건을 UNRELATED로 오판한 사례는 없었으나, 이는 과도한 보류가 많은 작은 합성 세트 결과이며 운영 안전성 보장은 아니다.
- UNCERTAIN 기대 사례는 총 7건 모두 라벨이 같았다. 혼합 화면은 대부분 INSUFFICIENT_EVIDENCE여서 관련·무관 충돌 검출 성공으로 해석하지 않는다.

### 기준 하향으로 달라진 사례

| 사례 | 기대 | 새 Graph·이전 기준 | 새 Graph·하향 기준 |
| --- | --- | --- | --- |
| decoder-05-cross-attention | RELATED | UNCERTAIN | RELATED |
| decoder-14-decoder-review | RELATED | UNCERTAIN | RELATED |
| decoder-val-normalization | RELATED | UNCERTAIN | RELATED |
| sql-val-keys | RELATED | UNCERTAIN | RELATED |

### 남은 원인의 실제 예

- 디코더 구조 본문의 일부는 결합 점수 0.655로 직접 기준을 통과했다. 그러나 관련 본문 커버리지가 **0.5890**이어서 유지한 0.60 기준에 미달했다.
- causal mask 코드 사례는 설명 문단의 결합 점수 0.850으로 통과했지만 관련 커버리지가 **0.3650**에 그쳤다. 긴 코드 문단이 연결되지 않은 상태에서 설명 하나만으로 전체를 관련으로 만들지 않았다.
- SQL GROUP BY 본문은 코사인 0.583이었지만 bare `GROUP BY`와 생성 별칭이 맞지 않아 해당 노드 키워드 0, 결합 0.379였다.
- SQL 요리 사례는 코사인 0.636이었지만 `요리하기`, `음식 조리`, `레시피 따라하기`라는 별칭이 실제 본문 표현에 맞지 않아 키워드 0, 결합 0.413이었다. 임계값만 더 낮추어도 별칭 게이트 때문에 해결되지 않는다.
- 다음 작업은 짧은 정식 용어·명사형 표기·코드 표현을 포함하는 별칭 품질과 설명/코드 본문 대표성을 검토하는 것이다. 이번 결과에 맞춰 커버리지를 0.589 이하로 내리거나 정답 문구를 본문에 주입하지 않는다.

### 실제 호출·시간·테스트

- Graph 생성: 요청 2회, 모두 HTTP 200. 디코더 13.092초, SQL 7.912초, 합계 21.004초.
- 임베딩 요청 시도 38회. HTTP 응답 이벤트 36회는 모두 200이고, 응답 이벤트가 남지 않은 시도 2회 뒤 재시도했다. 해당 두 시도의 timeout/transport 상세는 이번 도구에서 수집하지 않았다. 최종 분석 실패는 0건이다.
- 입력 배열 항목 합계 115개는 재시도 중복을 포함한다. 고유 벡터 수나 모두 실제 처리 완료된 텍스트 수로 해석하지 않는다. 2560차원·유한값·정규화 검사는 기본 Client가 수행했다. 메타데이터 조회는 0회다.
- 임계값 두 조건의 전체 비교 시간 627.842초. Graph 생성 포함 약 648.846초. 같은 벡터 캐시를 공유했으므로 시나리오별 시간 비교는 하지 않는다. 모델 내부 시간·순수 네트워크 시간을 분리한 값도 아니다.
- 입력 34건 중 최종 상태 SUCCEEDED 32·SKIPPED 2. 원래 로딩 화면은 TOO_SHORT, 검증의 낮은 OCR은 LOW_CONFIDENCE로 생략했다. SQL 로딩 안내는 SUCCEEDED/INSUFFICIENT_EVIDENCE로 보류했다.
- 로컬 전체 테스트 **219 passed, 8 subtests passed**. 한 개 별칭+의미 근거의 통과, 약한 근거·무관 별칭 1개·충돌 보류 유지, Prompt 변경의 메모리/저장 Graph 캐시 분리, 실행/평가 설정 일치를 포함한다.
- DOM/OCR 원본 추출·Server 계약·실제 알림·독립 운영 정확도·GPU 자원은 이번 검증 범위가 아니다.

### 전체 사례별 최신 일치/불일치

#### 기존 개발 사례 14건

| 사례 | 기대 | 실제 | 비교 | 이유 |
| --- | --- | --- | --- | --- |
| decoder-01-decoder-structure | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-02-masked-self-attention | RELATED | RELATED | **일치** | GOAL_CONNECTED |
| decoder-03-attention-prerequisite | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-04-gaming-video | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-05-cross-attention | RELATED | RELATED | **일치** | GOAL_CONNECTED |
| decoder-06-causal-mask-code | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-07-transformers-movie | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-08-kv-cache | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-09-shopping | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-10-next-token-training | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-11-mixed-screen | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE |
| decoder-12-loading-screen | UNCERTAIN | UNCERTAIN | **일치** | TOO_SHORT |
| decoder-13-cooking | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-14-decoder-review | RELATED | RELATED | **일치** | GOAL_CONNECTED |

#### 별도 합성 검증 사례 20건

| 사례 | 기대 | 실제 | 비교 | 이유 |
| --- | --- | --- | --- | --- |
| decoder-val-shift | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-normalization | RELATED | RELATED | **일치** | GOAL_CONNECTED |
| decoder-val-attention-math | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-generation | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-video-codec | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-game | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-shopping | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-unknown | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-mixed | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE |
| decoder-val-low-ocr | UNCERTAIN | UNCERTAIN | **일치** | LOW_CONFIDENCE |
| sql-val-inner-join | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-group-having | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-aggregate | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-left-join-null | RELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-keys | RELATED | RELATED | **일치** | GOAL_CONNECTED |
| sql-val-sport | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-cooking | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |
| sql-val-loading | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE |
| sql-val-mixed | UNCERTAIN | UNCERTAIN | **일치** | INSUFFICIENT_EVIDENCE |
| sql-val-social | UNRELATED | UNCERTAIN | **불일치** | INSUFFICIENT_EVIDENCE |

### 산출물

- [실제 Graph·이전/하향 기준 결과·문단별 점수](../../evaluation/graph-quality-v2-results.json)
- [실행 전에 고정한 별도 합성 20건](../../evaluation/graph-quality-validation-cases.json)
- [학교 전달 지침 — 최신 버전·기준 반영](school-ollama-embedding-SETUP.md)


## 후속: 설명·코드 묶음 구현 및 단건 실제 검증

- 구현 버전: school-hybrid-v6. 기존 숫자 기준·목표 Graph·원래 전처리 문단·JSON 계약은 유지했다.
- 같은 입력의 인접 BODY/OCR 설명과 명시 CODE 블록을 묶는다. 소제목/표가 전처리에서 제거돼도 넘어 묶지 않도록 원문 인접 관계를 확인한다. 단독 코드나 표식 없는 평문을 임의로 묶지 않는다.
- 묶음은 원래 설명 문자열 + 줄바꿈 2개 + 코드 문자열이다. 같은 문자열로 키워드와 임베딩 점수를 계산한다. 상한은 8,000 UTF-8 바이트와 Client 제한 중 작은 값이며 초과하면 원래 단위만 평가한다.
- 개별 문단 분석도 유지해 강한 반대 근거를 놓치지 않는다. 묶음 통과 근거는 원본 ID로 환산하고 커버리지에서 원본 길이를 중복 계산하지 않는다. 분석 결과 캐시는 버전으로 분리한다.

### 같은 실제 벡터로 비교한 causal mask 사례

| 항목 | 묶음 없이 | 묶음 적용 |
| --- | --- | --- |
| 최종 라벨 | UNCERTAIN | RELATED |
| 이유 | INSUFFICIENT_EVIDENCE | GOAL_CONNECTED |
| 관련 근거 | 설명 passage-003 | 설명 passage-003 + 코드 passage-004 |
| 관련 본문 길이 | 100자 | 274자 |
| 전체 본문 길이 | 274자 | 274자 |
| 관련 커버리지 | 36.5% | 100% |

- 묶음 입력은 276자·342 UTF-8 바이트다. 구분자 줄바꿈 2자는 커버리지 길이에 포함하지 않는다.
- masked_self_attention과의 묶음 키워드 점수 1.0·코사인 0.7421·결합 점수 0.8323으로 DIRECT 기준을 통과했다.
- 기존 분석기에서 실제 학교 API를 호출했다. 개념 13개, 콘텐츠 원본 4개+묶음 1개를 각각 한 배치로 보내 POST /api/embed 2회 모두 HTTP 200이었다. 전체 경과시간 7.583초. Graph 생성 및 메타데이터 호출은 없었다.
- 비교 시 같은 벡터·Graph·숫자 조건으로 문맥 근거만 포함/제외했다. 새 버전으로 전체 14/20건을 재평가한 것은 아니며 단건 성공을 전체 정확도로 표현하지 않는다.
- 전체 로컬 회귀: 235 passed, 8 subtests passed. 관련 설명+관련 코드·개별 반대 근거·묶음 자체 미통과·중복 커버리지·소제목/표 경계·삭제된 중복 소제목·UTF-8 크기·단독 코드·배치 전달을 확인했다.
- [실제 벡터 기반 비교 및 점수 JSON](../../evaluation/code-context-grouping-smoke.json)

### 별칭 생성 기준 제안 — 이번 구현에서 Prompt 변경하지 않음

- 노드의 정식 표현을 우선한다. 예: GROUP BY 자체를 넣고 GROUP BY 절이라는 긴 표현만 넣지 않는다.
- 같은 개념의 자연스러운 한/영 표현·약어·실제 코드 표기를 선택한다. causal mask / causal_mask / 인과 마스크 / 미래 토큰 마스킹이 예다.
- 무관 범주는 확정 목표 밖일 때만 선택하고 실제 본문에서 함께 나타날 수 있는 짧은 활동 용어를 쓴다. 요리 / 레시피 / 조리 / 식재료 같은 표기를 검토한다. 모든 목표에서 요리를 무관으로 고정하지 않는다.
- 목표 문구를 구성 단어로 잘라 별칭 수를 채우거나, 같은 단어의 접두·접미 확장을 나열해 한 번의 언급을 여러 개로 부풀리지 않는다. 대소문자만 다른 중복도 추가하지 않는다.
- 생성 후 형식·중복 검사 외에 정식 기본형·불필요한 수식어·문장형 표현을 검수하는 단계가 필요하다. 일부 사례를 본 뒤 그 표현만 넣는 방식과 독립 정확도 검증은 구분한다.
