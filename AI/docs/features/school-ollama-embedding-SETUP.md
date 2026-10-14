# FocusOn 학교 전달 지침 — Qwen3 임베딩·입출력·Hybrid 6번

- 개정일: 2026-10-14
- 대상: 학교 RTX A4000·RAM 128GB 컴퓨터에서 테스트하는 개발 담당자 또는 코딩 AI
- 모델: Qwen/Qwen3-Embedding-4B·FP16, 초기 Ollama 태그 `qwen3-embedding:4b-fp16`
- 현재 코드 기준: `feat/11-ai-preprocessing-relevance`, 전처리 `school-text-v2`, 분석기 `school-hybrid-v2`
- 원본: 이번 대화에서 수정한 노션 4개 문서를 다시 조회하고 현재 코드와 대조했다. 출처는 14절에 있다.
- 이 파일은 개발·테스트 지침이다. 임베딩 모델의 `input`에 이 MD 전체를 넣지 않는다.

## 1. 이번 개정에서 반드시 반영할 내용

| 항목 | 현재 기준 |
| --- | --- |
| 비교 대상 | 제목·명시된 소제목·본문·코드 각각을 기존 Graph 개념과 비교 |
| 개념 ID | 기존 목표 템플릿의 개념 ID 유지. 제목·소제목을 새 개념 노드로 추가하지 않음 |
| 제목 입력 | 기존 `pageTitle` 사용 |
| 소제목 입력 | 기존 `text`에 명시된 Markdown h1~h3만 내부 `HEADING`으로 구분 |
| 코드 정보 | `text` 안의 코드 fence·언어·설명·들여쓰기·문자열 보존 |
| 근거 충돌 | 전체 통과 근거에 관련과 무관이 함께 있으면 `UNCERTAIN` |
| 커버리지 | 실제 분석한 BODY·OCR·CODE·TABLE 기준. TITLE·HEADING 제외 |
| 결과 근거 | 본문으로 판정이 성립하면 같은 방향의 제목·소제목 ID도 함께 반환 |
| 앱·도메인 | 기존 `appName`·`urlHost`로 개인정보 검사·결과 캐시 구분 |
| 공개 컬럼 | 새 입력·출력 컬럼 추가 없음. `HEADING`은 내부 종류값 |
| 숫자 정책 | 기존 결합 비율·임계값 유지. 아래 숫자는 미보정 실험값 |

구 지침의 “제목 충돌 미구현”, “CODE의 HTML 문자 변환 수정 필요” 설명은 이번 코드에서는 해결됐다. 학교에 있는 코드가 위 두 버전인지 먼저 확인한다. Branch 이름이 같아도 최신 로컬 변경이 학교에 전달됐다는 뜻은 아니다.

## 2. 실행 위치와 역할

| 위치 | 처리 |
| --- | --- |
| Client·Server | 활성 대상의 허용된 DOM/OCR 텍스트 전달, 인증·소유권·확정 목표·현재 문맥 확인 |
| Mac의 FocusOn AI 코드 | 검증·전처리·Graph·키워드·코사인·Hybrid·커버리지·최종 라벨 |
| 학교 Windows의 Ollama | 전달받은 목표 개념 질의와 정제 텍스트를 벡터로 변환 |
| 후속 정책 계층 | 체류·최근 흐름을 통한 지속 이탈·알림 판단 |

```text
Client 추출 JSON + 별도 확정 목표 + Server 검증 문맥
→ Mac: 상태·개인정보·품질 검사, 제목/소제목/본문/코드 정제
→ Mac: 목표 템플릿의 개념 목록·aliases·role 준비
→ 학교 Ollama: 개념 질의 벡터 + 각 정제 단위 벡터
→ Mac: 각 단위 문자열과 aliases 비교 → 키워드 점수
→ Mac: 각 단위 벡터와 개념 벡터 비교 → 코사인 유사도
→ Mac: 개념 × 단위별 Hybrid 점수·역할별 근거 조건 확인
→ Mac: 전체 근거 충돌 → 본문 커버리지 → 최종 관련성
```

키워드 계산은 임베딩 결과에 의존하지 않는다. 현재 코드의 실행 순서는 벡터 확보 후 키워드·코사인을 함께 계산하지만, aliases는 정제 문자열과 비교한다. 벡터 두 개를 합쳐 새 벡터를 만드는 방식이 아니다.

학교에 같은 Python 코드를 복사하면 CPU에서 후처리까지 학교 로컬로 검사할 수 있다. 그 경우 API 주소는 학교 localhost를 사용한다. 기본 운영 분담은 Mac의 후처리와 학교의 임베딩 API다. 추후 WSL2·Linux·vLLM 전환은 별도 단계다.

## 3. 현재 내부 입력 JSON

### 3.1 DOM 추출 결과

기존 필드만 사용한 합성 예시다. 문단은 빈 줄로 구분하고 소제목과 코드 언어는 `text` 안에 보존한다.

```json
{
  "sessionId": "session-001",
  "runId": "run-001",
  "goalId": "goal-react-001",
  "goalVersion": 1,
  "eventId": "event-dom-001",
  "navigationId": "navigation-001",
  "observedAt": "2026-10-11T01:00:00Z",
  "sessionStatus": "ACTIVE",
  "exclusionMode": "NONE",
  "contentSource": "CHROME_DOM",
  "contentType": "DOCUMENT",
  "appName": "Google Chrome",
  "pageTitle": "useState 객체 상태 업데이트",
  "urlHost": "react.dev",
  "text": "## useState 객체 상태\n\nuseState로 객체 상태 업데이트를 수행할 때 이전 객체를 직접 수정하지 않고 상태 변경 함수에 새 객체를 전달합니다.\n\n```javascript title=\"state.js\"\nsetState({...prev, count: 1});\nconst example = \"&lt;\";\n```",
  "extractionStatus": "SUCCESS",
  "isSensitive": false,
  "isExcluded": false
}
```

`text`의 JSON 문자열 안에서 `\n`은 줄바꿈이다. Markdown의 h1·h2·h3는 각각 `#`, `##`, `###` 뒤에 공백을 둔다. 코드 fence의 언어·설명은 기존 문자열의 일부다. 추출기가 평문만 전달했다면 AI가 소제목·언어를 임의로 붙이지 않는다. CODE·TABLE·ERROR 유형은 해당 구조를 우선 유지한다.

