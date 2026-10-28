# FocusOn Graphless 임계값 보정 실험

개발 목표로만 기준을 선택한 뒤 동결하고 별도 목표를 평가했다. 합성 자료의 실험 결과이며 실제 화면 정확도가 아니다.

## 선택한 기준

```json
{
  "keyword_boost": 0.2,
  "related_passage_threshold": 0.35,
  "unrelated_cosine_threshold": 0.25,
  "min_related_cosine": 0.3,
  "unrelated_coverage_threshold": 0.7,
  "unrelated_min_body_chars": 80
}
```

개발 무관→관련 0건 조건 충족: True

## 지표

| 분할 | 사례 | Macro-F1 | 관련 재현율 | 무관→관련 | 보류율 | 파이프라인 실패 | 종단 일치율 |
|---|---:|---:|---:|---:|---:|---:|---:|
| development · 초기 기준 | 24 | 0.7778 | 50.0% | 0 | 50.0% | 0 | 75.0% |
| development · 개발 보정 기준 | 24 | 0.9564 | 100.0% | 0 | 20.8% | 0 | 95.8% |
| evaluation · 초기 기준 | 24 | 0.7709 | 58.3% | 0 | 50.0% | 0 | 75.0% |
| evaluation · 개발 보정 기준 | 24 | 0.8110 | 100.0% | 1 | 12.5% | 0 | 83.3% |

## development 사례

| 사례 | 기대 | 실제 | 상태 | 사유 |
|---|---|---|---|---|
| cal-react-direct-object | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-direct-array | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-direct-functional | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-support-spread | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-support-filter | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-support-debug | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-react-unrelated-cooking | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-react-unrelated-title-trap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-react-unrelated-overlap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-react-uncertain-generic | UNCERTAIN | UNCERTAIN | COMPLETED | INSUFFICIENT_EVIDENCE |
| cal-react-uncertain-mixed | UNCERTAIN | UNCERTAIN | COMPLETED | CONFLICTING_EVIDENCE |
| cal-react-uncertain-fragment | UNCERTAIN | UNCERTAIN | COMPLETED | INSUFFICIENT_EVIDENCE |
| cal-transport-direct-reliability | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-direct-datagram | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-direct-selection | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-support-latency | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-support-ports | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-support-capture | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-unrelated-travel | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-transport-unrelated-title-trap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-transport-unrelated-overlap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| cal-transport-uncertain-generic | UNCERTAIN | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| cal-transport-uncertain-mixed | UNCERTAIN | UNCERTAIN | COMPLETED | CONFLICTING_EVIDENCE |
| cal-transport-uncertain-fragment | UNCERTAIN | UNCERTAIN | COMPLETED | INSUFFICIENT_EVIDENCE |

## evaluation 사례

| 사례 | 기대 | 실제 | 상태 | 사유 |
|---|---|---|---|---|
| eval-querydsl-direct-builder | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-direct-expression | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-direct-query | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-support-qtype | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-support-join | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-support-debug | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-unrelated-music | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| eval-querydsl-unrelated-title-trap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| eval-querydsl-unrelated-overlap | UNRELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-uncertain-generic | UNCERTAIN | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-querydsl-uncertain-mixed | UNCERTAIN | UNCERTAIN | COMPLETED | CONFLICTING_EVIDENCE |
| eval-querydsl-uncertain-fragment | UNCERTAIN | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-direct-definition | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-direct-characteristic | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-direct-eigenspace | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-support-determinant | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-support-nullspace | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-support-check | RELATED | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-unrelated-travel | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| eval-eigen-unrelated-title-trap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| eval-eigen-unrelated-overlap | UNRELATED | UNRELATED | COMPLETED | UNRELATED_COVERAGE_MET |
| eval-eigen-uncertain-generic | UNCERTAIN | RELATED | COMPLETED | RELATED_EVIDENCE_FOUND |
| eval-eigen-uncertain-mixed | UNCERTAIN | UNCERTAIN | COMPLETED | CONFLICTING_EVIDENCE |
| eval-eigen-uncertain-fragment | UNCERTAIN | UNCERTAIN | COMPLETED | INSUFFICIENT_EVIDENCE |

## 제한사항

- Manually authored synthetic development/evaluation data; not independently human-labelled real screens.
- Evaluation goals were excluded from threshold selection. Reusing these results for tuning makes them development data.
- Pipeline failures are excluded from semantic confusion but counted as failures and wrong in end-to-end accuracy.
- UNCERTAIN is a normal semantic label. SKIPPED empty-content abstentions are eligible; failed model requests are not.


