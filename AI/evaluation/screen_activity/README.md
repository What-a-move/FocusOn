# 화면 활동 분류기 팀 베타

현재 활성 화면에서 공부 중(`STUDY`), 딴짓(`OFF_TASK`), 판단 보류(`UNCERTAIN`)를 구분한다. 일반적인 학습 활동을 판정하며 목표 문구와 세부 주제의 일치 여부는 채점하지 않는다.

- **팀원 Mac에서 평소처럼 공부하며 테스트**하려면 [무알림 베타 사용법](team_pilot/README.md)을 따른다. 세션이 끝나면 알림 후보의 정답과 실제 공부 시간을 표시할 수 있다.
- **예시 한 건의 모델 점수**를 보려면 아래 Streamlit을 실행한다. 개별 관측을 입력할 수 있고, 활성 탭과 전면 앱을 자동으로 확인하는 흐름은 팀원 베타에서 실행한다.
- 고정 Chrome 화면 22개에서 19개, 캔버스 화면 19개에서 10개를 맞혔다. 캔버스는 브라우저 안에서 그림·도형을 그리거나 회로를 배치하는 작업 영역이다. [화면별 평가와 한계](BETA_RESULTS.md)

## 학습한 모델

공개 `intfloat/multilingual-e5-small` 인코더를 고정하고 화면 텍스트와 입력 출처 등의 특징으로 3개 라벨의 분류 헤드를 학습했다. 가중치와 학습 데이터 체크섬은 [모델 정보](model/manifest.json)에서 확인할 수 있다.

- **학습 입력 10,168건**. 공부 3,755건, 딴짓 2,807건, 보류 3,606건이다.
- Chrome 탭 메타데이터 2,645건, Chrome DOM 2,841건, 앱 메타데이터 2,281건, 데스크톱 Apple Vision OCR 2,401건이다. 같은 페이지에서 읽은 메타데이터와 DOM은 각각 한 건으로 센다.
- 기존 4,743건을 포함한 전체 데이터로 분류 헤드 하나를 학습했다. 팀원 베타는 이 단일 헤드를 사용한다.

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
