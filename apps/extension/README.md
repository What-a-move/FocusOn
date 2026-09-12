# FocusOn Chrome Extension

Chrome 브라우저의 학습 흐름을 수집하는 Extension이다. Popup 화면은 Next.js 정적 Export로 생성한다.

## 담당 기능

- 활성 학습 세션 중 현재 활성 탭과 페이지 제목 자동 추적
- 페이지 이동 흐름 기록
- 설정된 시간 이후 페이지 재분석 요청
- 분석 제외 도메인 처리
- Server를 기준으로 Desktop 앱과 학습 세션 상태 연결

## 실행

```bash
pnpm dev:extension
pnpm build:extension
pnpm turbo run lint --filter=@focuson/extension
```

화면 스타일은 Tailwind CSS v4로 작성하며 `app/globals.css`와 `postcss.config.mjs`가 진입점이다. Build 결과물의 `out/` 폴더를 Chrome의 `chrome://extensions`에서 개발자 모드로 불러온다. Frontend 공통 규칙은 [`apps/docs/README.md`](../docs/README.md)를, Extension 전용 규칙은 [`docs/README.md`](docs/README.md)와 [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md)를 확인한다.
