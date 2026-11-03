# Desktop 문서 안내

이 폴더는 Frontend(`apps`) 담당 범위 중 macOS Desktop 전용 설계와 작업 기록을 관리한다. 공통 프론트엔드 규칙은 `apps/docs/`에서 관리한다.

## 작업 시작 순서

1. `apps/docs/README.md`와 필요한 공통 프론트엔드 규칙을 읽는다.
2. `CONTEXT.md`를 읽고 현재 Desktop 상태를 확인한다.
3. `NEXT_TASK.md`를 읽고 이번 작업 범위를 확인한다.
4. `DECISION_RECORD.md`를 읽고 이미 합의된 기술·동작을 확인한다.
5. 새 기능이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 복사해 기획서를 먼저 작성한다.
6. 실제 오류가 발생하면 오류마다 `templates/DEVELOPMENT_ERROR_TEMPLATE.md`로 기록한다.
7. 새 기능 개발과 테스트가 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
apps/desktop/docs/
├── README.md
├── DEVELOPMENT_GUIDE.md
├── ELECTRON_ARCHITECTURE.md
├── IPC_CONTRACT.md
├── MACOS_PERMISSION_FLOW.md
├── WINDOW_LIFECYCLE.md
├── CONTEXT.md
├── NEXT_TASK.md
├── DECISION_RECORD.md
├── templates/
│   ├── FEATURE_PLAN_TEMPLATE.md
│   ├── DEVELOPMENT_ERROR_TEMPLATE.md
│   └── RESULT_REPORT_TEMPLATE.md
└── features/
    └── 기능명-PLAN.md / 기능명-오류명-ERROR.md / 기능명-REPORT.md
```

## 개발·학습 규칙

Next.js, Tailwind CSS, TypeScript를 공부하면서 개발할 때는 [`DEVELOPMENT_GUIDE.md`](./DEVELOPMENT_GUIDE.md)를 기준으로 한다.

- 어려운 문법과 유지해야 하는 판단에는 초보자가 이해할 수 있는 짧은 한국어 주석을 작성한다.
- 코드 자체로 충분히 설명되는 줄에는 불필요한 주석을 달지 않는다.
- `any`를 피하고 데이터 모양을 타입으로 설명한다.
- Next.js의 서버·클라이언트 경계와 Electron Renderer·Preload·Main의 책임을 지킨다.
- 함수와 컴포넌트의 책임을 작게 유지해 다음 담당자가 쉽게 수정할 수 있도록 한다.
- 결과 리포트에는 이번 작업에서 배운 문법·개념과 유지보수 메모를 남긴다.

## Desktop 전용 설계 문서

| 문서 | 확인할 내용 |
| --- | --- |
| `ELECTRON_ARCHITECTURE.md` | Main·Preload·Renderer 책임과 보안 경계 |
| `IPC_CONTRACT.md` | IPC Channel·Payload·응답·Listener 규칙 |
| `MACOS_PERMISSION_FLOW.md` | Camera·Screen Recording·Notification 권한 흐름 |
| `WINDOW_LIFECYCLE.md` | Main·Floating Window와 Settings Route 생성·종료 |

## 기능 문서 작성 규칙

- 새 기능 개발은 `기능명-PLAN.md` 작성과 확인 후 시작한다.
- 새 기능을 완료하면 `기능명-REPORT.md`를 한 번 작성한다.
- 실제 오류가 발생하면 오류마다 `기능명-오류명-ERROR.md`를 생성하고, 오류가 없으면 만들지 않는다.
- 단순 버그 수정에는 기능 결과 리포트를 작성하지 않는다.
- 파일명의 기능명과 오류명은 소문자 kebab-case를 사용한다.
- 화면, Electron, macOS 권한, 화면 캡처, 카메라 연동 여부를 기획서에 명시한다.
- Extension까지 함께 변경하면 하나의 Frontend 작업 범위인지 확인하고 영향받는 앱을 Issue와 PR에 적는다.
- Server·AI 작업이 필요하면 해당 담당 Issue를 연결한다.

## Desktop 전용 확인 항목

- macOS 권한 요청과 권한 거부 흐름
- 활성 앱 및 Bundle Identifier 확인
- 화면 캡처 생명주기
- 타이머와 학습 세션 상태 동기화
- 카메라 사용 동의와 분석 중지
- 앱 종료·재실행·네트워크 끊김 상황