## 기존 React24와 대조군 회귀

- 같은 React 목표·본문·기대 라벨로 실제 Qwen을 호출했다. 기존4/24(16.7%) → 변경23/24(95.8%), +79.2%p. 관련 보류20→1건. 이미 진단에 사용된 합성 회귀 자료이며 독립 정확도가 아니다.
- 새 처리시간14.797초(24건), 기존 단발12.529초다. 네트워크·예열 조건이 통제되지 않았고 제목 문맥 벡터가 추가되어 속도 개선을 주장하지 않는다.
- 남은 react-state-065m은 계산된 속성 이름과 폼 핸들러 자료다. 본문H0.3246·문맥H0.3292로 H0.35에 미달해 UNCERTAIN. 이 한 사례에 맞춰 기준을 다시 낮추지 않았다.
- 기존 대조군8건은 의미 라벨8/8 일치. 메뉴만 있는 사례는 정상 EMPTY_CONTENT/SKIPPED라 UI 집계의 기대 일치는7/8이다. FAILED를 정답 UNCERTAIN으로 보상하지 않는다.
- 게임 상태창은 상태 단어가 겹쳐도 UNRELATED가 되며, 관련 제목+요리 본문도 UNRELATED를 유지했다.

## 지표 해석과 남은 문제

- 앞의 보정 전 지표는 새 전처리·문맥·키워드 규칙에 기존 문단 임계값V0.55/H0.60을 적용한 상태다. 보정 후와의 차이는 같은 벡터의 임계값 효과이며 기존 v2 전체와 신규48건을 직접 비교한 것이 아니다.
- 개발용 React/TCPUDP24건만72개 후보 선택에 사용했다. 다른 목표 QueryDSL/고유값24건은 고정 기준으로 확인했다. 별도 평가 일치율75.0%→83.3%, Macro-F1 0.771→0.811, 관련 재현율58.3%→100%다.
- 별도 평가에서 무관6건 중1건을 RELATED로 오판했고 정보부족6건 중3건도 RELATED로 오판했다. RELATED precision75.0%다. 운영 정확도가 검증되었다고 볼 수 없다.
- 불일치: eval-querydsl-unrelated-overlap, eval-querydsl-uncertain-generic, eval-querydsl-uncertain-fragment, eval-eigen-uncertain-generic. 평가 결과를 보고 기준을 다시 조정하지 않았다.
- 다음은 실제 DOM/OCR·독립 사람 검수·같은 용어의 무관 문서·정보 부족을 포함한 새 평가다. 이 자료를 후속 보정에 쓰면 개발 자료로 전환하고 별도 평가셋을 준비한다.

## 구현 범위

- Streamlit 전처리는 penalize_removed_fragments=False / preserve_extracted_markup=True를 명시한다. 운영 전처리 기본값·공개 JSON 계약은 유지한다.
- 정상 본문 뒤의 메뉴 제거가 품질0.25를 만드는 문제와 추출 텍스트의 JSX/제네릭 삭제를 수정했다. 빈 입력·낮은 OCR 신뢰도·깨진 문자·민감정보 차단은 유지한다.
- 같은 화면의 pageTitle, 없으면 windowTitle과 실제 Markdown 소제목 경로를 해당 본문과 연결한다. 반복 소제목도 구조 경계로 보존하고 평문 OCR에서 제목을 생성하지 않는다. 제목은 목표 키워드나 query 지침에 섞지 않는다.
- 원래 본문 코사인이 최소 기준을 넘어야 문맥 점수를 근거로 인정한다. 무관 본문을 제목만으로 RELATED로 올리지 않으며 커버리지는 원래 본문 ID로 한 번만 센다.
- 상태/객체/값 등 명시한 넓은 용어는0.25, 나머지 목표 용어는1.0이다. 작은 규칙 목록이며 전공 의미를 이해하는 분류기는 아니다. 어떤 단어도 낮은 V의 무관 판정을 단독으로 막지 않는다.
- 본문 최고점·문맥 최고점·제목 참고점·실제 통과 근거 점수를 나눠 표시한다.
- 보정 설정은 scoring/keyword/model/dimension/revision/instruction과 연결한다. 모델 정보 API를 새로 조회하지 않는다. Graph·OpenAI·Chroma 호출은0회다.
- FAILED만 있거나3라벨 검증 근거가 없는 자료로 보정 성공 설정을 저장하지 않는다. 보정 도구에서 목표 ID가 lab-goal로 바뀌는 문제도 수정하고 모델 호출 전 검증 테스트를 추가했다.

## 로컬 검증