### 3.2 OCR 추출 결과

제목 없는 충분한 OCR 본문도 사용할 수 있다. 아래 출처는 콘텐츠를 추출한 Client를 뜻한다. 학교 Windows에서 모델을 실행한다는 이유로 출처를 바꾸지 않는다.

```json
{
  "sessionId": "session-001",
  "runId": "run-001",
  "goalId": "goal-react-001",
  "goalVersion": 1,
  "eventId": "event-ocr-001",
  "navigationId": "navigation-001",
  "observedAt": "2026-10-11T01:00:00Z",
  "sessionStatus": "ACTIVE",
  "exclusionMode": "NONE",
  "contentSource": "DESKTOP_APP_OCR",
  "contentType": "DOCUMENT",
  "appName": "Preview",
  "pageTitle": "",
  "urlHost": "",
  "text": "useState로 객체 상태 업데이트를 수행할 때 이전 객체를 직접 수정하지 않고 상태 변경 함수에 새 객체를 전달합니다.",
  "extractionStatus": "SUCCESS",
  "isSensitive": false,
  "isExcluded": false,
  "ocr": {
    "confidence": 0.91,
    "captureStatus": "SUCCESS"
  }
}
```

Chrome 화면 OCR은 `CHROME_VIEWPORT_OCR`이다. `ocr.confidence`는 인식 품질이며 최종 qualityScore·관련성 점수·정답 확률과 다르다. 전처리가 최종 품질을 계산한다. 입력 qualityScore가 있더라도 최종값으로 무조건 신뢰하지 않는다.

### 3.3 사용자가 확정한 목표

현재 내부 `ConfirmedGoal` 예시다. `text`는 여기에서는 목표 문장, 추출 입력에서는 현재 콘텐츠 문자열이다. 둘은 별도 객체다.

```json
{
  "goalId": "goal-react-001",
  "goalVersion": 1,
  "text": "React useState로 객체 상태 변경하기",
  "confirmed": true,
  "templateId": "react-object-state"
}
```

`templateId`는 사용할 목표별 개념 목록의 식별자다. `react-object-state` 템플릿의 개념은 `state-update`(DIRECT), `object-spread`(PREREQUISITE), `state-debug`(SUPPORTING)다. 제목·소제목은 이 개념들과 비교할 데이터다.

### 3.4 검증 문맥

다음은 학교 테스트용 합성 `VerifiedContext`다. 실제 운영에서는 Server가 검증한 현재 상태를 공급한다.

```json
{
  "sessionId": "session-001",
  "runId": "run-001",
  "goalId": "goal-react-001",
  "goalVersion": 1,
  "eventId": "event-dom-001",
  "navigationId": "navigation-001",
  "observedAt": "2026-10-11T01:00:00Z",
  "sessionStatus": "ACTIVE",
  "exclusionMode": "NONE",
  "userId": "synthetic-user-001",
  "resumeRequired": false,
  "policyVersion": "school-experimental-v1",
  "extractorVersion": "synthetic-v1",
  "stateVersion": 1
}
```

실제 호출에는 콘텐츠·목표·문맥 3개 객체와 최신 문맥 조회 callback을 각각 넘긴다. OCR 예시를 호출할 때는 문맥의 eventId도 `event-ocr-001`로 맞춘다.

```python
result = analyzer.analyze(content, goal, context, current_context=read_current_context)
```

`sessionId`, `runId`, `goalId`, `goalVersion`, `eventId`, `navigationId`, `observedAt`, `sessionStatus`, `exclusionMode` 9개는 현재 내부 테스트 Schema에서는 필요하지만, Client의 최종 공개 필수 컬럼·발급 책임으로 모두 확정된 것은 아니다. 백엔드 협의 경계를 유지한다. 합성 테스트에서는 `current_context=lambda: context`를 쓸 수 있으나 운영의 최신 상태 조회를 대신하지 않는다.

## 4. 전처리 후 비교 단위

3.1절 DOM을 현재 전처리에 통과시킨 내부 결과의 passages다. 공개 요청에 kind·order 컬럼을 새로 넣으라는 뜻이 아니다.

