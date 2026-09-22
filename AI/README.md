# FocusOn AI

FocusOn AI는 사용자의 학습 목표와 현재 화면·페이지 정보를 비교해 활동 관련성을 판단하고, 이후 학습 흐름과 사용자 상태를 보조 분석하는 Python 서비스다.

## 담당 기능

### MVP

- 자연어 학습 목표의 명확성 검사와 사용자 확인·추천·질문 보조
- Chrome Extension DOM·내부 로컬 OCR과 macOS Desktop Apple Vision OCR에서 전달된 정제 텍스트의 품질 확인
- 목표와 현재 콘텐츠의 관련성 판단
- 최근 7일 최소 집계와 규칙 기반 개인화 휴식 제안 후보 검토
- 규칙 기반 판단, 임베딩 유사도, 필요한 경우 LLM을 사용하는 단계적 분석
- 추출·분석·관련성·이탈 흐름·권장 행동을 분리한 상태와 근거 반환

### 후속 기능 또는 출시 결정 필요

- 페이지 이동과 체류 시간을 이용한 학습 흐름·목표 이탈 분석
- 클라이언트 MediaPipe가 계산한 얼굴·시선·자세 상태값의 보조 분석
- YouTube 자막, PDF 추출 텍스트 등 외부 콘텐츠 분석
- PDF·Canvas·이미지·슬라이드 대상 ColPali 비교 실험

카메라 출시 여부는 현재 기획서 안에서 MVP 포함과 출시 미정이 함께 기록되어 있어 팀 결정이 필요하다. AI는 어떤 경우에도 원본 영상·프레임·랜드마크를 받지 않고, 출시되더라도 클라이언트가 만든 최소 상태·집계값만 선택적으로 받는다.

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

- Python 디렉터리와 모듈 파일만 구성되어 있으며 실행 가능한 코드는 아직 없다.
- 관련성 API, 분석 기준, Prompt 계약은 문서 초안까지 작성했다.
- 목표 보조, 세션 설정, 두 분석 제외 모드와 개인화 휴식의 입력 계약은 기획 반영 초안까지 작성했다.
- FastAPI, Pydantic, 임베딩 모델, LLM 공급자, 캐시 저장소는 구현 전 검토·확정한다.
- 원본 화면과 원본 카메라 영상은 AI 입력·저장 대상으로 사용하지 않는다.
- ColPali는 정식 기본 경로가 아니라 시각 구조 콘텐츠의 실험 경로다.

## 문서

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
