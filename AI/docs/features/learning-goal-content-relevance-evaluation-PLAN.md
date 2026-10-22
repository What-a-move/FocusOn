# 학습 목표와 콘텐츠 관련성 판단 — 학교 캡스톤 구현·테스트 지침

> 상태: 학교 내부 전처리·Graph·Hybrid·Ollama Client·평가 CLI 구현 · 공개 API/학교 실측 미완료
> 기준일: 2026-10-07
> 이 문서는 이재빈의 노션 기능 세부 기획서 두 개를 직접 읽어 작성했다. GitHub Issue 본문은 요구사항 원본으로 사용하지 않았다. Issue는 작업 추적에만 사용한다.

## 기본 정보

- 기능명: 입력 검증·전처리를 포함한 학습 목표와 콘텐츠 관련성 판단
- 기능 ID: relevance-analysis
- 작성자/담당: 이재빈
- 우선순위: MVP - 핵심
- 관련 Issue: [#11 통합 기능](https://github.com/What-a-move/FocusOn/issues/11). 기존 [#14](https://github.com/What-a-move/FocusOn/issues/14)의 관련성 구현·평가 범위를 #11로 통합한다.
- 통합 작업 Branch: `feat/11-ai-preprocessing-relevance`. 전처리·관련성을 이 Branch에서 함께 개발하고 내부 모듈과 테스트는 분리한다.
- 기획 원본:
  - [입력 테스트 검증 전처리](https://app.notion.com/p/3e1357537c3180b0ba22f17c5fdb8694): 2절 입력, 4~6절 검증·정제·품질, 7절 후속 전달
  - [학습 목표와 콘텐츠 관련성 판단](https://app.notion.com/p/3e1357537c31807ba146ebda318f025a): 4절 실행 환경, 5절 입력, 7.2절 Graph·점수·판정, 8~12절 출력·예외·평가
- 관련 공통 문서: [개인정보](../../../docs/DATA_PRIVACY.md), [작업 흐름](../../../docs/WORKFLOW.md), [AI API](../api_spec.md), [상태](../state_model.md), [평가](../evaluation_spec.md)
- API·공유 타입의 최종 계약은 원본 공통 계약을 따른다. 이 지침의 내부 예시가 새 공개 계약이 되는 것은 아니다.

### 현재 상태와 학교에서 확인할 것

최초 문서 작성 시 원격 AI 코드·requirements·테스트는 골격이었다. 이슈·Branch 통합만으로 기능 구현 완료가 되는 것은 아니다. 본 문서만 받아서는 관련성 API나 평가 CLI가 실행되지 않는다. 아래 구현 파일과 테스트 명령을 실제로 만들어 검증해야 한다.

2026-10-07 코드 게시 작업에서 기존 전처리 모듈·단위 테스트와 유사도 실험 소스를 통합 Branch에 반영했다. 기존 전처리를 먼저 검토·재사용하고 같은 통합 이슈 안에서 관련성 단계로 연결한다. 현재 전처리·실험 코드와 미구현 운영 기능의 경계는 [부분 구현 게시 결과](learning-goal-content-relevance-evaluation-REPORT.md)를 따른다. 실제 Qwen3·원격 임베딩·최종 품질 산식·공개 API 연동까지 완료된 것은 아니다.

최초 작성은 문서만 변경했다. 2026-10-07 실제 개발에서 내부 입력·Graph·Hybrid·Ollama Client·평가 CLI를 구현했다. [ERROR](learning-goal-content-relevance-evaluation-ERROR.md)와 [REPORT](learning-goal-content-relevance-evaluation-REPORT.md)에 실제 오류·테스트 결과와 미완료 연동을 기록한다. 미실행 학교 실측을 통과로 쓰지 않는다.

## 기능 목적

사용자가 확정한 단일 학습 목표와 현재 활성 앱·탭 콘텐츠를 비교한다. 하나의 기능 안에 아래 두 모듈을 연결한다.

1. 입력 검증·전처리: 유효한 요청인지 확인하고 개인정보·텍스트 품질을 검사한 뒤 대표 문단과 최종 품질·해시를 만든다.
2. 관련성 판단: 통과한 문단을 목표별 소형 Graph·키워드·Qwen3 임베딩으로 비교하고 최종 라벨과 근거를 반환한다.

화면 캡처, DOM 수집, OCR 실행, 카메라, 목표 설정 보조, 체류 시간 집계, 이탈·복귀·알림, 사용자 판정 수정·삭제·수정 기반 개인화는 구현 범위에 포함하지 않는다. 15초는 후속 이탈 정책이며 입력 폐기나 모든 분석의 공통 대기 조건으로 넣지 않는다.

## 입력 데이터

### 공통 추출 입력

김성현 → 이재빈 전달 기준은 노션 전처리 2.2절의 `text`·`ocr` 형식이다. 원천 `passages`·`ocrMeta`를 받는 경로가 있다면 김성현 측 Adapter가 문단·코드·표 경계를 보존해 변환한다.

```json
{
  "sessionId": "session-123",
  "runId": "run-456",
  "goalId": "goal-789",
  "goalVersion": 1,
  "clientRunId": "event-012",
  "sequence": 12,
  "navigationId": "navigation-034",
  "observedAt": "2026-10-07T01:00:00Z",
  "isActiveTarget": true,
  "sessionStatus": "ACTIVE",
  "exclusionMode": "NONE",
  "contentSource": "DESKTOP_APP_OCR",
  "contentType": "DOCUMENT",
  "appName": "Preview",
  "windowTitle": "React 학습 자료 — Preview",
  "pageTitle": "React 학습 자료",
  "urlHost": "",
  "text": "useState로 객체 상태를 갱신할 때 이전 객체를 직접 변경하지 않고 새로운 객체를 생성합니다.",
  "extractionStatus": "SUCCESS",
  "isSensitive": false,
  "isExcluded": false,
  "ocr": {
    "confidence": 0.91,
    "captureStatus": "SUCCESS"
  }
}
```

- DOM은 같은 공통 필드를 사용하며 출처를 `CHROME_DOM`으로 전달한다. OCR 추가 정보는 적용 가능한 입력에서만 받는다.
- 출처 후보: `APP_META / TAB_META / CHROME_DOM / CHROME_VIEWPORT_OCR / DESKTOP_APP_OCR`. 실제 enum·누락·빈 값 규칙은 합의된 소비자 Schema를 확인한다.
- `ocr.confidence`는 원천 OCR 인식 품질이다. 이재빈이 계산하는 최종 `qualityScore`나 목표 관련성 점수가 아니다.
- 기존 노션 내부 입력 예시에 `qualityScore`가 있어도 이를 최종 품질로 무조건 신뢰하지 않는다. 원천 품질을 참고하되 최종 품질은 전처리에서 산출한다. 품질 산식은 설정 가능한 실험 정책이며 아직 확정 수치가 아니다.
- 원본 HTML·이미지·Base64·캡처 파일·카메라 프레임은 입력으로 받지 않는다.

### 실행 문맥·중복·순서 합의 컬럼

기존 합의 대상 9개 중 `eventId`는 허재원 화면 기획의 `clientRunId` 의미로 교체해 내부 event key에 매핑한다. 여기에 출처별 역순 방지용 `sequence`가 추가로 필요하다. 전부 Client 추출 JSON의 필수 값으로 단정하지 않고 Client가 만든 관측 정보와 Server 검증 문맥을 결합한다.

| 컬럼 | 사용 목적 | 협의할 것 |
| --- | --- | --- |
| sessionId | 논리 세션 식별 | 발급·조회·전달 주체 |
| runId | 실행 구간 식별 | 구간 경계·현재 run 검증 |
| goalId | 확정 목표 조회 | run에서 조회하면 별도 전달이 필요한지 |
| goalVersion | 오래된 목표 분석 구분 | run 안 목표 고정과 버전 필요성 |
| clientRunId | 같은 관측의 결과 귀속·멱등 처리 | Client 발급·재시도 시 유지·`UNIQUE(sessionId, clientRunId)` |
| sequence | 같은 `sessionId + contentSource` 안의 증가 순서 | 발급 주체·재시도 시 유지·최신값 조회 |
| navigationId | 페이지·창 이동 구분 | 앱/Chrome별 변경 규칙 |
| observedAt | 원래 관찰 시각 | 시각 형식·시간대·역순 처리 |
| sessionStatus | 분석 가능한 실행 상태 | enum·Server 검증 문맥 |
| exclusionMode | 제외 정책 확인 | enum·적용·검증 책임 |

학교 테스트에서는 합성된 현재 실행 문맥을 주입한다. 이것은 실제 인증·소유권 검증이 아니다. 운영에서 Client가 준 상태만 믿지 않는다. 값이 없는데 임의로 현재 값으로 채우거나 stale 검사를 우회하지 않는다.

`contentSource`는 순서 비교 범위이며, `isActiveTarget`은 Client 조정자가 실제 foreground 앱·창과 Chrome foreground/active tab을 확인한 결과다. AI는 false이거나 최신 문맥과 불일치하면 `UNCERTAIN/INACTIVE_TARGET`으로 보류한다. Chrome의 active tab이라는 사실만으로 Chrome이 현재 foreground 앱이라고 간주하지 않는다.

### 목표 입력과 전처리 출력

실제 확정 목표 문장 또는 Server가 연결한 GoalProfile을 함께 사용한다. `goalId`만으로 목표 의미를 비교할 수 없고 페이지 텍스트에서 사용자 목표를 추측하지 않는다.

전처리 출력은 노션 7절대로 `content.passages`, 현재 요청 안에서 유일한 문단 ID, 최종 `contentHash`, 추출 상태·품질 근거를 전달한다. 목표와 분석 설정은 후속 요청에서 결합한다.

```text
contentSource → source / content.extractionMethod
contentType   → content.pageType
urlHost       → content.domain
pageTitle     → content.title
text          → content.passages
```

코드·표·문단 경계와 읽기 순서를 보존한다. 원천 화면 fingerprint와 최종 정제 contentHash는 서로 다른 중복 키다.

## 분석 기준

### 검증·전처리 구현

1. Schema·자료형·현재 실행 문맥·목표·이벤트를 검증한다.
2. 제외·민감 화면·권한·추출 실패를 먼저 처리하고 이 경로에서 모델을 호출하지 않는다.
3. 개인정보 잔존을 재검사한다. 안전하게 정제할 수 없으면 `PRIVACY_BLOCKED`로 중단한다.
4. 일반 문장 공백·줄바꿈·UI 조각·완전 중복을 정리한다. 코드 들여쓰기·수식·오류 코드·부정 표현을 임의 수정하지 않는다.
5. 대표 본문·문단·표·코드 단위로 분할하고 최종 품질과 해시를 산출한다.
6. 분석 가능한 범위만 관련성 단계로 전달한다. 실패·제외 결과에는 분석용 본문을 넘기지 않는다.

검은 화면·빈 캡처·중복 캡처 검사는 캡처 담당 Client가 한다. 이 기능은 전달된 `ocr.captureStatus` 등 실패 신호를 검증한다. 이미지를 요구해 재검사하지 않는다.

문단 최대 8개·문단당 800자·전체 6,000자, 30자·품질 0.70·깨진 문자 20%는 노션의 초기 실험값이다. 설정으로 관리하며 짧은 코드·오류 문구는 길이만으로 탈락시키지 않는다. 겹침도 입력 상한에 포함한다. 초기 품질 산식과 변경 이력을 테스트 결과에 기록한다.

### 목표별 Graph

MVP는 검토된 목표별 템플릿으로 시작한다. Graph 개념은 사용자의 확정 목표에서 준비하고 방문 문단에서 목표 범위를 자동 확장하지 않는다.

| 역할 | 정의 | 목표: React useState로 객체 상태 변경하기 |
| --- | --- | --- |
| DIRECT | 목표의 핵심 대상·수행 | useState, 상태 변경 함수, 객체 상태 업데이트 |
| PREREQUISITE | 이해·수행에 필요한 선행 지식 | JavaScript 객체, 전개 구문 |
| SUPPORTING | 수행 중 참고·확인·디버깅 | React DevTools 상태 확인 |

개념은 ID·설명·동의어·역할·목표 연결 근거·템플릿/관계 버전을 내부에서 관리한다. 이는 구현용 내부 구조이며 공개 응답 필드 추가가 아니다.

같은 개념도 목표에 따라 역할이 바뀐다. 임베딩 모델에 직접/선수/보조 라벨을 생성하게 하지 않는다. 문단을 Graph 개념과 매칭한 뒤 목표 기준 역할을 조회한다. `templateId`가 없는 확정 목표는 확인된 목표 문장·GoalProfile로 최소 Graph를 만들며, 생성형 LLM Client가 연결되면 같은 Graph Schema의 후보를 목표 버전별로 한 번 생성·검증·캐시한다.

생성형 LLM 입력에는 확정 목표와 확인된 GoalProfile만 넣고 현재 페이지·검색·OCR 본문은 넣지 않는다. 출력은 기존 Graph 구조의 concepts와 `role: null`인 `unrelated_topics`만 허용한다. 최소 DIRECT 1개, 최대 개념/aliases/문자 길이, ID·aliases 중복과 민감정보를 검증한다. LLM 실패·미연결 시 확정 범위만 사용하는 최소 Graph로 내려가며 이 fallback은 무관 주제를 추측하지 않는다. 프로세스 캐시는 Server 영속 저장을 대체하지 않는다.

대상이 있는 넓은 목표를 임의로 좁히지 않는다. 목표·관계 근거가 부족할 때는 가능한 판단만 하고 `UNCERTAIN`을 유지한다. 모든 넓은 목표를 무조건 보류하지 않는다.

### 메타데이터 우선 경로와 Hybrid 6번 판정

- `APP_META`·`TAB_META`는 `appName`, `windowTitle`, `pageTitle`, `urlHost`를 먼저 검사한다.
- 같은 Graph 개념의 aliases가 2개 이상 명확히 일치하고 반대 근거가 없을 때만 임베딩 없이 판정한다.
- 한 단어만 일치하거나 관련·무관 메타데이터가 충돌하면 `UNCERTAIN`이다. 이 경우 Client가 Chrome DOM, 그다음 필요한 경우 현재 viewport OCR을 새 관측으로 제출한다.
- 내부 분석 결과의 `next_content_source`는 이 단계 전환만 지시한다. `TAB_META → CHROME_DOM → CHROME_VIEWPORT_OCR`, `APP_META → DESKTOP_APP_OCR` 순서이며 OCR 뒤에는 추가 원문 수집을 요구하지 않는다.
- 각 단계는 새 `clientRunId`와 해당 `contentSource`의 증가한 `sequence`를 가진 새 관측이다. 이전 `clientRunId`의 본문만 바꿔 재사용하지 않는다.
- 제외·민감·비활성 대상·stale·목표 Graph 부재·모델 장애는 추가 DOM/OCR 수집으로 우회하지 않는다. `next_content_source`는 내부 조정 신호이며 합의되지 않은 공개 응답 필드로 확정하지 않는다.
- 비활성 창·탭, FocusOn 자체 UI, 활성 대상 불명 입력은 관련·무관 근거로 사용하지 않는다.
- DOM·OCR 본문이 필요한 요청은 기존 Hybrid 6번을 유지한다.

#### 2026-10-11 제목·소제목·코드 정보 반영

- 기존 `pageTitle`을 제목 문단으로 비교하고, `text` 안에 명시된 Markdown h1~h3 소제목은 내부 `HEADING` 단위로 구분한다. 공개 입력 컬럼은 추가하지 않는다.
- 제목·소제목에도 기존 키워드·cosine·Hybrid 계산을 적용한다. 본문과 상반된 통과 근거가 있으면 `UNCERTAIN`으로 보류하며, 제목·소제목 길이는 본문 커버리지 분자·분모에서 제외한다. 제목·소제목만으로 최종 관련·무관을 확정하지 않는다.
- 본문으로 판정이 성립하면 같은 방향의 제목·소제목 근거 ID도 함께 반환한다. 제목·소제목만으로 본문 근거가 부족한 결과를 승격하지 않는다. 이 결합 방식은 현재 보수적 실험 정책이며 실제 Qwen 평가가 필요하다.
- `text`에 이미 포함된 코드 fence의 언어·설명과 코드 들여쓰기·문자열 값을 보존한다. 추출기가 보내지 않은 소제목·코드 언어·창 제목은 추측하지 않는다.
- DOM·OCR Hybrid 경로의 `appName`·`urlHost`는 개인정보 검사와 결과 캐시 구분에 사용하며 새 점수나 목표 개념으로 만들지 않는다. 2026-10-17 메타데이터 우선 경로에서는 aliases의 명확한 일치 여부만 검사한다. 목표 벡터 캐시는 앱·도메인 변경으로 불필요하게 분리하지 않는다.
- 검증: 제목/본문 일치·충돌·제목만 있는 입력·제목 없는 OCR, 제목 길이와 커버리지, 코드 언어·literal 보존, 민감 제목/코드의 모델 미호출, 제목·앱·도메인 변경 시 캐시를 검사한다.
- `windowTitle`·`isActiveTarget`은 현재 내부 학교 Schema에 반영했다. 공개 Client·Server 계약과 실제 foreground 선택 구현은 담당자 합의가 필요하다.

```text
개념·문단 매칭 점수 = α × 키워드 매칭 점수 + β × 벡터 유사도
→ DIRECT / PREREQUISITE / SUPPORTING별 근거를 독립 평가
→ 본문 대표성·목표 연결·신호 충돌 검사
→ RELATED / UNRELATED / UNCERTAIN
```

- 키워드 비교는 동의어·토큰 경계·본문 위치를 사용한다. 부분 문자열 하나, 사이드바, 광고의 우연한 언급으로 확정하지 않는다.
- 임베딩은 같은 모델·리비전·정밀도·차원·전처리·instruction 설정으로 계산한다. cosine 계산 시 정규화·0벡터·NaN·차원 오류를 검사한다.
- 결합 전에 키워드와 cosine의 범위를 명시한다. α·β, 정규화 방법, 역할별 임계값은 실험 설정으로 주입하고 개발 세트에서 조정한다. 숫자는 본 문서에서 새로 확정하지 않는다.
- 직접/선수/보조 점수를 모두 더해 단일 0.6 기준으로 판단하지 않는다. 각 역할의 충분한 근거가 독립적으로 유효할 수 있다.
- 최고 점수 문단 하나로 전체 콘텐츠를 판단하지 않는다. 여러 대표 문단과 근거 ID를 보존한다.
- DIRECT 매칭이 낮아도 유효한 선수·보조 근거를 자동 취소하지 않는다. 선수·보조 점수가 높다고 자동 RELATED 처리하지도 않는다.
- 임베딩 유사도·결합 점수는 confidence·정답 확률·집중도와 동일하지 않다. 학습/평가로 검증되지 않은 confidence를 cosine으로 채우지 않는다.

| 최종 라벨 | MVP 판정 |
| --- | --- |
| RELATED | 목표 수행에 필요한 직접/선수/보조 연결과 실제 주요 본문 근거가 충분 |
| UNRELATED | 주요 본문 주제가 명확하고 목표와 무관하다는 적극적인 근거가 충분 |
| UNCERTAIN | 정보 부족, 경계, 의미 충돌, 충분하지 않은 근거 |

예: 파인튜닝 목표의 선수 개념 '벡터'가 벡터 그래픽 포스터 제작에 등장해도 그것만으로 RELATED가 아니다. 낮은 cosine·키워드 없음·Graph 누락만으로 UNRELATED를 만들지 않는다. 근거 검사 로직은 먼저 구현하고 복잡한 관계 검증·오탐 개선을 이후 보완한다.

1차 판단은 현재 목표·콘텐츠만 사용한다. 모호하거나 근거 충돌일 때만 최근 3~5개 개인정보 제거 활동 요약을 사용한다. 이 연결이 없거나 추가 LLM 공급자·예산이 정해지지 않았으면 명시적으로 추가 판단을 건너뛰고 UNCERTAIN을 유지한다. 이를 전체 MVP 추가 판단 구현 완료라고 표시하지 않는다.

## 출력 데이터

기존 팀 합의 응답 envelope와 노션 관련성 8절을 유지한다. 새 필드·새 confidence 계산식을 독자적으로 확정하지 않는다.

- 원래 이벤트·run·목표 버전·navigation·observedAt과 결과를 연결한다.
- `extractionStatus`, `analysisStatus`, `relevanceLabel`을 분리한다.
- `relevanceLabel`: 정상 분석은 RELATED / UNRELATED / UNCERTAIN; 모델 미실행 경로는 EXCLUDED / PRIVACY_BLOCKED를 구분한다.
- `confidence`, `confidenceType`, `reason`, `evidenceIds`, 분석기·모델·정책 버전은 기존 계약대로 직렬화한다.
- `contentRole`, `reasonCode`, `confidenceLevel`의 공개 여부는 기존 합의대로 유지한다. 내부 디버그 매칭 결과를 공개 필드로 추가하지 않는다.
- `driftState`·`recommendedAction`·알림·시간 집계는 김성현/정책 계층의 후속 책임이다.

학교에서 합의된 응답 Schema를 아직 확보하지 못하면 내부 결과 테스트를 먼저 하고 공개 API 연동은 미완료로 보고한다. 임의 JSON 예시를 '팀 합의 최종 응답'으로 내보내지 않는다.

## 처리 흐름

```text
김성현/Server의 합성 입력 + 확정 목표 + 검증 문맥
→ 제외/민감/실패/stale 검사
→ 개인정보 재검사·정리·품질·청크·최종 해시
→ 목표별 검토된 Graph·개념 벡터·캐시 확인
→ 현재 문단 키워드·임베딩·역할별 점수·근거 검사
→ 애매한 경우에만 제한된 최근 흐름 보완
→ 출력 Schema 검증·응답 직전 문맥 재검증
→ 원래 이벤트 귀속·임시 본문 참조 해제
```

## 기능 책임 분리

- #11의 전처리 모듈: 검증·정제·최종 qualityScore·contentHash. 기존 모듈과 테스트를 먼저 확인한다.
- #11의 관련성 모듈: 전처리 결과 소비·Graph·Hybrid·관련성 결과·관련성 캐시·평가 실행기.
- 김성현: 원천 추출 데이터 Adapter·목표 설정 보조·관련성 결과 이후 이탈/복귀 정책.
- Server: 인증·소유권·목표 조회·현재 실행 상태·기록.
- Client: 실제 DOM/OCR·캡처 권한·원천 중복·알림 UI.

Issue와 작업 Branch는 #11 하나로 통합한다. 검증·전처리와 관련성 판단은 별도 모듈·테스트로 연결하며 기존 전처리 코드를 복제하지 않는다.

## 모델·외부 연동

- 2026-10-21 사용자 지시: 임베딩 Client와 real CLI는 `POST /api/embed`만 호출한다. `/health`, `/api/tags`, `/api/show`, `/api/version`의 자동 조회를 제거하고 응답의 모델명·벡터 개수·차원·유한값·0벡터를 로컬에서 검증한다. revision·정밀도는 자동 원격 검증값이 아닌 설정이며, 서버 모델 교체 시 설정을 갱신하고 프로세스를 재시작한다.

- Python 3.11.6, FastAPI, Pydantic, pytest, HTTPX.
- 임베딩 모델: `Qwen/Qwen3-Embedding-4B`, FP16. 임의 목표 Graph 생성용 OpenAI 호환 LLM Client·Schema 검증·로컬 ChromaDB 재사용을 구현했다. 모델·API Key가 비어 있어 실제 호출과 생성 품질 검증은 비활성이며 외부 전송 고지·운영 저장 계약은 미정이다.
- Windows GPU 서버는 WSL2·Linux 방향이다. 학교의 GPU·VRAM·드라이버·CUDA·WSL2 GPU 접근·vLLM 버전은 실측 전에 확인한다.
- 모델은 시작 시 한 번 로드해 상주시킨다. 목표 개념 벡터를 버전별로 재사용하고 필요한 문단을 묶어 요청한다.
- vLLM 임베딩 API를 HTTPX Client로 호출한다. 생성/chat endpoint를 임베딩 계산에 사용하지 않는다.
- Qwen 모델 카드의 query instruction을 목표·개념 쪽에 적용하고 문단은 document 입력으로 처리하는 실험을 버전 관리한다. instruction 원문·차원 축소·정규화 변경도 캐시 무효화 및 회귀 평가 대상이다.
- timeout·제한된 재시도·동시 요청·입력 토큰 상한·유한 캐시 용량/TTL은 설정으로 관리한다. 모델 장애·미준비·잘못된 벡터는 UNCERTAIN과 상세 실패로 처리한다.
- 가짜 임베딩과 실제 모델 모드를 명확히 구분하고 모의 결과를 정확도·처리 속도 실측으로 보고하지 않는다.

공식 실행 참고: [Qwen 모델 카드](https://huggingface.co/Qwen/Qwen3-Embedding-4B), [vLLM Embed 예제](https://docs.vllm.ai/en/stable/examples/pooling/embed/), [vLLM GPU 설치](https://docs.vllm.ai/en/stable/getting_started/installation/gpu/). 사용한 설치 버전을 고정하고 해당 버전의 옵션을 확인한다.

## 예외 처리

| 상황 | 처리·필수 확인 |
| --- | --- |
| 잘못된 JSON·필드·enum | 안전한 Schema 오류, 원문 없는 메시지, 모델 미호출 |
| PAUSED/ENDED·목표/페이지 버전 불일치 | 현재 화면에 적용하지 않음, 모델 미호출 또는 진행 결과 폐기 |
| FAILED/UNSUPPORTED·검은/빈 캡처 신호 | 상세 원인과 UNCERTAIN, 실패를 UNRELATED로 변환하지 않음 |
| PARTIAL | 확보된 의미 있는 문단만 검사, 부족하면 UNCERTAIN |
| EXCLUDED/PRIVACY_BLOCKED | 본문·모델·대기 결과 차단, 해당 상태 유지 |
| 동일 이벤트 | 동일 버전 입력에서 멱등 처리; 다른 내용이면 재사용 거부 |
| 동일 contentHash | 사용자·목표·모델·Graph·정책·전처리 등이 같을 때만 관련성 재사용 |
| 모델 timeout·차원/NaN/0벡터 오류 | 오류 원인 분리, UNCERTAIN, 알림 없음 |
| 분석 중 화면/목표/제외 변경 | 완료 시 현재 문맥 재검사, 늦은 결과 폐기 |

## 학교에서 구현할 파일과 순서

아래는 구현 대상 제안이다. 파일이 없으면 생성하고 기존 파일이 있으면 실제 역할을 확인해 재사용한다. 이 목록은 이미 구현됐다는 뜻이 아니다.

| 단계 | 최소 작업 | 대상 |
| --- | --- | --- |
| 1 | 입력 Adapter·현재 문맥·출력 Schema 확인, 기존 전처리 재사용 | `AI/src/preprocessing/`, `AI/src/api/schemas.py` |
| 2 | 목표 템플릿·개념 역할·관계 근거 | `AI/src/analysis/goal_graph.py` |
| 3 | 순수 키워드·cosine·결합·라벨 근거 함수 | `AI/src/analysis/hybrid_scoring.py`, `relevance_analyzer.py` |
| 4 | 가짜 Client로 모델 경계와 파이프라인 테스트 | `AI/tests/test_relevance_pipeline.py`, `test_hybrid_scoring.py` |
| 5 | vLLM HTTP Client·batch·응답 검증·cache | `AI/src/models/embedding_client.py` |
| 6 | 합성 평가 CLI·고정 데이터·실험 설정 | `AI/evaluation/relevance_eval.py`, `relevance_cases.json`, `relevance_calibration.json` |
| 7 | 합의된 FastAPI 계약 연결 | `AI/src/api/routes.py`, `AI/src/main.py` |

Agent에게 요청할 때는 다음 지시를 함께 준다.

> 이 PLAN과 두 노션 원본의 범위만 구현한다. 기존 미커밋 변경을 지우지 않는다. 입력 계약 9개는 검증 문맥으로 분리하고 미합의 값을 운영 상수로 확정하지 않는다. 통합 #11의 기존 전처리는 재사용하며 Graph·키워드·벡터·최종 라벨을 순수 함수와 주입 가능한 Client로 분리한다. 외부 모델 없는 테스트를 먼저 만든다. 실제 Qwen 모드는 별도로 실행한다. 제외·민감·실패·stale에서 모델이 호출되지 않는지 검증한다. 기존 출력 계약을 임의 변경하지 않는다. 성공·실패 원문을 로그나 LangSmith/APM/checkpoint에 남기지 않는다. 실제 구현된 명령·테스트 결과만 REPORT에 남긴다.

## 평가 계획

### 필수 합성 사례

| 사례 | 기대 결과/검사 |
| --- | --- |
| useState 목표·객체 상태 업데이트 본문 | RELATED, DIRECT 근거 |
| 객체 상태 업데이트 목표·필요한 객체/전개 구문 설명 | 충분하면 RELATED, PREREQUISITE |
| JWT 목표·현재 JWT 403 해결 본문 | 충분하면 RELATED, SUPPORTING |
| 파인튜닝 목표·벡터 그래픽 포스터 본문 | 무관 근거가 충분하면 UNRELATED, 불충분하면 UNCERTAIN |
| Promise 목표·동명의 노래 가사 | 단어 일치만으로 RELATED 금지 |
| 관련 단어가 광고 한 문단에만 있음 | 해당 최고 점수만으로 RELATED 금지 |
| 제목 없는 충분한 OCR 본문 | 제목 부재만으로 탈락 금지 |
| 한 줄의 유효 코드·오류 메시지 | 길이만으로 탈락 금지 |
| 새 탭·메뉴만 남음·깨진 OCR | UNCERTAIN, 무관 근거로 쓰지 않음 |
| 메타데이터 aliases 2개 이상·반대 근거 없음 | 모델 미호출, 메타데이터 판정 |
| 메타데이터 한 단어·관련/무관 충돌 | UNCERTAIN, 필요한 경우 DOM/OCR로 진행 |
| 비활성 탭·창 또는 활성 대상 불명 | 모델 미호출, UNCERTAIN/INACTIVE_TARGET |
| BLACK_SCREEN/EMPTY_CAPTURE/OCR_FAILED 신호 | 모델 미호출, 실패 상세 상태 |
| PARTIAL이지만 의미 충분/부족 | 분석 가능 범위 유지/UNCERTAIN |
| 동일 clientRunId의 중복/내용 변경 | 멱등/잘못된 재사용 금지 |
| 같은 출처의 낮은 sequence가 늦게 도착 | stale 폐기, 현재 결과 미변경 |
| 같은 내용·다른 사용자/목표/정책 | 캐시 교차 사용 금지 |
| 모델 timeout·잘못된 차원·NaN·0벡터 | 안전 실패, UNRELATED 금지 |
| 분석 중 탭 전환·목표 변경·제외 진입 | 늦은 결과 현재 UI 적용 금지 |
| 목표 정보 없는 입력 | 목표 추측·임의 생성 금지 |
| 넓은 목표·Graph에 없는 표현 | 자동 범위 확대·무조건 무관 금지 |
| page text에 '규칙 무시'가 포함됨 | 데이터로만 사용, 지침 실행 금지 |
| EXCLUDED/PRIVACY_BLOCKED | 모델 호출 0회·본문 반환 없음 |
| 애매한 현재 문단·연결된 최근 흐름/연결 없는 흐름 | 제한된 보완/UNCERTAIN 유지 |

정답은 사람이 검수한다. 단순히 코드가 반환한 값을 정답으로 복사하지 않는다. 모델 없이 실행하는 테스트는 미리 정한 벡터·Client 응답으로 로직만 검증한다.

### 기록할 지표

#### 2026-10-12 16개 합성 Fixture 갱신

- `relevance_cases.json`을 `synthetic-school-v2`로 개정한다. 기존 평가 입력 필드를 유지하고 개발 8개·평가 8개 및 목표 템플릿 분리를 유지한다.
- 제목·명시 소제목·관련 본문 일치, 관련 제목/무관 본문·무관 제목/관련 본문·소제목/본문 충돌, 제목·소제목만 있는 입력, 제목 없는 OCR, 코드 fence/언어/entity 보존, 긴 제목·소제목의 커버리지 제외를 포함한다.
- 기존 선수·보조 개념, 적극적 무관 주제, 캡처 실패, PARTIAL, 제외·민감 입력도 일부 사례에 함께 배치한다. 16개는 전체 요구사항 평가 세트를 대신하지 않는다.
- fake 벡터는 미리 정의한 비교 대상을 표현하는 로직 검사용 입력이다. 전처리 후 TITLE·HEADING을 포함한 단위 순서/개수에 맞추며 real 모드에서는 사용하지 않는다.
- expected는 기획과 현재 보류 정책에 따른 초안이다. fake의 반환값을 복사해 실제 모델 정답으로 확정하지 않고 사람이 독립 검수한다. 숫자 정책·Graph·API 계약은 이번 데이터 갱신으로 변경하지 않는다.

- 라벨별 precision/recall, macro-F1, confusion matrix, UNCERTAIN 비율, 보조/선수 자료를 UNRELATED로 오판한 비율.
- 모델 초기 로딩·워밍업·warm embedding·전처리 포함 전체 요청·동시 요청 대기를 따로 측정한다.
- P50/P95·실제 임베딩 호출 수·캐시 hit·입력 토큰·GPU/VRAM·라이브러리/모델/정책/데이터 버전을 기록한다.
- 개발 세트에서 α/β·정규화·임계값을 조정한 뒤 설정을 고정해 별도 평가 세트에서 평가한다. 같은 정답 세트로 조정과 최종 성능 보고를 동시에 하지 않는다.
- 로딩 8.xx초·실행 0.10초는 기억한 참고값이며 실측 기준이나 합격 조건이 아니다. 합격 수치는 평가 후 팀과 결정한다.
- 실제 후속 알림 연동이 없으면 '잘못된 알림 0건'을 주장하지 않는다. 이 기능에서는 알림 미실행과 안전 상태만 검증한다.
- 후보 1~5·7은 기존 비교 기록이다. 다시 모든 방법을 만들 필요가 없으며 현재 구현은 Hybrid 6번이다.

## 학교 컴퓨터 작업 절차

### 코드 받기·환경 확인

새 폴더에서 아래처럼 받는다. 기존 clone에 미커밋 변경이 있으면 먼저 확인하며 자동 stash·reset·덮어쓰기를 하지 않는다.

```bash
git clone --branch feat/11-ai-preprocessing-relevance --single-branch https://github.com/What-a-move/FocusOn.git FocusOn
cd FocusOn
git status --short --branch
```

Windows에서는 WSL2 터미널에서 GPU 접근을 확인한다. 모델 다운로드·설치·워밍업은 수업 전 끝내는 편이 좋다. 인터넷·GPU가 없으면 모델 없는 단위 테스트만 하고 실제 성능 측정은 미완료로 남긴다.

```bash
nvidia-smi
python --version
```

AI 앱은 Python 3.11.6을 사용한다. 기존 pyenv 환경이 있으면 해당 버전/가상환경을 선택한다. 없다면 학교 권한 범위에서 준비한다. vLLM 환경은 앱과 분리할 수 있으며 호환되는 버전 조합을 확인한 뒤 고정한다. 이 지침이 설치 완료를 보장하지 않는다.

### 실행 인터페이스 — 구현 후 사용할 명령

아래 테스트 파일과 평가 CLI는 로컬 통합 #11 작업 공간에 구현했다. 실제 원격 게시 여부는 Git 상태·PR에서 확인한다. 현재 실행 도구는 결정 022에 따른 Ollama이며 vLLM은 후속 전환 단계다.

```bash
# 먼저 모델·네트워크 없는 테스트
PYTHONPATH=AI:. python -m pytest AI/tests -q

# Fake Client 결과: 로직 검증 전용
python -m AI.evaluation.relevance_eval --mode fake --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json

# 실제 모델: Ollama·학교 SSH 터널·정확한 모델 digest 환경 설정 필요
python -m AI.evaluation.relevance_eval --mode real --cases AI/evaluation/relevance_cases.json --config AI/evaluation/relevance_calibration.json
```

평가 CLI는 위 mode/cases/config와 `--split development|evaluation`을 지원한다. real 모드의 모델 digest 미설정·Ollama 연결 실패 시 fake로 대체하지 않는다. 지정 모델·F16·차원·digest·서비스 버전을 확인한다. 환경 변수 설정은 [학교 설치 지침](school-ollama-embedding-SETUP.md)을 따른다.

CLI 출력/보고서에는 caseId·정답 라벨·예측 라벨·상태·안전한 근거 코드·버전·점수·처리시간만 남긴다. 합성 Fixture 외 실제 DOM/OCR/검색어 원문은 저장하지 않는다. 영속 평가 산출물에 내부 임시 본문이나 사용자 목표 원문을 쓰지 않는다.

## 보안·개인정보

모든 테스트 Fixture는 합성 데이터다. 학교 계정의 이메일·실제 API Key·쿠키·토큰·개인 메시지·화면 캡처를 사용하지 않는다. 디버그 로그·ValidationError·HTTP tracing·LangSmith/APM에도 본문·목표 원문·비밀 값이 남지 않아야 한다.

본문·청크는 추론 중에만 사용하고 완료·취소 뒤 참조를 해제한다. 임시 캐시에 원문을 담거나 네트워크 복구 후 재전송 큐에 넣지 않는다. 유효한 캐시는 해시·벡터·최소 결과와 필요한 버전만 유한 용량/TTL로 관리하며 세션 종료·정책 변경에 정리한다.

민감 화면·제외 상태는 모델 사용 중에도 재확인한다. 사용자 판정 수정·삭제·수정 데이터 저장·그 기반 개인화는 현재 구현에서 제외한다.

## 완료 조건

### 학교 시간에 확인할 최소 완료

- [x] 노션 공통 입력을 읽고 검증된 실행 문맥과 분리했다. 학교 내부 Schema이며 9개 필드의 공개 합의는 별도다.
- [x] 통합 #11의 기존 전처리와 관련성 모듈을 내부 파이프라인으로 연결했다.
- [ ] 검토된 목표 템플릿·직접/선수/보조 Graph를 사용한다.
- [x] 키워드·벡터·역할별 결합과 기본 3라벨 로직이 테스트 가능하다. 숫자 정책은 미보정이다.
- [x] 제외·민감·추출 실패·stale 경로의 모델 미호출/본문 미노출을 로컬 테스트했다.
- [ ] 기존 팀 합의 출력과 원래 관찰 이벤트 연결을 검증했다.
- [x] Fake Client 단위·파이프라인 테스트와 실제 모델 평가를 구분했다.
- [x] 학교 주소·SSH 연결·model digest 미확보로 실제 Qwen3 4B·FP16 측정이 미실행임을 REPORT에 기록했다.
- [x] 로컬 평가 출력에 원문·비밀 값 없이 Python·데이터/설정 해시·모델 버전을 기록한다. 실제 GPU 정보는 학교 측정 후 추가한다.

### 전체 MVP 완료 전 추가 확인

- [ ] 애매한 경우 최근 3~5개 활동 요약 보완과 실패 처리를 검증했다.
- [ ] 합의된 FastAPI·Server·김성현 후속 정책 연결을 검증했다.
- [ ] 백엔드 컬럼 9개의 필요성·발급·전달·검증 책임을 합의했다.
- [ ] 독립 평가 결과로 임계값·결합 비율·품질 산식·성능 허용 기준을 결정했다.
- [ ] 모델/Graph/정책 변경·취소·역순·세션 종료에서 캐시를 검증했다.
- [ ] 실제 오류는 ERROR, 실제 결과·남은 제한은 REPORT에 기록했다.
- [ ] CONTEXT·NEXT_TASK·필요한 결정 기록을 실제 상태로 갱신했다.

적용 Rule ID: AI-PRIV-001/002, AI-PIPE-001, AI-FAIL-001, AI-STATE-001, AI-STALE-001, AI-LLM-001, AI-EVAL-001, AI-VERSION-001. 사용자 수정 우선 규칙 등 오래된 제안은 이번 구현 근거로 사용하지 않는다.

### 2026-10-21 사용자 요청: Graph 품질 지침과 임계값 하향

- 범위: 생성 지침의 역할·별칭·무관 활동 범주 보강, Prompt 변경 시 Graph 캐시 분리, DIRECT 0.62·선수/보조 0.65·무관 0.75 실험 기준 적용.
- 유지: 기존 JSON 계약, 키워드 산식·가중치·최소 코사인·본문 커버리지·충돌 보류·민감/품질/실패 처리.
- 검증: 기존 합성 14건은 개발용으로 유지하고 별도 합성 20건의 기대 라벨을 실행 전에 고정한다. 같은 실제 학교 벡터로 이전/하향 기준을 비교하며 평가 결과를 보고 기대 라벨이나 텍스트를 바꾸지 않는다. 사람 검수된 독립 최종 평가를 대신하지 않는다.
- 결과와 제한은 [트랜스포머 평가 및 Graph 품질 리포트](transformer-decoder-1h-evaluation-REPORT.md)에 누적한다. 외부 프로젝트·공개 API·수집 구현 변경은 포함하지 않는다.

### 2026-10-21 사용자 요청: 설명·명시 코드의 문맥 묶음

- 기존 개별 비교에 같은 입력의 인접 설명+코드 묶음을 추가하고, 원문 인접 관계와 UTF-8 크기를 확인한다. 키워드와 임베딩은 동일 묶음 텍스트로 계산한다.
- 개별 반대 근거는 유지하고 원래 ID·길이로 근거와 커버리지를 환산한다. 제목·표·소제목 경계를 넘거나 표식 없는 OCR의 코드 경계를 추측하지 않는다.
- 검증 완료: 235 passed·8 subtests passed, 실제 학교 API의 기존 causal mask 합성 사례에서 묶음 미사용 UNCERTAIN → 사용 RELATED. 전체 정확도·무관 검출 검증은 별도다.
- 별칭 생성 방식은 정식 기본형·자연스러운 표현 변형·실제 코드 표기와 중복 부풀림 방지 기준을 제안한다. 이번 구현에서 Graph 생성 Prompt 자체는 변경하지 않았다.

## 2026-10-21 현재 기능용 Streamlit 비교 화면

사용자 요청에 따라 기존 `similarity_streamlit.py`를 교체한다. 적용 규칙: AI-PRIV-002, AI-FAIL-001, AI-EVAL-001. 현재 6번 분석기를 직접 재사용하고 다른 후보는 기존 비교 방식으로 구분한다. JSON 컬럼과 판정 기준은 변경하지 않는다. 입력·기대 라벨 분리, OCR 실패·민감정보 모델 호출 차단, 명시적 비교 버튼, 결과·근거·시간 표시를 검증한다. 실제 수집·공개 API·독립 정확도는 범위에 포함하지 않는다. 실행법과 제한은 AI/README.md, 결과는 같은 기능 REPORT의 최신 절을 따른다.

## 2026-10-21 사용자 요청: 목표 기반 별칭 생성 보강만 재테스트

1번 방식만 적용한다. 목표에서 개념별 검색 가능한 별칭을 생성하는 Prompt를 보강하고 기존 버전과 캐시를 분리한다. 추가 검증 LLM·키워드 매칭 함수·점수 기준은 변경하지 않는다. 생성에는 현재 본문/기대 라벨을 넣지 않고, 기존 JWT와 고정 대조 사례로 실제 학교 API를 재평가한다. 적용·실패·검증 결과는 동일 기능 REPORT에 기록한다.