```json
[
  {
    "id": "passage-001",
    "kind": "TITLE",
    "text": "useState 객체 상태 업데이트",
    "order": 0
  },
  {
    "id": "passage-002",
    "kind": "HEADING",
    "text": "## useState 객체 상태",
    "order": 1
  },
  {
    "id": "passage-003",
    "kind": "BODY",
    "text": "useState로 객체 상태 업데이트를 수행할 때 이전 객체를 직접 수정하지 않고 상태 변경 함수에 새 객체를 전달합니다.",
    "order": 2
  },
  {
    "id": "passage-004",
    "kind": "CODE",
    "text": "```javascript title=\"state.js\"\nsetState({...prev, count: 1});\nconst example = \"&lt;\";\n```",
    "order": 3
  }
]
```

| 종류 | 점수·충돌 검사 | 본문 커버리지 |
| --- | --- | --- |
| TITLE | 포함 | 제외 |
| HEADING | 포함 | 제외 |
| BODY·OCR·CODE·TABLE | 포함 | 포함 |

모든 단위 ID는 현재 요청 안에서 유일하고 순서를 유지한다. 제목·소제목도 입력 상한에 포함한다. 현재 기본 상한은 최대 8단위·단위당 800자·총 6,000자다. 상한으로 잘려 대표성을 확인할 수 없는 입력은 `INCOMPLETE_REPRESENTATION`·`UNCERTAIN`으로 보류한다. 제목·소제목만 남거나 코드 fence에 내용이 없으면 본문 근거가 아니다.

제목·소제목을 추가했다고 본문 상한을 임의로 늘리거나 관련 문단만 선택해 나머지 내용을 버리지 않는다. 일반 문단 청크의 겹침과 전체 문단 길이 기반 커버리지는 현재 실험 정책이며 독립 평가가 필요하다.

## 5. Ollama에 실제로 보내는 JSON

서비스 입력 전체를 문자열로 직렬화해서 임베딩하지 않는다. `/api/embed`의 `input`에는 검증·정제한 텍스트 문자열만 넣는다. API 계약은 [Ollama 공식 embed 문서](https://docs.ollama.com/api/embed)를 따른다.

### 5.1 목표 개념 질의

3.3절 목표의 현재 Graph는 관련 개념 3개와 대조용 주제 3개를 포함한다. 아래는 현재 `concept_queries()`가 만든 실제 형식이다. 질의 instruction 및 `Concept:` 구성은 로컬 실험 설정이다.

```json
{
  "model": "qwen3-embedding:4b-fp16",
  "input": [
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: React useState로 객체 상태 변경하기\nConcept: React useState object state updates with a setter",
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: React useState로 객체 상태 변경하기\nConcept: JavaScript object spread and immutable copies needed to update state",
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: React useState로 객체 상태 변경하기\nConcept: Inspect React object state with React DevTools",
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: Create vector graphics posters in Illustrator",
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: Song lyrics music albums and singers",
    "Instruct: Retrieve passages useful for the given learning goal and concept.\nQuery: Cooking recipes and ingredients"
  ],
  "truncate": false,
  "keep_alive": "10m"
}
```

대조용 주제는 vector-art·music·cooking이다. `role=null`은 무관 근거 후보라는 내부 용도다. 해당 문구가 보이거나 관련 개념이 없다는 사실만으로 UNRELATED를 확정하지 않는다. 현재 connection은 템플릿 관계 메타데이터이며 별도 다중 경로 그래프 탐색 결과가 아니다.

### 5.2 현재 제목·소제목·본문·코드

3.1절 입력에서 정제한 문자열을 같은 순서로 배치에 넣는다. document에는 목표 질의 instruction을 붙이지 않는다. 벡터 순서와 passage ID의 대응은 FocusOn 코드가 유지한다.

```json
{
  "model": "qwen3-embedding:4b-fp16",
  "input": [
    "useState 객체 상태 업데이트",
    "## useState 객체 상태",
    "useState로 객체 상태 업데이트를 수행할 때 이전 객체를 직접 수정하지 않고 상태 변경 함수에 새 객체를 전달합니다.",
    "```javascript title=\"state.js\"\nsetState({...prev, count: 1});\nconst example = \"&lt;\";\n```"
  ],
  "truncate": false,
  "keep_alive": "10m"
}
```

제목·소제목 각각에도 벡터를 만든다. aliases·role·품질 숫자·식별자·정답 라벨·앱명·도메인을 위 문서 문자열에 섞지 않는다. 개인정보·제외·추출 실패·낮은 품질·stale을 먼저 검사한다.

### 5.3 모델 출력

| 필드 | 확인할 것 |
| --- | --- |
| model | `qwen3-embedding:4b-fp16`과 일치 |
| embeddings | input N개에 대해 같은 순서의 N개 벡터, 벡터당 2560차원 |
| 벡터 값 | 유한한 숫자, 0벡터 금지, 정규화 후 코사인 계산 |
| 모델 메타데이터 | 정확한 manifest digest·F16·embedding capability·2560차원 |

목표 개념 배치는 위 예시에서 6개, 콘텐츠 배치는 4개다. 학교 모델은 `RELATED`·이탈 이유·Hybrid 점수를 생성하지 않는다. 임베딩 반환은 [Qwen 모델 카드](https://huggingface.co/Qwen/Qwen3-Embedding-4B) 및 [Ollama API](https://docs.ollama.com/api/embed)의 텍스트 벡터 형식이다.

## 6. Hybrid 6번의 계산과 최종 판정

### 6.1 개념 × 비교 단위마다 계산

비교 단위 하나를 모든 관련 개념·대조용 주제와 각각 비교한다. 3.1절의 4단위와 6개 개념/주제는 총 24개 매칭 점수를 만든다. 역할별로 반드시 하나씩 나오는 구조가 아니며 역할에 여러 개념이 있으면 각각 계산한다.

```text
K = min(1, 일치한 aliases 항목 수 / 2)
C = cosine(단위 벡터, 개념 벡터)
H = 0.35 × K + 0.65 × max(0, C)
```

각 alias 항목은 해당 단위 안에서 발견되면 한 번 센다. 같은 단어의 반복 횟수를 세는 방식은 아니다. 현재 구현에서는 겹치는 aliases가 각각 일치할 수 있다. 선수·보조 역할은 Graph에 지정된 값을 사용하며 모델이 새로 판단하지 않는다.

### 6.2 현재 실험 숫자

노션의 확정 숫자가 아니다. `AI/evaluation/relevance_calibration.json`과 `HybridConfig`의 미보정 초기 값이며 학교 개발 세트에서 검증·조정해야 한다.

| 기준 | 현재 값 |
| --- | --- |
| 키워드/벡터 비율 | 0.35 / 0.65 |
| 최소 cosine | 0.55 |
| DIRECT 결합 점수 | 0.72 이상 |
| PREREQUISITE 결합 점수 | 0.76 이상 |
| SUPPORTING 결합 점수 | 0.76 이상 |
| 무관 후보 결합 점수 | 0.80 이상 |
| 관련 본문 커버리지 | 0.60 이상 |
| 무관 본문 커버리지 | 0.70 이상 |

관련 근거 조건: 관련 개념 role이 있고 K > 0, C >= 0.55, H >= 그 role의 임계값을 모두 통과한다.

무관 근거 조건: 대조용 주제 role이 null이고 K = 1, C >= 0.55, H >= 0.80을 모두 통과한다.

키워드가 없어도 의미가 연결될 수 있다는 기획 방향은 남아 있으나 현재 코드는 K > 0 조건을 요구한다. 그러므로 높은 벡터 유사도만으로 자동 RELATED가 되는 구현이라고 설명하지 않는다. 숫자와 Graph 관계·정답 데이터는 함께 검수한다.

### 6.3 충돌을 먼저 확인

전체 통과 근거에 관련과 무관이 동시에 있으면 커버리지 수치에 앞서 `UNCERTAIN / CONFLICTING_EVIDENCE`로 반환한다. 제목↔본문, 소제목↔본문, 본문↔본문 등 모든 단위의 통과 근거를 포함한다. 같은 단위가 두 범주의 근거를 모두 통과할 수도 있다. DIRECT·PREREQUISITE·SUPPORTING 근거가 함께 있다는 사실은 충돌이 아니다.

예: React 객체 상태 변경 목표에서 관련 제목 근거와 요리 본문의 무관 근거가 각각 기준을 통과하면 UNCERTAIN이다. 해당 문구만 입력하면 반드시 이 결과가 나온다는 의미가 아니다. 실제 모델 벡터가 조건을 통과하는지도 확인한다.

### 6.4 본문 커버리지와 최종 라벨

```text
본문 = 실제 분석한 BODY·OCR·CODE·TABLE (TITLE·HEADING 제외)
R = 관련 근거로 통과한 본문 단위 길이 / 전체 분석 본문 단위 길이
U = 무관 근거로 통과한 본문 단위 길이 / 전체 분석 본문 단위 길이

