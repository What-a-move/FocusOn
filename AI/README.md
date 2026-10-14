# FocusOn AI

FocusOn AI는 사용자의 학습 목표와 현재 화면·페이지 정보를 비교해 활동 관련성을 판단하고, 이후 학습 흐름과 사용자 상태를 보조 분석하는 Python 서비스다.

## 담당 기능

### MVP

- 자연어 학습 목표의 명확성 검사와 사용자 확인·추천·질문 보조
- Chrome Extension DOM·내부 로컬 OCR과 macOS Desktop Apple Vision OCR에서 전달된 정제 텍스트의 품질 확인
- 목표와 현재 콘텐츠의 관련성 판단
- MVP 기본 휴식 제안에 필요한 최소 결과 제공
- 규칙 기반 판단, 임베딩 유사도, 필요한 경우 LLM을 사용하는 단계적 분석
- 추출·분석·관련성·이탈 흐름·권장 행동을 분리한 상태와 근거 반환

### 후속 기능 또는 출시 결정 필요

- 최근 7일 최소 집계와 규칙 기반 개인화 휴식 추천(P1 후보)
- 페이지 이동과 체류 시간을 이용한 학습 흐름·목표 이탈 분석
- 클라이언트 MediaPipe가 계산한 얼굴·시선·자세 상태값의 보조 분석
- YouTube 자막, PDF 추출 텍스트 등 외부 콘텐츠 분석
- PDF·Canvas·이미지·슬라이드 대상 ColPali 비교 실험

카메라 로컬 분석은 Notion 기준 선택 동의 기반 MVP 후보이며 출시 전 별도 승인이 필요하다. 승인된 경우 AI에는 ON·유효 세션·비제외 상태에서 생성한 5분 버킷 또는 세션 종료 최소 요약의 집계값(`activeStudySeconds`, `cameraEnabledSeconds`, `validSignalSeconds`, `sustainedAwayEventCount`, `localNudgeShownCount`, `cameraState`, `signalQuality`, `consentVersion`, `sourceVersion`)만 전달한다. `EXCLUDED`의 카메라 집계·전송은 별도 승인 전 금지하고, `PRIVACY_BLOCKED`에서는 카메라 분석·집계·전송을 중단한다. 원본 영상·프레임·랜드마크·연속 시선·얼굴 방향 좌표는 전달하지 않는다.

관련성, 학습 흐름, 사용자 상태는 하나의 판단으로 섞지 않고 별도 분석 모듈과 API로 분리한다. `SUPPORTING`은 AI 내부의 보조 학습 라벨이며 제품 표시에서는 `RELATED`의 보조 유형으로 매핑한다. `OFF_TASK`는 제품의 `UNRELATED`, `UNAVAILABLE`은 제품의 `UNCERTAIN`과 상세 처리 실패 상태로 매핑한다.

## 분석 흐름

```text
Client별 제외·권한 검사
  → Chrome: DOM 우선, 부족할 때 Extension 내부 로컬 OCR
  → macOS Desktop: 허용된 활성 앱 화면의 Apple Vision 로컬 OCR
  → 개인정보·품질 검사
  → Spring Server의 인증·소유권·버전 검증
  → AI 입력 검증·텍스트 정제
  → 규칙 기반 판단
  → 임베딩 유사도 판단
  → 애매한 경우 LLM 또는 FocusSessionAgent 정밀 판단
  → 관련성·흐름·행동 상태 분리
  → Server가 결과 저장
  → Extension·웹 대시보드가 상태·알림·기록 표시
```

## 현재 상태

- `dev` 기준 목표·관련성 AI 서비스는 아직 골격이다. Issue #29 브랜치의 선행 학습 정리 코드는 새 커밋에서 되돌렸다. 현재는 합성 데이터 Streamlit 시연과 **고정된 가짜 근거만 전송하는 선택적 OpenAI 생성 테스트**가 있다. 실제 화면 데이터·FastAPI·Server·Client 제품 연결은 미구현이다.
- 관련성 API, 분석 기준, Prompt 계약은 문서 초안까지 작성했다.
- 목표 보조와 세션·분석 상태 계약은 Notion MVP 기준으로 갱신했다. 개인화 휴식은 P1 후보 초안이다.
- FastAPI, Pydantic, 임베딩 모델, LLM 공급자, 캐시 저장소는 구현 전 검토·확정한다.
- 원본 화면과 원본 카메라 영상은 AI 입력·저장 대상으로 사용하지 않는다.
- ColPali는 정식 기본 경로가 아니라 시각 구조 콘텐츠의 실험 경로다.

## 문서

### #29 로컬 시연

합성 데이터로 종료 후 정리 흐름과 근거 부족·중복·제외·실패를 직접 확인한다. 기본 시연은 외부 모델을 호출하지 않는다. 별도 OpenAI 테스트 버튼은 **코드에 고정된 가짜 목표와 근거만** 전송하며 편집한 입력은 보내지 않는다. 실제 화면 분석·Server·저장소는 연결되지 않았으며 제품 데이터는 입력하지 않는다.

```bash
python3 -m venv /tmp/focuson-summary-demo-venv
/tmp/focuson-summary-demo-venv/bin/python -m pip install -r AI/demos/requirements.txt
/tmp/focuson-summary-demo-venv/bin/python -m streamlit run AI/demos/learning_summary_streamlit.py
```

선택적 OpenAI 시험은 로컬 실행 환경에 `OPENAI_API_KEY`가 있을 때만 버튼이 활성화된다. 실제 호출은 버튼을 눌렀을 때에만 일어난다. 기본 시험 모델은 `gpt-5-mini`이고 `FOCUSON_SUMMARY_MODEL`로 바꿀 수 있다. API 응답 저장은 끄고(`store=false`), 요청은 20초 제한·자동 재시도 없이 실행한다. 실제 사용자 화면 데이터의 제공자·보존·고지 승인은 DOM 추출 연동 시점에 별도로 진행한다.

단위 테스트:

```bash
PYTHONPATH=AI python3 -m unittest discover -s AI/tests -p 'test_learning_summary*.py' -v
```


- [AI 문서 안내](docs/README.md)
- [현재 컨텍스트](docs/CONTEXT.md)
- [다음 작업](docs/NEXT_TASK.md)
- [결정 기록](docs/DECISION_RECORD.md)
- [AI 개발 규칙 초안](docs/DEVELOPMENT_RULES.md)
- [목표·세션·회차 모델](docs/goal_session_spec.md)
- [콘텐츠 수집·추출 계약](docs/content_acquisition_spec.md)
- [상태 모델](docs/state_model.md)
- [API 명세](docs/api_spec.md)
- [분석 기준](docs/analysis_rules.md)
- [FocusSessionAgent 실행 계약](docs/focus_session_agent_spec.md)
- [사용자 피드백·개인화 명세](docs/feedback_personalization_spec.md)
- [AI 학습 노트 생성 명세](docs/session_note_spec.md)
- [데이터 수명](docs/data_lifecycle.md)
- [프롬프트 가이드](docs/prompt_guide.md)
- [평가 명세](docs/evaluation_spec.md)
