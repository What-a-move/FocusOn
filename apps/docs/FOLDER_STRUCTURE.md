# Frontend 폴더 구조

## 권장 구조

기능이 커지면 앱 내부를 기능 중심으로 구성한다.

```text
apps/{desktop|extension}/
├── app/
├── components/
├── features/
│   └── study-session/
│       ├── components/
│       ├── hooks/
│       ├── services/
│       ├── types/
│       ├── constants/
│       └── utils/
├── lib/
├── docs/
└── public/
```

현재 존재하지 않는 폴더는 실제 기능이 필요할 때 만든다. 빈 구조를 미리 대량 생성하지 않는다.

## 위치 선택 기준

| 종류 | 위치 |
| --- | --- |
| 라우트와 화면 진입점 | `app/` |
| 앱 전체에서 사용하는 순수 UI | `components/` |
| 특정 기능에만 필요한 코드 | `features/기능명/` |
| 앱 공통 설정·클라이언트 | `lib/` |
| 앱 사이에서 공유하는 데이터 타입 | `packages/shared-types/` |
| 정적 이미지·Manifest·Content Script | 해당 앱의 `public/` |

## 이름 규칙

- 폴더: 소문자 kebab-case
- React 컴포넌트: PascalCase 파일명
- Hook: `use`로 시작하는 camelCase
- 일반 함수·상수 파일: 소문자 kebab-case
- 기능 문서: `소문자-kebab-case-PLAN.md` 형식

`common`, `misc`, `helpers`처럼 역할이 넓고 모호한 폴더는 만들지 않는다.