본문 없음 → UNCERTAIN / INSUFFICIENT_BODY (모델 미호출)
관련·무관 통과 근거가 함께 있음 → UNCERTAIN / CONFLICTING_EVIDENCE
그 외 R >= 0.60 → RELATED / GOAL_CONNECTED
그 외 U >= 0.70 → UNRELATED / POSITIVE_UNRELATED_TOPIC
그 외 → UNCERTAIN / INSUFFICIENT_EVIDENCE
```

길이는 정제 후 문자열의 문자 수다. 현재 코드는 일치한 부분 문자열의 길이가 아니라 통과한 전체 본문 단위 길이를 센다. 한 본문이 같은 범주에서 여러 개념과 일치해도 한 번만 센다. 원래 웹사이트 전체 길이가 아니다. 두 범주가 겹치면 R+U가 1을 넘을 수 있지만 충돌 규칙으로 보류한다.

제목·소제목의 점수가 높아도 부족한 본문 근거를 대신하지 않는다. 본문 근거로 RELATED/UNRELATED가 성립하면 같은 방향으로 통과한 제목·소제목 ID도 근거 ID에 포함한다. 제목 없는 충분한 OCR은 그대로 분석 가능하다.

### 6.5 캐시

제목·소제목·본문·코드의 내용·종류·순서가 정제 해시에 포함된다. 기존 appName·urlHost도 관련성 결과 캐시를 구분한다. 목표·모델·instruction·Graph·정책·전처리·분석기·점수 설정 등의 범위가 같을 때만 재사용한다. 앱·도메인만 바뀌면 결과는 다시 계산하고 목표 벡터는 재사용한다. 원문 텍스트·메타데이터는 결과 캐시에 저장하지 않는다.

## 7. 출력 형식과 구현 경계

### 7.1 현재 내부 Python 결과

다음은 3.1절에 사전 지정한 fake 벡터를 넣어 로컬에서 재현한 `RelevanceResult` 직렬화 예시다. 실제 Qwen 점수·정확도 결과가 아니다. 실제 모델에서는 판정이 달라질 수 있다.

```json
{
  "session_id": "session-001",
  "run_id": "run-001",
  "goal_id": "goal-react-001",
  "goal_version": 1,
  "event_id": "event-dom-001",
  "navigation_id": "navigation-001",
  "observed_at": "2026-10-11T01:00:00+00:00",
  "extraction_status": "SUCCESS",
  "analysis_status": "SUCCEEDED",
  "relevance_label": "RELATED",
  "reason_code": "GOAL_CONNECTED",
  "evidence_ids": [
    "passage-001",
    "passage-002",
    "passage-003",
    "passage-004"
  ],
  "content_roles": [
    "DIRECT"
  ],
  "quality_score": 1.0,
  "content_hash": "afacccad8ebe93baecd2f64bb92cbf2bba088d436471564b79ea40e72ab5632d",
  "cache_hit": false,
  "analyzer_version": "school-hybrid-v2",
  "model_version": "fake-scripted-v1",
  "policy_version": "school-experimental-v1"
}
```

`evidence_ids`에는 현재 요청의 제목·소제목·본문·코드 ID가 들어갈 수 있다. UNCERTAIN에서는 현재 코드가 확정 근거 ID를 비워 반환한다. 내부 reason_code는 고정 사유 코드이며 자연어 설명이 아니다. 코드에 자연어 reason·공개 confidence 생성이 이미 구현됐다고 가정하지 않는다.

현재 내부 CLI의 `cases[].predicted`가 관련성 라벨이고 `reasonCode`·`evidenceIds`·`roles`가 진단 결과다. CLI 자체 종료 코드 0만으로 실제 정확도 검증을 통과했다고 보지 않는다.

### 7.2 노션 공개 요청·응답 초안

아래 JSON 세 개는 AI 최종 기획서 15.3~15.5절의 기존 예시다. 이번 개정에서 새 공개 컬럼을 넣지 않았다. 내부 ContentInput/ConfirmedGoal/VerifiedContext를 이 공개 예시와 동일한 Schema로 취급하지 않는다. 공개 Adapter·Server 연결은 미완료이며 실제 팀 합의 계약과 null 규칙을 확인해 매핑해야 한다.

공개 요청 초안:

```json
{
  "eventId": "event-001",
  "sessionId": "session-001",
  "runId": "run-001",
  "goalId": "goal-jwt-001",
  "goalVersion": 1,
  "navigationId": "navigation-003",
  "source": "extension",
  "goalProfile": {
    "mainTopic": "Spring Security JWT 인증 구현",
    "purpose": "IMPLEMENTATION",
    "coreTopics": ["JWT", "인증 필터"],
    "supportingTopics": ["인증 오류 해결"]
  },
  "content": {
    "contentHash": "sanitized-content-hash",
    "pageType": "qa",
    "domain": "example.com",
    "title": "JWT 인증 오류 해결",
    "passages": [
      {"id": "passage-001", "text": "인증 필터 설정을 확인하는 예제"}
    ],
    "extractionMethod": "DOM",
    "extractionStatus": "SUCCESS",
    "extractorVersion": "proposal-1"
  },
  "signals": {
    "activeDurationSeconds": 35,
    "browserFocused": true
  },
  "observedAt": "2026-09-08T10:00:00Z"
}
```

공개 성공 응답 초안:

```json
{
  "success": true,
  "data": {
    "eventId": "event-001",
    "sessionId": "session-001",
    "runId": "run-001",
    "goalVersion": 1,
    "navigationId": "navigation-003",
    "source": "extension",
    "extractionStatus": "SUCCESS",
    "analysisStatus": "COMPLETED",
    "relevanceLabel": "RELATED",
    "driftState": "LEARNING",
    "state": "FOCUSED",
    "confidence": 0.84,
    "confidenceType": "HEURISTIC",
    "reason": "현재 인증 구현 목표에 필요한 오류 해결 자료입니다.",
    "evidenceIds": ["passage-001"],
    "recommendedAction": "NO_ACTION",
    "decisionStage": "FINAL",
    "observedAt": "2026-09-08T10:00:00Z"
  },
  "message": "분석이 완료되었습니다."
}
```

공개 실패 예시:

```json
{
  "success": false,
  "error": {
    "code": "MODEL_TIMEOUT",
    "message": "AI 분석이 지연되어 이번 페이지는 판단하지 않았습니다."
  }
}
```

confidence 0.84와 reason은 기존 노션의 가상 설명 값이다. Hybrid·cosine·OCR 값을 confidence에 그대로 복사하지 않는다. 내부 `analysis_status=SUCCEEDED`와 공개 초안 `analysisStatus=COMPLETED`는 명시적으로 매핑해야 한다. 관련성·지속 이탈·알림 정책도 각각 연결한다. `OFF_TASK`·`UNKNOWN`을 새 공개 relevanceLabel로 넣지 않는다.

## 8. 학교 Windows에서 Ollama 확인

PowerShell에서 실행한다. Ollama가 설치되지 않았다면 [Windows 다운로드](https://ollama.com/download/windows)로 설치한다. 기존 서비스가 실행 중이면 추가 `ollama serve`를 실행할 필요가 없다.

```powershell
ollama --version
nvidia-smi
Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/version" -Method Get
ollama pull qwen3-embedding:4b-fp16
ollama list
ollama show qwen3-embedding:4b-fp16
```

위 서비스 확인만 연결 실패하고 서버가 꺼져 있다면 별도 창에서 `ollama serve`를 실행한다. 모델 태그·F16은 [Ollama 공식 모델 페이지](https://ollama.com/library/qwen3-embedding:4b-fp16)에서 확인할 수 있다. 실제 VRAM과 GPU 사용은 학교의 nvidia-smi·ollama ps로 확인한다. RAM 128GB 또는 모델 파일 크기만으로 전량 GPU 실행을 보장하지 않는다.

정확한 manifest digest를 조회한다:

```powershell
$schoolModels = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -Method Get
$schoolModel = @($schoolModels.models | Where-Object { $_.name -eq "qwen3-embedding:4b-fp16" })
if ($schoolModel.Count -ne 1) { throw "Expected one Qwen3 FP16 model" }
$schoolDigest = $schoolModel[0].digest
$schoolModel[0] | Select-Object name, digest, details
```

digest는 API Key가 아니라 모델 버전 식별값이다. 실제 값을 10절에 사용한다. 모델명만 같고 digest·정밀도·차원이 다른 모델로 테스트를 이어가지 않는다.

## 9. 학교에서 벡터 반환만 확인하는 테스트

아래 4문자열은 질의 1개와 제목·소제목·본문을 한 API 호출로 확인하는 합성 smoke 입력이다. 5절의 실제 파이프라인은 개념 6개와 문서 4개를 별도 배치로 처리한다.

```powershell
$schoolPayload = @{
    model = "qwen3-embedding:4b-fp16"
    input = @(
        "Instruct: Retrieve passages useful for the given learning goal and concept.`nQuery: React useState로 객체 상태 변경하기`nConcept: React useState object state updates with a setter",
        "useState 객체 상태 업데이트",
        "## useState 객체 상태",
        "useState로 객체 상태 업데이트를 수행할 때 상태 변경 함수에 새 객체를 전달합니다."
    )
    truncate = $false
    keep_alive = "10m"
}
$schoolJson = $schoolPayload | ConvertTo-Json -Depth 10 -Compress
$schoolBytes = [System.Text.Encoding]::UTF8.GetBytes($schoolJson)
$schoolResponse = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/embed" -Method Post -ContentType "application/json; charset=utf-8" -Body $schoolBytes -TimeoutSec 120
if ($schoolResponse.model -ne "qwen3-embedding:4b-fp16") { throw "Wrong model" }
if ($schoolResponse.embeddings.Count -ne 4) { throw "Wrong vector count" }
foreach ($schoolVector in $schoolResponse.embeddings) {
    if ($schoolVector.Count -ne 2560) { throw "Wrong vector dimension" }
}
"Embedding API returned 4 vectors of 2560 dimensions."
ollama ps
nvidia-smi
```

벡터 수·차원·GPU 사용 여부만 확인한 결과다. 최종 RELATED/UNRELATED/UNCERTAIN은 다음 Hybrid 실행에서 확인한다. 첫 요청과 로드 후 반복 요청 시간을 구분하고, 동일 합성 payload를 반복해 warm P50/P95를 측정한다. 테스트 원문·벡터 전체를 로그나 장기 저장에 남기지 않는다.

## 10. 현재 프로젝트의 fake/real 평가 실행

### 10.1 코드 전달 상태 확인

최신 코드가 있는 저장소 루트에서 실행한다. 아래 확인이 실패하면 이 MD만 전달된 상태 또는 구 코드이므로 최신 구현 파일도 전달받아야 한다.

```bash
python3 -c "from AI.src.preprocessing.adapter import PREPROCESSING_VERSION; from AI.src.analysis.relevance_analyzer import ANALYZER_VERSION; print(PREPROCESSING_VERSION, ANALYZER_VERSION); assert PREPROCESSING_VERSION == 'school-text-v2'; assert ANALYZER_VERSION == 'school-hybrid-v2'"
```

Python 3.11 환경과 `AI/requirements.txt`의 프로젝트 의존성을 사용한다. 필요한 경우 활성화한 가상환경에서 `python3 -m pip install -r AI/requirements.txt`를 실행한다. 기존 Streamlit 후보 비교 화면은 E5-small 기반이며 이 Qwen3 내부 CLI와 동일하지 않다.

### 10.2 모델 없는 로직 확인 — Mac/Linux

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=AI:. python3 -m pytest AI/tests -q --ignore=AI/tests/langsmith_test.py -p no:cacheprovider
python3 -m AI.evaluation.relevance_eval --mode fake --split development --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
python3 -m AI.evaluation.relevance_eval --mode fake --split evaluation --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

현재 로컬 코드에서 166개 테스트·6개 하위 테스트가 통과했다. 최신 synthetic-school-v2의 fake 개발/평가 split은 각각 8/8 기대 라벨이 일치했다. fake는 등록된 벡터로 판정 로직을 검사하며 실제 모델 정확도·GPU 시간과 구분한다.

### 10.3 Mac에서 학교 모델을 호출

학교에서 이미 허용·설정한 SSH 계정·주소를 실제 값으로 바꾸고 터널을 유지한다.

```bash
ssh -N -L 11435:127.0.0.1:11434 SCHOOL_USER@SCHOOL_HOST
```

다른 Mac 터미널에서 저장소 루트로 이동해 실행한다. digest는 학교 8절에서 조회한 값이다.

```bash
export FOCUSON_EMBEDDING_URL='http://127.0.0.1:11435'
export FOCUSON_EMBEDDING_REVISION='REPLACE_WITH_ACTUAL_MANIFEST_DIGEST'
python3 -m AI.evaluation.relevance_eval --mode real --split development --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