관련 전처리·입력 Adapter·코드 문맥·판정·보정·Streamlit UI 회귀는 **157 passed, 16 subtests passed**다. 버전 불일치 설정·전부 실패한 개발 자료·소제목 문맥 누출도 검사했다. diff/py_compile 검사를 통과했다. 모델 없는 회귀와 실제 Qwen 합성 평가를 구분한다.

## 최종 웹 확인

새 프로세스의 실제 Streamlit에서 같은 React24를 비교하기 버튼으로 재실행해23/24·95.8%·분석 실패0건을 확인했다. 전체11.464초, 임베딩 왕복/검증10.241초, 로컬1.223초였다. 단발 측정이며 기존 실행 대비 속도 개선율로 사용하지 않는다.


## 2026-10-23 메타데이터 전용 실험

사용자 요청으로 Streamlit에만 `title-hybrid-v1`을 추가했다. TAB_META·APP_META는 실제 pageTitle/windowTitle을 정제·중복 제거해 임베딩하며 본문 길이·본문 커버리지 조건은 적용하지 않는다. 앱 이름·도메인·우발적으로 들어온 text는 제목 근거로 쓰지 않는다. DOM/OCR의 기존 보정값과 판정은 유지한다. 공개 입력/출력 계약·운영 분석기는 변경하지 않았다.

메타데이터 기준은 **미보정 실험값** V≥0.55 및 H≥0.60, 보강0.20, 무관V≤0.25다. 일반/빈 제목은 추가 수집이 필요한 완료된 UNCERTAIN이다. 실제 제목에 관련·무관 근거가 함께 있으면 UNCERTAIN, 추출 PARTIAL은 무관 확정하지 않는다. 제외·민감·stale·모델 실패 검사를 유지한다. 메타데이터 화면에는 커버리지를 0% 대신 해당 없음으로 표시한다.

### 실제 Qwen 결과

바탕화면 개발 주제 메타데이터 JSON 10개×24건(기대 RELATED160 / UNCERTAIN80)을 라벨·원문·기준 변경 없이 평가했다. 실제 학교 Qwen3-Embedding-4B 2560차원 /api/embed 호출이며 configured revision은 서버 실검증값이 아니다. 기대 라벨은 추론에 넣지 않았다.

| 분야 | 일치 / 24 |
|---|---:|
| 프로그래밍_React_객체상태 | 24 |
| 네트워크_TCP_UDP | 20 |
| 인프라_로드밸런싱 | 10 |
| 백엔드_QueryDSL | 24 |
| AI_트랜스포머디코더 | 15 |
| 데이터베이스_PostgreSQL_인덱스 | 24 |
| 보안_JWT인증 | 16 |
| 운영체제_프로세스스레드 | 24 |
| 인프라_Docker | 10 |
| 알고리즘_BFS_DFS | 24 |

- 전체191/240(79.58%), COMPLETED240, FAILED0. 관련160건 중111 RELATED /49 UNCERTAIN, 모호80건은80 UNCERTAIN.

- 전체50.193초, 임베딩 API 왕복·검증 합48.266초, 실제POST145회. 일반 제목80건은 사전 보류하고 반복된 제목·목표 벡터는 실행 메모리 캐시로 재사용했다. 단회·캐시 혼합 시간이며 순수 GPU 처리시간이나 독립 지연 측정이 아니다.

- TCP4, 로드밸런싱14, 디코더9, JWT8, Docker14가 관련→보류 불일치다. 본문 길이/커버리지 차단이 아니며 제목 V/H의 관련 기준 미달이다. 평가 후 임계값 재조정은 하지 않았다.

- **한계:** 합성 제목에 주제 접두사가 반복되고 시간 순서는 가정이다. 실제 2시간 수집·실사용 정확도 검증이 아니다. 무관 정답0건이므로 UNRELATED 재현율·오탐 안전성·3라벨 Macro-F1로 일반화하지 않는다. 일반 제목 감지는 작은 어휘 규칙으로 임의 제목 전체에 대한 보장이 없다. 다음 평가는 접두사 없는 실제 제목과 같은 용어의 다른 주제·무관·모호 화면을 사람 검수해 분리해야 한다.

- 결과 수치는 `AI/evaluation/metadata-experiment-results.json`에 저장했다. 원문 제목·목표·벡터는 결과 파일에 저장하지 않았다. 관련 회귀와 UI 검증113 passed.

## 2026-10-25 도입 문장의 약한 무관 후보 검토

