# 화면 활동 분류기 팀 베타

현재 활성 화면에서 공부 중(`STUDY`), 딴짓(`OFF_TASK`), 판단 보류(`UNCERTAIN`)를 구분한다. 일반적인 학습 활동을 판정하며 목표 문구와 세부 주제의 일치 여부는 채점하지 않는다.

- **팀원 Mac에서 평소처럼 공부하며 테스트**하려면 [무알림 베타 사용법](team_pilot/README.md)을 따른다. 세션이 끝나면 알림 후보의 정답과 실제 공부 시간을 표시할 수 있다.
- **예시 한 건의 모델 점수**를 보려면 아래 Streamlit을 실행한다. 개별 관측을 입력할 수 있고, 활성 탭과 전면 앱을 자동으로 확인하는 흐름은 팀원 베타에서 실행한다.
- 새 Chrome 화면, 캔버스, 모의 세션, 메모리·지연 측정은 [베타 평가 결과](BETA_RESULTS.md)에 정리했다.

## 학습한 모델

공개 `intfloat/multilingual-e5-small` 인코더를 고정하고 화면 텍스트와 입력 출처 등의 특징으로 3개 라벨의 분류 헤드를 학습했다. 가중치와 학습 데이터 체크섬은 [모델 정보](model/manifest.json)에서 확인할 수 있다.

- **학습 4,743건**. 공부 1,527건, 딴짓 1,423건, 보류 1,793건.
- 탭 메타데이터 1,405건, Chrome DOM 1,349건, 앱 메타데이터 1,020건, 데스크톱 Apple Vision OCR 969건.
- 직전 4,648건에 탭 메타데이터 47건과 Chrome DOM 48건을 추가했다. 추가한 95건은 합성 입력 69건과 공개 페이지의 활성 탭에서 수집·정리한 입력 26건이다. 같은 페이지의 메타데이터와 DOM은 서로 다른 입력으로 센다.

## 로컬 Streamlit

Python 3.11에서 저장소 루트를 기준으로 실행한다. 설치 시 고정 리비전의 공개 인코더를 내려받아 체크섬을 검사한다.

```sh
cd AI/evaluation/screen_activity
python3.11 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python setup_encoder.py
.venv/bin/python -m streamlit run streamlit_app.py --server.address 127.0.0.1
```

## 어떤 입력으로 판정하나

아래는 모델을 실험할 때 쓰는 최소 관측 예시다. 제품의 전체 요청 형식은 [공유 타입](../../../packages/shared-types/src/index.ts)에 있다.

```json
{"contentSource":"TAB_META","metadata":{"appName":"Google Chrome","pageTitle":"Spring Boot Reference","urlHost":"docs.spring.io"}}
```

```json
{"contentSource":"CHROME_DOM","metadata":{"appName":"Google Chrome","pageTitle":"Spring Boot Reference","urlHost":"docs.spring.io"},"contentPayload":{"text":"Profiles allow you to use environment-specific configuration."}}
```

```json
{"contentSource":"APP_META","metadata":{"appName":"미리보기","windowTitle":"웹 검색 실습.pdf"}}
```

```json
{"contentSource":"DESKTOP_APP_OCR","metadata":{"appName":"미리보기","windowTitle":"웹 검색 실습.pdf"},"contentPayload":{"text":"검색 결과의 title, href, body를 읽어 출력합니다."}}
```

Chrome에서는 현재 탭의 메타데이터를 먼저 판정한다. 보류면 해당 탭 DOM을 확인한다. 데스크톱에서는 전면 앱의 메타데이터에서 시작해 필요한 경우 그 창의 Apple Vision OCR을 사용한다. Chrome OCR 경로는 차단한다.

상위 두 모델 점수의 차이가 1%p 이하이면 보류한다. 베타는 확정 점수가 0.60 미만일 때도 보류하며, 메타데이터에서 딴짓이 나오면 본문 확인으로 넘긴다. 수집이 허용된 전면 네이티브 VS Code는 앱 정책으로 공부 처리한다. 이 정책에는 모델 점수가 없다.
