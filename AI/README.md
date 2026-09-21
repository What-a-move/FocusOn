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