- 범위: Streamlit Graphless 실험의 근거 정책 `adjacent-intro-v1`. 점수 버전 v3, 키워드 보강0.20, 관련 V0.30/H0.35, 무관 V0.25·커버리지70%·80자는 유지했다. 입력 계약·메타데이터 판정·운영 분석기는 이번 변경 대상이 아니다.
- 재현 원인: `embedded-rx-overrun-debug-002`의 19자 도입 문장이 V0.227806으로 무관 근거가 돼, 수신 처리/오버런 설명의 관련 근거와 충돌했다. 도입+다음 설명을 합쳐 임베딩해도 V0.249여서 기존 무관 기준을 벗어나지 못했다.
- 새 기준: 원본에서 바로 이어진 같은 BODY/OCR, 한 줄 명사형 도입 표현(종결 문장부호 없음), 더 긴 다음 설명(문장부호 있음)을 대상으로 한다. 원문 두 벡터 간 코사인≥0.50이며 다음 설명의 목표 V>0.25이면 도입 표현만 `INTRO_CONTEXT_DEFERRED`로 보류한다. 원래 낮은 점수·무관 후보 여부는 보존하며 관련 근거로 올리지 않는다. 제거된 문단/명시 소제목/코드 경계를 넘어 붙이지 않는다. 기존 벡터끼리 로컬 계산하므로 API 요청은 늘지 않는다.
- 0.50은 이번 개발 중 정한 문서 간 연속성의 임시 기준이다. 문장 구조와 의미 유사도는 실제 다른 활동의 확정 증거가 아니므로 새 목표·실제 화면으로 검수해야 한다.

| 대조 사례 | 이전 | 변경 후 | 기대 |
|---|---|---|---|
| embedded-rx-overrun-debug-002 | UNCERTAIN | RELATED | RELATED |
| uart-direct | RELATED | RELATED | RELATED |
| youtube-game | UNRELATED | UNRELATED | UNRELATED |
| mixed-paragraphs | UNCERTAIN | UNCERTAIN | UNCERTAIN |
| mixed-short-heading | UNCERTAIN | UNCERTAIN | UNCERTAIN |
| insufficient | UNCERTAIN | UNCERTAIN | UNCERTAIN |
| unrelated-first-sentence | UNCERTAIN | UNCERTAIN | UNCERTAIN |
| unrelated-two-paragraphs | UNRELATED | UNRELATED | UNRELATED |

- 실제 학교 Qwen F16/2560차원, POST /api/embed 56회, 전체36.342초. 변경 후 실제 벡터를 받고 같은 벡터에서 이전 근거 정책만 재현했다. 기존 개발24건23/24·기존 평가24건20/24 유지, 대조8건7/8→8/8. 전체50/56→51/56(89.3→91.1%), 실패0. 새로운 독립 평가 정확도가 아니라 알려진 사례와 합성 회귀 결과다.
- 남은 기존 오판5건은 모두 RELATED 과판정: cal-transport-uncertain-generic, eval-querydsl-unrelated-overlap, eval-querydsl-uncertain-generic, eval-querydsl-uncertain-fragment, eval-eigen-uncertain-generic. 이번 수정으로 해결됐다고 보지 않는다.
- 002는1POST·7.133초(임베딩 왕복/응답검증6.187초, 로컬0.945초)였다. 이 로컬 시간에는 최초 Kiwi 초기화가 포함될 수 있다. 다른 대조7건은0.345~0.920초다. 원격 GPU 처리시간·모델 로드시간은 이 클라이언트 계측만으로 분리하지 않았다. 기존 벡터 재사용으로 정책만 비교한 시간은 모델 속도가 아니다.
- 자동 회귀362 passed,16 subtests passed. 후보 보류가 관련 근거를 만들지 않는지, 명확한 짧은 게임/요리·혼합 화면의 충돌 유지, 원본 인접성·문장/출처 경계를 확인했다.
- 입력: `AI/evaluation/negative-candidate-cases.json`. 결과: `AI/evaluation/graphless-negative-policy-results.json`(원문/벡터 없음). 목표와 JSON을 그대로 Streamlit에 넣어 재현할 수 있다. fixture hash는 UTF-8 JSON의 키 정렬 직렬화 기준이다.

## 2026-10-25 FocusOn더미데이터 25개·600건 전체 재평가

