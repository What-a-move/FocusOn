# Extension 문서 안내

이 폴더는 Frontend(`apps`) 담당 범위 중 Chrome Extension 전용 설계와 작업 기록을 관리한다. 공통 프론트엔드 규칙은 `apps/docs/`에서 관리한다.

## 작업 시작 순서

1. `apps/docs/README.md`와 필요한 공통 프론트엔드 규칙을 읽는다.
2. `CONTEXT.md`를 읽고 현재 Extension 상태를 확인한다.
3. `NEXT_TASK.md`를 읽고 이번 작업 범위를 확인한다.
4. `DECISION_RECORD.md`를 읽고 브라우저 권한·수집 범위를 확인한다.
5. 새 기능이면 `templates/FEATURE_PLAN_TEMPLATE.md`를 복사해 기획서를 먼저 작성한다.
6. 실제 오류가 발생하면 오류마다 `templates/DEVELOPMENT_ERROR_TEMPLATE.md`로 기록한다.
7. 새 기능 개발과 테스트가 끝나면 `templates/RESULT_REPORT_TEMPLATE.md`로 결과를 작성한다.

## 문서 구조

```text
apps/extension/docs/
├── README.md
├── DEVELOPMENT_GUIDE.md
├── EXTENSION_ARCHITECTURE.md
├── MESSAGE_CONTRACT.md
├── MANIFEST_PERMISSION_POLICY.md
├── SERVICE_WORKER_LIFECYCLE.md
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
- Next.js의 서버·클라이언트 경계를 지키고, Tailwind 클래스와 컴포넌트의 역할이 읽히게 작성한다.
- 함수와 컴포넌트의 책임을 작게 유지해 다음 담당자가 쉽게 수정할 수 있도록 한다.
- 결과 리포트에는 이번 작업에서 배운 문법·개념과 유지보수 메모를 남긴다.

## Extension 전용 설계 문서

| 문서 | 확인할 내용 |
| --- | --- |
| `EXTENSION_ARCHITECTURE.md` | Popup·Service Worker·Content Script 책임 |
| `MESSAGE_CONTRACT.md` | 내부 Message Type·Payload·검증 규칙 |
| `MANIFEST_PERMISSION_POLICY.md` | Manifest·Host Permission 최소화 기준 |
| `SERVICE_WORKER_LIFECYCLE.md` | Worker 종료·복구·저장·15초 체류 처리 |

## 기능 문서 작성 규칙

- 새 기능 개발은 `기능명-PLAN.md` 작성과 확인 후 시작한다.
- 새 기능을 완료하면 `기능명-REPORT.md`를 한 번 작성한다.
- 실제 오류가 발생하면 오류마다 `기능명-오류명-ERROR.md`를 생성하고, 오류가 없으면 만들지 않는다.
- 단순 버그 수정에는 기능 결과 리포트를 작성하지 않는다.
- 파일명의 기능명과 오류명은 소문자 kebab-case를 사용한다.
- Chrome 권한, 수집 데이터, 제외 도메인, Desktop 연결 여부를 기획서에 명시한다.
- 페이지 본문을 수집하거나 분석하는 경우 최소 수집 범위와 중지 조건을 함께 작성한다.
- Desktop까지 함께 변경하면 하나의 Frontend 작업 범위인지 확인하고 영향받는 앱을 Issue와 PR에 적는다.

## Extension 전용 확인 항목

- Manifest V3 권한과 Host Permission
- 현재 탭·URL·페이지 제목 수집
- 페이지 이동과 15초 체류 재분석
- 분석 제외 도메인
- Content Script와 Service Worker 생명주기
- Desktop 앱 연결 끊김과 재연결
- Chrome의 권한·보안 제한
