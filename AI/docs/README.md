# AI 문서 안내

FocusOn AI 영역의 분석 기준, 입력·출력 계약, 프롬프트, 평가 결과를 기록한다.

## 작업 시작 순서

1. `CONTEXT.md`에서 현재 AI 구조와 제한사항을 확인한다.
2. `NEXT_TASK.md`에서 이번 작업 범위와 선행 조건을 확인한다.
3. `DECISION_RECORD.md`에서 모델·분석 기준·데이터 처리 결정을 확인한다.
4. 기능 작업이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 기준으로 기획서를 먼저 작성한다.
5. 오류는 `templates/DEVELOPMENT_ERROR_TEMPLATE.md` 형식으로 기록한다.
6. 평가와 구현이 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
AI/docs/
├── README.md
├── CONTEXT.md
├── NEXT_TASK.md
├── DECISION_RECORD.md
├── api_spec.md
├── analysis_rules.md
├── prompt_guide.md
├── templates/
│   ├── FEATURE_PLAN_TEMPLATE.md
│   ├── DEVELOPMENT_ERROR_TEMPLATE.md
│   └── RESULT_REPORT_TEMPLATE.md
└── features/
    └── 기능명-PLAN.md / 기능명-ERROR.md / 기능명-REPORT.md
```

## AI 전용 확인 항목

- 입력 데이터의 출처와 최소 수집 범위
- OCR·페이지 텍스트의 정제와 품질 기준
- 목표와 화면·페이지의 관련성 판단 기준
- 규칙·임베딩·LLM의 호출 순서와 Fallback 조건
- 분석 결과의 상태값·점수·신뢰도
- 임계값과 `UNCERTAIN` 처리
- 학습 흐름 분석과 MediaPipe 보조 분석의 분리
- 외부 콘텐츠 API의 입력·비용·실패 처리
- 프롬프트·모델 버전 관리
- 평가 데이터와 재현 가능한 테스트
- 원본 화면·카메라 영상의 미저장 원칙

## 분석 기능 구분

| 기능 | 입력 | 출력 | 개발 순서 |
| --- | --- | --- | --- |
| 현재 활동 관련성 | 목표, 앱·도메인·제목, 정제된 텍스트 | 관련성 상태·점수·신뢰도 | MVP 1순위 |
| OCR 텍스트 정제 | 클라이언트 OCR 결과 | 정제 텍스트·품질 상태 | 관련성 분석과 함께 구현 |
| 학습 흐름·이탈 | 이전 활동, 현재 활동, 체류 시간 | 흐름 상태·이탈 근거 | 관련성 분석 이후 |
| 사용자 상태 보조 | MediaPipe가 계산한 얼굴·시선·자세 상태값 | 보조 상태·신뢰도 | 후속 기능 |
| 외부 콘텐츠 | YouTube 자막·PDF 추출 텍스트 | 분석 가능한 정제 텍스트 | 후속 기능 |

각 기능은 별도의 PLAN·ERROR·REPORT 문서와 별도의 분석 모듈로 관리한다. 원본 화면과 카메라 영상은 어떤 기능에서도 AI 서버로 전달하지 않는다.