- 대상은 바탕화면 `FocusOn더미데이터`의 JSON25개, 각24건이다. 기대 RELATED460·UNRELATED60·UNCERTAIN80건이며 파일의 SHA-256은 지난10월23일 평가와 모두 같고 실행 전후에도 같았다.
- 실제 학교 Qwen `qwen3-embedding:4b-fp16`/2560차원으로 현재 판정 경로를 실행했다. 새 벡터 캐시로 시작해 이번 순차 실행 안에서만 재사용했다. OpenAI/Graph 호출0회. 원문·벡터는 결과 파일에 저장하지 않았다.
- 점수·기대 라벨·본문을 고정하고 같은 실제 벡터에서 이전 근거 정책을 재현했다. 이 비교는 모델/API 조건 차이를 제거한 정책 변경 비교다. 과거10월23일 집계도 별도 기록해 확인했다.
- 전체 소요151.191초, 실제POST382회, 재시도1건. 최종 상태{'COMPLETED': 600}. 반복 벡터 캐시를 포함한 전체 실행 시간이며 순수 GPU 시간은 아니다. 최초 일괄 실행에서 `react-state-045m-dom-mixed` 1건이 MODEL_OR_SCORING_FAILED였고 동일 입력 재시도로 COMPLETED/UNRELATED를 받았다. 최종 집계는 재시도를 반영하며 최초 실패 기록은 결과 JSON의 retries에 보존했다.

| 지표 | 이전 정책 | 현재 정책 |
|---|---:|---:|
| 기대 일치 | 456/600 | 456/600 |
| 기대 불일치·미완료 | 144 | 144 |
| 불일치율 | 24.00% | 24.00% |
| 기대 일치율 | 76.00% | 76.00% |
| 공부를 딴짓으로 오판 | 1/460 (0.22%) | 1/460 (0.22%) |
| 공부를 UNCERTAIN으로 보류 | 143/460 (31.09%) | 143/460 (31.09%) |
| 딴짓 탐지 | 60/60 | 60/60 |

- 불일치율 감소는 **0.00%p**, 기존 불일치 대비 상대 감소율은 **0.00%**다. 계산식은 `(이전 불일치율−현재 불일치율)/이전 불일치율×100`이다.
- 여기서 불일치율은 공부→UNCERTAIN까지 포함한다. 공부를 딴짓으로 확정한 오탐률과 구분한다. 실패는 정답 UNCERTAIN으로 세지 않는다.

### 현재 라벨별 결과

| 기대 라벨 | 맞음 | 다른 라벨·미완료 | RELATED 반환 | UNRELATED 반환 | UNCERTAIN 반환 |
|---|---:|---:|---:|---:|---:|
| RELATED | 316/460 | 144 | 316 | 1 | 143 |
| UNRELATED | 60/60 | 0 | 0 | 60 | 0 |
| UNCERTAIN | 80/80 | 0 | 0 | 0 | 80 |

### 파일별 비교

| JSON 파일 | 이전 맞음/24 | 현재 맞음/24 | 이전 불일치율 | 현재 불일치율 |
|---|---:|---:|---:|---:|
| 01_프로그래밍_React_객체상태_2시간.json | 23 | 23 | 4.17% | 4.17% |
| 01_프로그래밍_React_객체상태_2시간_DOM_공부딴짓혼합.json | 24 | 24 | 0.00% | 0.00% |
| 01_프로그래밍_React_객체상태_2시간_메타데이터만.json | 24 | 24 | 0.00% | 0.00% |
| 02_네트워크_TCP_UDP_2시간.json | 20 | 20 | 16.67% | 16.67% |
| 02_네트워크_TCP_UDP_2시간_DOM_공부딴짓혼합.json | 20 | 20 | 16.67% | 16.67% |
| 02_네트워크_TCP_UDP_2시간_메타데이터만.json | 20 | 20 | 16.67% | 16.67% |
| 03_인프라_로드밸런싱_2시간.json | 23 | 23 | 4.17% | 4.17% |
| 03_인프라_로드밸런싱_2시간_DOM_공부딴짓혼합.json | 23 | 23 | 4.17% | 4.17% |
| 03_인프라_로드밸런싱_2시간_메타데이터만.json | 10 | 10 | 58.33% | 58.33% |
| 04_백엔드_QueryDSL_2시간.json | 20 | 20 | 16.67% | 16.67% |
| 04_백엔드_QueryDSL_2시간_DOM_공부딴짓혼합.json | 21 | 21 | 12.50% | 12.50% |
| 04_백엔드_QueryDSL_2시간_메타데이터만.json | 24 | 24 | 0.00% | 0.00% |
| 05_AI_트랜스포머디코더_2시간.json | 21 | 21 | 12.50% | 12.50% |
| 05_AI_트랜스포머디코더_2시간_DOM_공부딴짓혼합.json | 22 | 22 | 8.33% | 8.33% |
| 05_AI_트랜스포머디코더_2시간_메타데이터만.json | 15 | 15 | 37.50% | 37.50% |
| 06_데이터베이스_PostgreSQL_인덱스_2시간_DOM_공부딴짓혼합.json | 12 | 12 | 50.00% | 50.00% |
| 06_데이터베이스_PostgreSQL_인덱스_2시간_메타데이터만.json | 24 | 24 | 0.00% | 0.00% |
| 07_보안_JWT인증_2시간_DOM_공부딴짓혼합.json | 12 | 12 | 50.00% | 50.00% |
| 07_보안_JWT인증_2시간_메타데이터만.json | 16 | 16 | 33.33% | 33.33% |
| 08_운영체제_프로세스스레드_2시간_DOM_공부딴짓혼합.json | 12 | 12 | 50.00% | 50.00% |
| 08_운영체제_프로세스스레드_2시간_메타데이터만.json | 24 | 24 | 0.00% | 0.00% |
| 09_인프라_Docker_2시간_DOM_공부딴짓혼합.json | 6 | 6 | 75.00% | 75.00% |
| 09_인프라_Docker_2시간_메타데이터만.json | 10 | 10 | 58.33% | 58.33% |
| 10_알고리즘_BFS_DFS_2시간_DOM_공부딴짓혼합.json | 6 | 6 | 75.00% | 75.00% |
| 10_알고리즘_BFS_DFS_2시간_메타데이터만.json | 24 | 24 | 0.00% | 0.00% |

