# Frontend 폴더 구조

FSD(Feature-Sliced Design)의 의존 방향 규칙만 가볍게 가져와 3층으로 나눈다(Issue #183). 위층만 아래층을 가져다 쓴다.

```text
app/  →  features/  →  shared/
```

## 구조

```text
apps/desktop/
├── app/            Next 라우트. 화면 하나를 불러오기만 한다
├── features/       화면 단위 기능 (dashboard, goal, session, report, notes, records, settings, onboarding, floating)
│   └── 기능명/      컴포넌트·그 기능만 쓰는 API·CSS를 한 폴더에 평평하게 둔다
├── shared/         두 개 이상의 기능이 같이 쓰는 것
│   ├── api/        Main 창구(window.focusOn) 래퍼와 데이터 훅
│   ├── ui/         사이드바·설정 틀·불러오기 실패·동의 원문 같은 공용 컴포넌트
│   ├── lib/        오류 문구·도메인 규칙·선택지 상수 같은 순수 함수·데이터
│   └── styles/     여러 화면이 불러오는 전역 CSS
├── electron/       Main·Preload·계약 타입 (FSD 밖)
├── native/         macOS 관측 도우미 (FSD 밖)
├── tests/electron/
└── public/

apps/extension/
├── app/            팝업 라우트. 첫 화면(app/page.tsx)이 연결·사이트 허용·세션 화면을 고른다
├── features/       pairing(연결 코드), session(세션 표시·사이트 허용)
├── shared/         api(서버 요청·토큰·연결), lib(로거·세션 모델), ui(로그 복사), styles(팝업 공통 CSS)
├── extension/      background·content 스크립트와 그 공통 규칙 (FSD 밖)
├── scripts/
├── tests/
└── public/         manifest와 아이콘
```

## 규칙

- `features/` 끼리는 서로 import 하지 않는다. 다른 기능의 코드가 필요하면 그 코드를 `shared/`로 옮긴다.
- `shared/`는 `features/`를 import 하지 않는다. Desktop `electron/`, Extension `extension/`도 `features/`를 import 하지 않는다.
- 한 기능만 쓰는 코드는 그 기능 폴더에 둔다. "나중에 같이 쓸 것 같아서" 미리 `shared/`로 옮기지 않는다.
- 위 규칙은 각 앱 `eslint.config.mjs`의 `no-restricted-imports`가 검사한다. `pnpm lint`에서 오류가 난다.
- 기능 폴더 안을 `components/`·`hooks/`·`services/`로 다시 나누지 않는다. 파일이 10개를 넘으면 그때 나눈다.
- Extension의 `shared/`는 팝업 화면 공통이고, `extension/shared/`는 background·content 스크립트 공통이다.

## 위치 선택 기준

| 종류 | 위치 |
| --- | --- |
| 라우트 | `app/` |
| 한 화면에서만 쓰는 컴포넌트·API·CSS | `features/기능명/` |
| 여러 화면이 쓰는 컴포넌트 | `shared/ui/` |
| 여러 화면이 쓰는 Main·서버 요청 | `shared/api/` |
| 여러 화면이 쓰는 순수 함수·상수 | `shared/lib/` |
| 여러 화면이 불러오는 CSS | `shared/styles/` |
| Main·Renderer가 같이 쓰는 IPC 타입 | Desktop `electron/shared/*-contract.cts` |
| 앱 사이에서 공유하는 데이터 타입 | `packages/shared-types/` |
| 정적 이미지·Manifest | 해당 앱의 `public/` |

## 이름 규칙

- 폴더: 소문자 kebab-case
- React 컴포넌트: PascalCase 파일명
- Hook: `use`로 시작하는 camelCase
- 일반 함수·상수 파일: 소문자 kebab-case
- 기능 문서: `소문자-kebab-case-PLAN.md` 형식

`common`, `misc`, `helpers`처럼 역할이 넓고 모호한 폴더는 만들지 않는다.

## CSS 위치

- 여러 화면이 쓰는 CSS만 `shared/styles/`에 둔다. 한 화면 전용 규칙은 그 기능 폴더의 CSS에 둔다(Issue #185).
  - `app-shell.css`: 사이드바 화면의 창 틀·사이드바·기록 없음 상태 (`AppSidebar`가 불러옴)
  - `settings-shell.css`: 설정 틀 토큰·애니메이션·토스트·뒤로 링크 (`SettingsShell`이 불러옴, 기록·노트도 사용)
  - `onboarding-window.css`: Pretendard 글꼴과 단계 화면 공용 창 `.onboarding-window` (온보딩·목표·집중 준비·집중·리포트)
- 화면에 필요한 CSS는 그 화면 컴포넌트가 직접 import한다. 다른 컴포넌트를 거쳐 우연히 딸려 오는 CSS에 기대지 않는다.
