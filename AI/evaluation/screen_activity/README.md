# 화면 활동 분류기 · 502건 후보

Chrome DOM과 Desktop Apple Vision OCR의 제목·본문으로 화면을 공부 중, 딴짓, 판단 보류로 분류합니다. 세션 목표의 관련성은 기존 API 계약에서 다룹니다. 이 후보는 로컬 Streamlit에서 검토할 수 있습니다.

## 학습 데이터

- `CHROME_DOM` 291건. 제목과 DOM 본문이 있는 280건, 제목만 남은 11건.
- `DESKTOP_APP_OCR` 211건. 창 제목과 OCR 본문이 모두 포함됩니다.
- `TAB_META`·`APP_META` 단독 입력 0건.
- 전체 502건. 공부 중 150건, 딴짓 154건, 판단 보류 198건.

E5 인코더와 단어·연속 두 단어의 출현 정보를 사용한 분류기입니다. 점수 1·2위 차이가 1%p 이하이면 보류합니다. 학습 결과로 생성한 분류 가중치와 모델 정보를 `model/`에 포함했습니다.

## 현재 관측 요청 형식

아래 예시는 요청에서 화면 관련 필드만 발췌한 것입니다. 전체 요청에는 `clientRunId`, `observedAt`, `sequence`, `guardStatus`가 포함되고, 전경 관측 기기에서는 `observationContextId`를 추가할 수 있습니다. [공유 타입 정의](../../../packages/shared-types/src/index.ts)

```json
{"contentSource":"CHROME_DOM","metadata":{"appName":"Google Chrome","pageTitle":"Spring Boot Reference","urlHost":"docs.spring.io"},"contentPayload":{"text":"Profiles allow you to use environment-specific configuration."}}
```

```json
{"contentSource":"DESKTOP_APP_OCR","metadata":{"appName":"Microsoft Word","windowTitle":"학습 기록"},"contentPayload":{"text":"프로필별 설정을 작성하고 활성 프로필별 동작을 확인했다."}}
```

```json
{"contentSource":"TAB_META","metadata":{"appName":"Google Chrome","pageTitle":"Spring Boot Reference","urlHost":"docs.spring.io"}}
```

```json
{"contentSource":"APP_META","metadata":{"appName":"Microsoft Word","windowTitle":"학습 기록"}}
```

로컬 모델의 학습 입력은 DOM·OCR입니다. 입력의 `pageTitle` 또는 `windowTitle`과 `contentPayload.text`를 사용합니다.

## 로컬 실행

Python 3.11 가상환경에서 실행합니다. 인코더 설치는 고정된 공개 E5 체크포인트를 가져와 실험 당시 형식으로 저장합니다.

```sh
cd AI/evaluation/screen_activity
python -m pip install -r requirements.txt
python setup_encoder.py
python -m streamlit run streamlit_app.py --server.address 127.0.0.1
```

## 검증 결과

- PR의 설치 스크립트가 생성한 인코더 파일 6개의 SHA-256이 실험 파일과 일치했습니다. 비교용 71개 입력의 라벨과 점수도 모두 일치했습니다.
- 최종 평가 28건 기준 228건 모델 15/28, 502건 모델 19/28. 후보 기준 공부 10/10, 보류 5/10, 딴짓 4/8.
- 로컬 세션 재생에서 지뢰찾기와 2048은 2/2회 포착했고, 새 Hextris 세션은 보류했습니다. 해당 세션의 공부·메뉴 구간 오알림 후보는 0회였습니다.
- 캔버스 도구 7개에서 작업 전후 제목과 본문이 같았고, 각 쌍의 분류 결과도 같았습니다.
