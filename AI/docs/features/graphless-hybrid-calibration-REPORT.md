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
