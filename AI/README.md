# FocusOn AI

FocusOn AI는 사용자의 학습 목표와 현재 화면·페이지 정보를 비교해 활동 관련성을 판단하고, 이후 학습 흐름과 사용자 상태를 보조 분석하는 Python 서비스다.

## 담당 기능

### MVP

- Extension DOM 결과와 허용된 로컬 Apple Vision OCR 텍스트의 정제·품질 확인
- 목표와 현재 콘텐츠의 관련성 판단
- 규칙 기반 판단, 임베딩 유사도, 필요한 경우 LLM을 사용하는 단계적 분석
- 추출·분석·관련성·이탈 흐름·권장 행동을 분리한 상태와 근거 반환

### 후속 기능

- 페이지 이동과 체류 시간을 이용한 학습 흐름·목표 이탈 분석
- 클라이언트 MediaPipe가 계산한 얼굴·시선·자세 상태값의 보조 분석
- YouTube 자막, PDF 추출 텍스트 등 외부 콘텐츠 분석
- PDF·Canvas·이미지·슬라이드 대상 ColPali 비교 실험

관련성, 학습 흐름, 사용자 상태는 하나의 판단으로 섞지 않고 별도 분석 모듈과 API로 분리한다.

## 분석 흐름

```text
Extension 제외·권한 검사
  → DOM 우선 추출
  → 정보 부족 시 허용된 로컬 Apple Vision OCR
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

- Python 디렉터리와 모듈 파일만 구성되어 있으며 실행 가능한 코드는 아직 없다.
- 관련성 API, 분석 기준, Prompt 계약은 문서 초안까지 작성했다.
- FastAPI, Pydantic, 임베딩 모델, LLM 공급자, 캐시 저장소는 구현 전 검토·확정한다.
- 원본 화면과 원본 카메라 영상은 AI 입력·저장 대상으로 사용하지 않는다.
- ColPali는 정식 기본 경로가 아니라 시각 구조 콘텐츠의 실험 경로다.

## 로컬 목표 보조 실행

`AI/.env`에 아래 두 Key를 입력하면 별도 Gateway 설정 없이 Streamlit 테스트 화면을 실행할 수 있다. Key 값은 저장소에 Commit하지 않는다.

```dotenv
TYPESAFE_API_KEY=your_typesafe_key
OPENAI_API_KEY=your_openai_key
```

확인 질문은 기본적으로 OpenAI에 최대 2건을 동시에 요청하고 먼저 도착한 유효한 질문 하나를 표시한다. 호출량을 1건으로 제한하려면 `OPENAI_QUESTION_PARALLELISM=1`을 설정한다. 실제 지연 개선과 추가 비용은 Live 평가가 필요하다.

Streamlit은 질문 문구 초안을 스트리밍으로 표시한다. 5초는 성능 목표이지 강제 종료 시점이 아니다. 유효한 모델 질문은 먼저 완성되는 즉시 표시하고, 기본 10초(`OPENAI_QUESTION_DEADLINE_SECONDS`)가 지나도록 유효한 질문이 없을 때만 로컬 기본 질문을 보여준다. 두 호출이 모두 일찍 실패해도 10초 전에는 기본 질문으로 바꾸지 않으며, 설정·인증 오류는 바로 표시한다. 이 대기 기준은 질문 생성 단계에만 적용된다.

```bash
cd AI
python -m streamlit run streamlit_app.py
```

## Laya 비교 판단

Streamlit 목표 설정 화면은 JEV와 같은 입력·네 가지 판단 질문을 로컬 `convaiinnovations/laya-multilingual`에도 전달해 비교 표로 표시한다. Laya 결과는 비교 전용이며 실제 상태, 질문·추천 생성, GoalProfile, 저장 흐름에는 사용하지 않는다.

로컬 수동 테스트에서 LangSmith에 목표 원문과 후속 답변을 함께 남겨 비교하려면 `AI/.env`에 `LANGSMITH_TRACING=true`, `LANGSMITH_TRACE_RAW_GOAL_TEXT=true`를 설정한다. 이 설정은 로컬 테스트에만 사용하며 공유·배포 환경에서는 `false`를 유지한다.

처음 실행하기 전 의존성을 설치해야 하며, 첫 Laya 비교 시 공개 모델 체크포인트를 내려받을 수 있다.

```bash
cd AI
python -m pip install -r requirements.txt
```

## 문서

- [AI 문서 안내](docs/README.md)
- [현재 컨텍스트](docs/CONTEXT.md)
- [다음 작업](docs/NEXT_TASK.md)
- [결정 기록](docs/DECISION_RECORD.md)
- [AI 개발 불변 규칙](docs/DEVELOPMENT_RULES.md)
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
