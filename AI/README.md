# FocusOn AI

FocusOn AI는 사용자의 학습 목표와 현재 화면·페이지 정보를 비교해 활동 관련성을 판단하고, 이후 학습 흐름과 사용자 상태를 보조 분석하는 Python 서비스다.

## 담당 기능

### MVP

- Desktop OCR 결과와 Extension 페이지 텍스트 정제·품질 확인
- 목표와 현재 콘텐츠의 관련성 판단
- 규칙 기반 판단, 임베딩 유사도, 필요한 경우 LLM을 사용하는 단계적 분석
- `RELATED`, `UNRELATED`, `UNCERTAIN` 관련성과 점수·신뢰도·근거 반환

### 후속 기능

- 페이지 이동과 체류 시간을 이용한 학습 흐름·목표 이탈 분석
- 클라이언트 MediaPipe가 계산한 얼굴·시선·자세 상태값의 보조 분석
- YouTube 자막, PDF 추출 텍스트 등 외부 콘텐츠 분석

관련성, 학습 흐름, 사용자 상태는 하나의 판단으로 섞지 않고 별도 분석 모듈과 API로 분리한다.

## 분석 흐름

```text
Desktop / Extension
  → 로컬 OCR 또는 페이지 텍스트 추출
  → Spring Server의 검증·민감 정보 제거
  → AI 입력 검증·텍스트 정제
  → 규칙 기반 판단
  → 임베딩 유사도 판단
  → 애매한 경우 LLM 정밀 판단
  → Server가 결과 저장
  → Desktop이 상태·알림·리포트 표시
```

## 현재 상태

- Python 디렉터리와 빈 모듈 파일만 구성되어 있으며 실행 가능한 코드는 아직 없다.
- 공개 분석 API는 Notion 명세를 따르고, 내부 AI 호출 계약·분석 기준·Prompt 계약은 문서 초안 상태다.
- FastAPI, Pydantic, 임베딩 모델, LLM 공급자, 캐시 저장소는 구현 전 검토·확정한다.
- 원본 화면과 원본 카메라 영상은 AI 입력·저장 대상으로 사용하지 않는다.

## 문서

- [AI 문서 안내](docs/README.md)
- [현재 컨텍스트](docs/CONTEXT.md)
- [다음 작업](docs/NEXT_TASK.md)
- [결정 기록](docs/DECISION_RECORD.md)
- [API 명세](docs/api_spec.md)
- [분석 기준](docs/analysis_rules.md)
- [프롬프트 가이드](docs/prompt_guide.md)