### 변경 영향과 남은 문제

- 판정이 달라진 사례0건. 구조상 검토 대상이 된 도입/설명 쌍0개, 실제 무관 근거 보류0개. 이번 기준은 문장형 본문 전체를 올려주는 기준이 아니므로 도입 표현 조건이 없는 화면에는 영향을 주지 않는다.
- 과거10월23일 결과와 이번 이전 정책 재현의 라벨 차이0건. 과거 결과와 파일 해시까지 확인했으므로 같은25개 파일을 비교한 것이다.
- 현재 불일치 사유: {'INSUFFICIENT_EVIDENCE': 22, 'METADATA_INSUFFICIENT': 49, 'UNRELATED_COVERAGE_MET': 1, 'CONFLICTING_EVIDENCE': 72}.
- RELATED 보류의 해결과 이번 도입 문장 오보류 해결은 별도다. 메타데이터 제목 점수 부족이나 본문 관련 근거 자체의 부족은 이번 조건으로 개선되지 않는다. 이번 결과에 맞춰 임계값이나 정답을 바꾸지 않았다.
- 합성 자료이며 같은 공부/딴짓 본문을 여러 목표·출처에 반복한 사례가 있다. 600건의 독립 실제 화면 평가나 실사용 정확도로 일반화하지 않는다.
- [전체 결과 JSON](../../evaluation/all-dummy-negative-policy-results.json), [600건 CSV](../../evaluation/all-dummy-negative-policy-cases.csv). 결과에는 파일·caseId별 기대/이전/현재 라벨, 사유·점수·실행 시간 및 실패·재시도 여부가 들어 있다.

## 2026-10-25 의존적인 후속 설명 구현·전체 재평가

- 사용자 승인으로 `dependent-followup-v2`를 Streamlit Graphless 경로에 적용했다. 메타데이터/공개 계약/기존 운영 Graph 분석기와 수치 임계값은 그대로다. 기존 도입 표현 처리도 유지한다.
- Kiwi의 형태소·완결 문장 여부와 작은 참조/추상 명사·동작 어휘 규칙으로 후속 설명 후보를 만든다. 원본에서 인접한 동일 BODY/OCR만 묶고 제목·명시 소제목·코드·제거된 문단·출처 경계를 넘지 않는다. 새 주제 명사·영문 식별자·알 수 없는 행동은 이 경로에서 제외한다. 문장 세 개의 정확 일치 예외나 caseId별 규칙은 없다.
- 앞 문단 자체와 두 문단 묶음이 모두 기존 V0.30/H0.35를 통과할 때만 낮은 유사도 후속 문장의 무관 후보를 `FOLLOWUP_CONTEXT_DEFERRED`로 보류한다. 문맥 묶음·후속 문장을 관련 근거로 올리지 않고 원점수·분모·원본 관련 글자 수를 유지한다. 다른 무관 근거는 충돌 판정을 유지하고 중립 후속 설명을 다음 문장의 관련 근거로 전파하지 않는다.
- 한국어 수동 어휘 휴리스틱이다. Kiwi가 주제·의미를 이해하는 분류기는 아니며 새로운 표현·영어·OCR 오탈자는 보류가 남을 수 있다. 현재 알려진 개발 자료에 맞춰 확대한 규칙이므로 독립 실제 화면 검수가 필요하다.