검수한 개발 세트로 설정을 조정한 뒤 동결하고 같은 명령에서 `--split evaluation`으로 평가한다. Client는 real 모드에서 `/api/tags`·`/api/show`·`/api/version`으로 모델 metadata를 검사한다. 실패하면 fake로 대체하지 않는다. 학교 11434를 그대로 외부 공개하는 대신 localhost·SSH 터널 또는 합의된 TLS 경로를 사용한다.

### 10.4 학교 Windows에서 동일 후처리까지 검사하는 선택 경로

학교에도 현재 프로젝트 코드와 Python 환경이 있는 경우다. 활성 Python 명령이 python인지 먼저 확인하고 저장소 루트에서 실행한다. Python 이름이 다르면 해당 환경의 실행 파일로 바꾼다.

```powershell
python --version
python -m pip install -r AI/requirements.txt
$env:PYTHONDONTWRITEBYTECODE = "1"
$env:PYTHONPATH = "AI;."
python -m pytest AI/tests -q --ignore=AI/tests/langsmith_test.py -p no:cacheprovider
$env:FOCUSON_EMBEDDING_URL = "http://127.0.0.1:11434"
$env:FOCUSON_EMBEDDING_REVISION = $schoolDigest
python -m AI.evaluation.relevance_eval --mode real --split development --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

이때 후처리는 학교 Python에서 실행되고 Ollama는 동일 학교 GPU의 벡터 API다. Mac 왕복 지연을 측정한 결과와 구분한다. SSH 서버를 새로 설치하거나 방화벽을 변경하는 작업은 학교의 기존 허용 설정에 맞춘다.

## 11. 새로 확인할 테스트 사례

2026-10-12에 relevance_cases.json의 16개를 synthetic-school-v2로 개정했다. 기존 입력 필드를 유지하고 제목·소제목·코드와 충돌·커버리지 사례를 넣었다. 개발 8개·평가 8개와 목표 템플릿 분리를 유지한다. expected는 사람이 독립 검수하기 전의 기획/정책 기반 기대값이다. fakePassageVectors는 로직 검사용이며 real 모드에서는 사용하지 않는다. 아래 표는 실제 점수를 고정하거나 실제 Qwen 정확도를 확인한 결과가 아니다.

| 사례 | 확인할 것 |
| --- | --- |
| 관련 제목 + 관련 주요 본문 | 본문 근거 충족 시 RELATED, 일치하는 제목·본문 ID 연결 |
| 관련 제목 + 적극적 무관 본문 | 둘 다 기준 통과 시 UNCERTAIN·CONFLICTING_EVIDENCE |
| 무관 제목 + 관련 본문 | 둘 다 기준 통과 시 동일하게 보류 |
| 관련 소제목 + 무관 본문 | 소제목 근거도 충돌 검사에 참여 |
| 긴 관련 제목 + 부족한 본문 | 제목 길이로 관련 커버리지를 높이지 않음 |
| 긴 중립 제목 + 충분한 관련 본문 | 제목 길이로 관련 커버리지를 낮추지 않음 |
| 제목·소제목만 있고 본문 없음 | 모델 미호출 UNCERTAIN |
| 제목 없는 충분한 OCR | 제목 부재만으로 탈락하지 않음 |
| JavaScript fence·언어·설명·들여쓰기·entity literal | 구조·값 보존, 같은 정제 문자열이 모델에 전달 |
| 민감 제목·본문·코드 | 모델 미호출 PRIVACY_BLOCKED |
| 같은 본문, 제목/앱/도메인 변경 | 결과 재사용 구분, 동일 목표 벡터 재사용 |
| 목표 템플릿 없음 | 현재 코드 GOAL_GRAPH_UNAVAILABLE·UNCERTAIN |
| 게임 화면 본문 | 사람 정답과 실제 결과 비교. 현재 무관 후보에는 게임 개념이 없어 UNRELATED 보장이 없음 |

최신 16개는 기존 `input.pageTitle`과 `text`의 명시 소제목을 사용한다. 추가 사례를 만들 때도 같은 형식을 사용한다. 새 공개 컬럼을 만들어 해결하지 않는다. 개발/평가 목표군 분리 검사를 유지한다. fakePassageVectors가 필요한 fake 사례는 전처리 후 전체 단위 수와 순서가 맞아야 한다. real 테스트에 fake 벡터를 모델 결과처럼 사용하지 않는다.

라벨별 precision·recall·Macro-F1, 정상 학습을 UNRELATED로 오판한 비율, UNCERTAIN 비율, 본문 없는 입력의 모델 호출 수, cold/warm 임베딩 시간과 전체 요청 P50/P95를 구분해 기록한다. 목표 개념 캐시 유무, 실제 모델 digest, 정책·전처리·Graph·instruction 버전을 함께 기록한다. 지금 CLI의 provisionalMetrics는 제외·민감 상태까지 포함한 합성 지표이며 출시 기준이 아니다.

### 최신 16개 데이터 구성

| caseId | split | 검증 사례 | 기대 라벨 |
| --- | --- | --- | --- |
| dev-state-direct | development | 관련 제목·소제목·주요 본문 | RELATED |
| dev-state-prerequisite | development | 선수 개념 제목·소제목·본문 | RELATED |
| dev-unrelated-title-related-body | development | 무관 제목과 관련 보조 본문 충돌 | UNCERTAIN |
| dev-code-language-entity | development | 코드 fence·언어·설명·entity 보존 | RELATED |
| dev-title-heading-only | development | 제목·소제목만 있고 본문 없음 | UNCERTAIN |
| dev-ocr-no-title | development | 제목 없는 충분한 OCR | RELATED |
| dev-black-screen | development | 관련 제목이 있어도 캡처 실패 | UNCERTAIN |
| dev-long-title-insufficient-body | development | 긴 관련 제목·소제목과 부족한 관련 본문 | UNCERTAIN |
| eval-jwt-supporting | evaluation | JWT 오류 해결 보조 자료 | RELATED |
| eval-vector-poster | evaluation | 벡터 그래픽 무관 제목·소제목·본문 | UNRELATED |
| eval-related-title-unrelated-body | evaluation | 관련 제목과 무관 본문 충돌 | UNCERTAIN |
| eval-heading-body-conflict | evaluation | 관련 소제목과 무관 본문 충돌 | UNCERTAIN |
| eval-partial | evaluation | 긴 중립 제목과 충분한 PARTIAL 본문 | RELATED |
| eval-unrelated-title-related-body | evaluation | 무관 제목과 관련 React 본문 충돌 | UNCERTAIN |
| eval-excluded | evaluation | 사용자 제외 | EXCLUDED |
| eval-sensitive | evaluation | 코드의 인코딩된 민감정보 | PRIVACY_BLOCKED |

## 12. 현재 구현된 것과 남은 연결

| 현재 구현 | 아직 확인·연결할 것 |
| --- | --- |
| 내부 입력·목표·문맥 Schema, 전처리·해시 | Client/Server의 실제 JSON과 발급·검증 책임 |
| 제목·소제목·코드 Hybrid 매칭·충돌·커버리지 | 실제 Qwen 결과로 보정 및 독립 정답 평가 |
| Ollama Client·metadata/벡터 검증·TTL/LRU | 학교 GPU·VRAM·API·Mac 왕복 실측 |
| 내부 라벨·고정 reason_code·근거 ID | 공개 Adapter·자연어 reason·confidence 의미·Server 연결 |
| 템플릿 5개와 대조용 주제 3개 | 목표/무관 주제·관계 근거의 사람 검수와 범위 보완 |

최근 3~5개 활동 요약·정밀 LLM, 공개 FastAPI 실행 경로, 지속 이탈·알림 연결은 현재 내부 파이프라인에 완료되지 않았다. 이 파일의 명령은 기존 CLI 실행이며 uvicorn이나 Streamlit 공개 서버가 완성됐다는 의미가 아니다. 실패·낮은 품질·제외·민감·오래된 결과를 UNRELATED나 이탈로 바꾸지 않는다.

## 13. 학교 코딩 AI에게 전달할 지시

```text
이 지침의 school-text-v2 / school-hybrid-v2 코드와 노션 Hybrid 6번을 기준으로 확인한다.
기존 입력 JSON과 공개 응답 계약을 사용하고 새 컬럼을 임의 추가하지 않는다.
제목과 소제목은 새 Graph 개념이 아니라 현재 콘텐츠의 비교 단위다.
pageTitle과 text에 명시된 소제목/본문/코드를 각 개념과 독립 비교한다.
aliases는 정제 문자열과 비교한다. 임베딩 모델에는 개념 질의와 정제 단위 문자열을 보낸다.
전체 업무 JSON 또는 이 MD를 하나의 문자열로 임베딩하지 않는다.
학교 Ollama는 qwen3-embedding:4b-fp16의 실제 벡터를 반환하고 후처리 코드가 cosine/Hybrid/판정을 계산한다.
기존 숫자 정책을 운영 확정값으로 기록하지 않는다.
관련·무관 통과 근거가 함께 있으면 커버리지에 앞서 UNCERTAIN이다.
TITLE/HEADING은 점수·충돌·근거에 참여하지만 본문 커버리지에서 제외한다.
본문 근거가 부족하면 높은 제목 점수로 RELATED/UNRELATED를 확정하지 않는다.
코드 fence·언어·설명·들여쓰기·문자열을 보존하고 빠진 정보를 추측하지 않는다.
실제 GPU·vector 수/차원·digest·정밀도·cold/warm·전체 지연을 확인하고 미실행 항목은 명시한다.
검증에는 합성 데이터만 사용하고 원문·전체 URL·검색어·비밀 값·벡터를 장기 저장하거나 로그에 남기지 않는다.
현재 기능 범위에서 기존 코드를 재사용한다. 다른 기능/모듈과 GitHub 쓰기는 별도 요청 범위다.
```

## 14. 출처와 문서 검증

- [입력 테스트 검증 전처리](https://app.notion.com/p/3e1357537c3180b0ba22f17c5fdb8694): 2.2절 입력, 5절 보존, 7절 전달.
- [학습 목표와 콘텐츠 관련성 판단](https://app.notion.com/p/3e1357537c31807ba146ebda318f025a): 4.1절 분담, 7.2.4절 제목·소제목 정책, 8절 공개 계약, 10절 캐시.
- [유사도 검색 6번](https://app.notion.com/p/3e1357537c3180b7a519f8c7940c80d2): 목표별 Graph·키워드·벡터 Hybrid 방향.
- [AI 최종 기획서 (2)](https://app.notion.com/p/3e2357537c318072821efc0028fb8436): 7.1·8.2·8.3절 개정, 15.3~15.5절 기존 공개 예시.
- [Ollama 모델 태그](https://ollama.com/library/qwen3-embedding:4b-fp16), [embed API](https://docs.ollama.com/api/embed), [Qwen 모델 카드](https://huggingface.co/Qwen/Qwen3-Embedding-4B).
- 코드: AI/src/api/schemas.py, preprocessing/adapter.py·models.py·text_cleaner.py·validator.py, analysis/goal_graph.py·hybrid_scoring.py·relevance_analyzer.py, models/embedding_client.py, evaluation/relevance_eval.py·relevance_calibration.json.

개정 문서의 JSON·전처리 순서·현재 기본 설정·Python 명령을 로컬에서 확인했다. Windows PowerShell 실행·학교 Ollama/GPU·Mac SSH 연결은 이 작성 환경에서 실행하지 않았다. 실제 결과값을 추측해서 채우지 않았다.

## 15. 현재 선택한 연결 경로: Tailscale 사설 HTTPS

2026-10-13 사용자는 학교 LAN timeout 이후 Tailscale을 선택했다. 10.3절 SSH 명령은 기존 연결 방법 기록이며 현재 작업은 아래 순서로 진행한다.

1. Mac과 학교 Windows에 Tailscale을 설치하고 같은 tailnet에 로그인한다. 계정 로그인·설치 약관·VPN 권한은 사용자가 직접 확인한다.
2. 학교 PowerShell에서 `Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health'`로 기존 FastAPI 상태를 확인한다.
3. `tailscale serve status`로 기존 설정을 확인한다. 해당 HTTPS 포트·경로가 비어 있는 새 설치라면 다음 명령으로 학교 API를 private HTTPS Serve에 연결한다.

