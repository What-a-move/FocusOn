# Desktop 문서 안내

이 폴더는 FocusOn macOS Desktop 담당 영역의 설계와 작업 기록을 관리한다.

## 작업 시작 순서

1. `CONTEXT.md`를 읽고 현재 Desktop 상태를 확인한다.
2. `NEXT_TASK.md`를 읽고 이번 작업 범위를 확인한다.
3. `DECISION_RECORD.md`를 읽고 이미 합의된 기술·동작을 확인한다.
4. 기능 작업이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 복사해 기획서를 먼저 작성한다.
5. 개발 중 오류는 `templates/DEVELOPMENT_ERROR_TEMPLATE.md`로 기록한다.
6. 개발과 테스트가 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
apps/desktop/docs/
├── README.md
├── CONTEXT.md
├── NEXT_TASK.md
├── DECISION_RECORD.md
├── templates/
│   ├── FEATURE_PLAN_TEMPLATE.md
│   ├── DEVELOPMENT_ERROR_TEMPLATE.md
│   └── RESULT_REPORT_TEMPLATE.md
└── features/
    └── 기능명-PLAN.md / 기능명-ERROR.md / 기능명-REPORT.md
```

## 기능 문서 작성 규칙

- 기능 개발은 기획서 작성과 확인 후 시작한다.
- 파일명은 `소문자-kebab-case-PLAN.md` 형식을 사용한다.
- 같은 기능의 기획서·오류 보고서·결과 리포트는 같은 기능명으로 묶는다.
- 화면, Electron, macOS 권한, 화면 캡처, 카메라 연동 여부를 기획서에 명시한다.
- Desktop 외 영역의 작업이 필요하면 Server·AI·Extension 담당 Issue를 연결한다.

## Desktop 전용 확인 항목

- macOS 권한 요청과 권한 거부 흐름
- 활성 앱 및 Bundle Identifier 확인
- 화면 캡처 생명주기
- 타이머와 학습 세션 상태 동기화
- 카메라 사용 동의와 분석 중지
- 앱 종료·재실행·네트워크 끊김 상황