### 실제 Qwen 전체600건 결과

| 지표 | 직전 adjacent-intro-v1 | dependent-followup-v2 |
|---|---:|---:|
| 기대 일치 | 456/600 | 528/600 |
| 일치율 | 76.00% | 88.00% |
| 불일치율(공부→보류 포함) | 24.00% | 12.00% |
| 공부→UNCERTAIN | 143/460 | 71/460 |
| 공부→UNRELATED | 1/460 | 1/460 |
| 딴짓 탐지 | 60/60 | 60/60 |
| 모호 정답 일치 | 80/80 | 80/80 |

- 불일치율 감소 **12.00%p**, 기존 불일치 대비 상대 감소 **50.00%**. 문제72건만 UNCERTAIN→RELATED로 변경됐고 다른528건의 라벨은 같다. 일치율의 상대 개선율과 불일치율의 상대 감소율을 혼용하지 않는다.
- 라벨별 현재 결과: RELATED460건 중388 RELATED·71 UNCERTAIN·1 UNRELATED, UNRELATED60건은60 UNRELATED, UNCERTAIN80건은80 UNCERTAIN이다.
- 본문 경로360건265→337건 일치(73.61→93.61%), 메타데이터240건191→191건(79.58% 유지). 남은72건은 메타데이터 관련 보류49·본문 관련 근거 부족22·QueryDSL 공부 무관 오판1이다.
- 원본25개 파일 해시는 이전 평가 및 실행 전후 모두 같다. 실제 학교 Qwen F16/2560차원, POST /api/embed 381회, 전체185.774초. 최초 실패2건은 동일 입력 재시도로 성공했고 최종 실패0건이다. 각 실패·재시도는 결과 JSON에 보존했다.
- 후속 문맥 벡터90개를 기존 배치에 추가했고, 실제 무관 근거 보류는72개다. 입력 배치 상한에 따라 요청 수도 늘 수 있다. 캐시 혼합 전체 지연이며 원격 GPU 시간·순수 네트워크 시간은 분리하지 않았다. 직전151.191초와 이번185.774초는 서로 다른 시점의 단회 실행이므로 지연 차이를 전부 변경 비용으로 단정하지 않는다.
- 비교는 같은 실제 벡터에서 FOLLOWUP_CONTEXT만 제외해 직전 정책을 재현했다. 직전600건 결과와 재현 라벨 차이0건이다. 새 벡터 캐시에서 시작하고 이번 순차 실행 안에서만 재사용했으며 OpenAI/Graph 호출은0회다.

### 별도 대조·자동 검증

- MQTT/Redis의 새 목표·후속 표현·게임 혼합·단독 일반 문장16건은10→16건 일치했다. 개선된6건은 참조 표현의 새 문장이고 실제 딴짓2건·혼합4건·단독 일반 문장2건도 기대와 일치했다. 이 자료도 독립 사람 검수 전 합성 대조다.
- 기존8건 대조는8/8, 기존48건은43/48 유지했다. 기존48건의 RELATED 과판정5건은 해결되지 않았다.
- 자동 테스트376 passed,16 subtests passed. 문맥만 높고 앞 본문이 미달인 경우·제목만 관련·출처/명시 경계·새 주제/행동·불완전 문장·중립 문장 연쇄 전파·중복 글자 가산을 방지하는 회귀를 포함한다.
- [새 대조 입력](../../evaluation/followup-context-cases.json) · [대조 결과](../../evaluation/followup-context-control-results.json) · [600건 결과 JSON](../../evaluation/all-dummy-followup-policy-results.json) · [600건 CSV](../../evaluation/all-dummy-followup-policy-cases.csv). 결과 JSON은 원문·벡터를 저장하지 않는다. 새 대조 입력은 목표별 groups 구조이며 CLI 검증용이다.

### 파일별 기대 일치