```powershell
tailscale serve --bg --https=443 http://127.0.0.1:8000
tailscale serve status
```

4. 실제 출력된 HTTPS 주소 전체를 Mac 쪽에 전달한다. 예시 문자열이나 이전 학교 LAN IP를 그대로 사용하지 않는다.
5. Mac의 Tailscale 연결이 완료되면 해당 HTTPS 주소의 `/health`와 합성 `/api/embed` 반환을 확인한다.

Mac과 학교 PC는 같은 tailnet에 연결됐고 학교의 Tailscale Serve HTTPS 주소로 `/health` 및 `/api/embed` 요청을 실제 확인했다. 상세 측정과 계약 한계는 아래 2026-10-14 결과를 참조한다.

학교 FastAPI는 `/health`·`/api/embed`만 제공한다. HTTPS와 실제 벡터 반환은 확인했지만 기존 Ollama Client가 요구하는 `/api/tags`·`/api/show`·`/api/version` 경로는 제공하지 않는다. 현재 표준 `relevance_eval --mode real`을 그대로 실행할 수 없으며, Client가 health 메타데이터를 검증하도록 계약을 연결한 뒤 독립 검수한 정답 세트로 평가한다. Tailscale 연결이나 임시 Adapter 실행을 표준 Hybrid 연동 완료로 표시하지 않는다.

