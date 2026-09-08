# Extension 문서 안내

이 폴더는 FocusOn Chrome Extension 담당 영역의 설계와 작업 기록을 관리한다.

## 작업 시작 순서

1. `CONTEXT.md`를 읽고 현재 Extension 상태를 확인한다.
2. `NEXT_TASK.md`를 읽고 이번 작업 범위를 확인한다.
3. `DECISION_RECORD.md`를 읽고 브라우저 권한·수집 범위를 확인한다.
4. 기능 작업이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 복사해 기획서를 먼저 작성한다.
5. 개발 중 오류는 `templates/DEVELOPMENT_ERROR_TEMPLATE.md`로 기록한다.
6. 개발과 테스트가 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
apps/extension/docs/
├── README.md
├── DEVELOPMENT_GUIDE.md
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

## 개발·학습 규칙

Next.js, Tailwind CSS, TypeScript를 공부하면서 개발할 때는 [`DEVELOPMENT_GUIDE.md`](./DEVELOPMENT_GUIDE.md)를 기준으로 한다.

- 어려운 문법과 유지해야 하는 판단에는 초보자가 이해할 수 있는 짧은 한국어 주석을 작성한다.
- 코드 자체로 충분히 설명되는 줄에는 불필요한 주석을 달지 않는다.
- `any`를 피하고 데이터 모양을 타입으로 설명한다.
- Next.js의 서버·클라이언트 경계를 지키고, Tailwind 클래스와 컴포넌트의 역할이 읽히게 작성한다.
- 함수와 컴포넌트의 책임을 작게 유지해 다음 담당자가 쉽게 수정할 수 있도록 한다.
- 결과 리포트에는 이번 작업에서 배운 문법·개념과 유지보수 메모를 남긴다.

## 기능 문서 작성 규칙

- 기능 개발은 기획서 작성과 확인 후 시작한다.
- 파일명은 `소문자-kebab-case-PLAN.md` 형식을 사용한다.
- 같은 기능의 세 문서는 같은 기능명으로 묶는다.
- Chrome 권한, 수집 데이터, 제외 도메인, Desktop 연결 여부를 기획서에 명시한다.
- 페이지 본문을 수집하거나 분석하는 경우 최소 수집 범위와 중지 조건을 함께 작성한다.

## Extension 전용 확인 항목

- Manifest V3 권한과 Host Permission
- 현재 탭·URL·페이지 제목 수집
- 페이지 이동과 15초 체류 재분석
- 분석 제외 도메인
- Content Script와 Service Worker 생명주기
- Desktop 앱 연결 끊김과 재연결
- Chrome의 권한·보안 제한