| 파일 | 이전 /24 | 변경 후 /24 |
|---|---:|---:|
| 01_프로그래밍_React_객체상태_2시간.json | 23 | 23 |
| 01_프로그래밍_React_객체상태_2시간_DOM_공부딴짓혼합.json | 24 | 24 |
| 01_프로그래밍_React_객체상태_2시간_메타데이터만.json | 24 | 24 |
| 02_네트워크_TCP_UDP_2시간.json | 20 | 20 |
| 02_네트워크_TCP_UDP_2시간_DOM_공부딴짓혼합.json | 20 | 20 |
| 02_네트워크_TCP_UDP_2시간_메타데이터만.json | 20 | 20 |
| 03_인프라_로드밸런싱_2시간.json | 23 | 23 |
| 03_인프라_로드밸런싱_2시간_DOM_공부딴짓혼합.json | 23 | 23 |
| 03_인프라_로드밸런싱_2시간_메타데이터만.json | 10 | 10 |
| 04_백엔드_QueryDSL_2시간.json | 20 | 20 |
| 04_백엔드_QueryDSL_2시간_DOM_공부딴짓혼합.json | 21 | 21 |
| 04_백엔드_QueryDSL_2시간_메타데이터만.json | 24 | 24 |
| 05_AI_트랜스포머디코더_2시간.json | 21 | 21 |
| 05_AI_트랜스포머디코더_2시간_DOM_공부딴짓혼합.json | 22 | 22 |
| 05_AI_트랜스포머디코더_2시간_메타데이터만.json | 15 | 15 |
| 06_데이터베이스_PostgreSQL_인덱스_2시간_DOM_공부딴짓혼합.json | 12 | 24 |
| 06_데이터베이스_PostgreSQL_인덱스_2시간_메타데이터만.json | 24 | 24 |
| 07_보안_JWT인증_2시간_DOM_공부딴짓혼합.json | 12 | 24 |
| 07_보안_JWT인증_2시간_메타데이터만.json | 16 | 16 |
| 08_운영체제_프로세스스레드_2시간_DOM_공부딴짓혼합.json | 12 | 24 |
| 08_운영체제_프로세스스레드_2시간_메타데이터만.json | 24 | 24 |
| 09_인프라_Docker_2시간_DOM_공부딴짓혼합.json | 6 | 24 |
| 09_인프라_Docker_2시간_메타데이터만.json | 10 | 10 |
| 10_알고리즘_BFS_DFS_2시간_DOM_공부딴짓혼합.json | 6 | 24 |
| 10_알고리즘_BFS_DFS_2시간_메타데이터만.json | 24 | 24 |

## 2026-10-25 모호한 메타데이터 제목 사전 보류

`title-hybrid-v2`는 정보성 검사에서만 앱·브라우저 접미사를 제외한다. `학습 자료 - Preview`, `참고 페이지 - Google Chrome`, `문서 내용 - Google Chrome`처럼 일반 표현만 남는 제목은 임베딩 전에 UNCERTAIN으로 처리한다. 일부 제목만 일반적이면 해당 제목을 관련·무관 근거에서 제외한다. 임베딩 입력과 수치 임계값은 기존대로 유지한다.

새 주제 10개 메타데이터 파일의 동일 120건을 재평가했다. 기존 결과는 `new-topic-accuracy-results.json`, 이번 결과는 `new-topic-metadata-prefilter-results.json`이다. 입력 파일과 기대 라벨은 수정하지 않았으며 SHA-256을 확인했다.

| 항목 | 기존 | 최종 수정 |
| --- | ---: | ---: |
| 기대 라벨 일치 | 36/120 | 66/120 |
| 기대 일치율 | 30.00% | 55.00% |
| 기대 불일치율 | 70.00% | 45.00% |
| 모호한 제목을 UNRELATED로 오판 | 30/30 | 0/30 |
| 모호한 제목을 UNCERTAIN으로 처리 | 0/30 | 30/30 |

- 최종 평가에서 30건 교정, 기존 정답의 오답 전환 0건, 실패 0건이다. 모호한 30건의 모델 호출은 0회다. 나머지 제목은 기존 학교 Qwen API를 사용했고 전체 90 POST·32.873초다. OpenAI·모델 metadata 재조회는 없다.
- 임베딩 입력에서도 접미사를 제거한 최초 진단은 기대 일치 65/120이고 기존 무관 1건이 보류로 변했다. 최종 구현은 접미사 제거를 정보성 검사로 한정해 해당 변화를 없앴다.
- 관련 자동 회귀는 127 passed다. 알려진 제목 외에 제공된 `appName`, 다른 앱 이름을 포함한 유의미한 제목, 관련 제목과 일반 제목의 공존, 명확한 무관 제목, 실패·민감·제외 경로를 확인했다.
- 남은 54건은 공부→보류 46건·공부→무관 6건·딴짓→보류 2건이다. 전체 오판이 없어진 것은 아니다. 이번 수정은 메타데이터 합성 개발 사례의 일반 제목 오판에 한정하며, DOM/OCR 전체 재평가나 실제 클라이언트 수집·추가 요청 연결 검증은 포함하지 않는다.