## 2026-10-14 실제 연결 결과

- `/health`: HTTP 200, `qwen3-embedding:4b-fp16`, digest `3c93b6415795f9cc3e16c403a492ce25b1c0e260f2f760acc90e2035de25f9bc`, 2560차원, F16, embedding capability.
- 실제 한국어 임베딩 1건: Mac 왕복 12.409초, Ollama 내부 6.238초, 모델 로딩 6.176초. 벡터는 2560개 유한값이며 norm 1.0.
- 같은 텍스트 반복 요청 3건: Mac 왕복 2.619초·16.419초·5.704초, Ollama 내부 0.245~0.329초, 모델 로딩 0.012~0.014초. 왕복 편차가 있어 Tailscale 하나를 원인으로 확정할 수 없다.
- 메모리에서만 만든 임시 Adapter로 개발용 합성 사례 8개를 실행했다. 2개는 본문 부족·BLACK_SCREEN으로 분석 제외, 나머지 6개 중 기대 라벨 일치 3개·불일치 3개였다. 기대 라벨은 독립 검수 전이므로 정확도 결과가 아니다.
- 전체 실행 58.39초, API 왕복 지연 p50 6.08초·p95 23.31초. Ollama 내부 시간 p50 약 0.35초, 모델 로딩 p50 약 0.013초. GPU 사용률·VRAM은 수집하지 않았다.
- 표준 Client 연동, 공개 API·Server 연결, 독립 정답 세트의 정확도와 GPU 성능 검증은 남아 있다.

공식 안내: [Mac](https://tailscale.com/docs/install/mac), [Windows](https://tailscale.com/docs/install/windows), [Serve 명령](https://tailscale.com/docs/reference/tailscale-cli/serve).
