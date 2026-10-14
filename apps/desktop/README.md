# FocusOn Desktop

FocusOn의 macOS 데스크톱 앱 Renderer다. Next.js가 정적 화면을 생성하고, Electron Main·Preload가 macOS 기능을 담당한다.

## 담당 기능

- 학습 세션 시작·일시정지·종료
- 타이머와 휴식 알림
- macOS 활성 앱 확인
- 화면 분석과 분석 제외 앱 처리
- 카메라 권한 및 MediaPipe 분석 연동
- Server를 기준으로 Chrome Extension과 세션 상태 연결

## 실행

```bash
pnpm dev:desktop
pnpm build:desktop
pnpm turbo run lint --filter=@focuson/desktop
```

화면 스타일은 Tailwind CSS v4로 작성하며 `app/globals.css`와 `postcss.config.mjs`가 진입점이다. `next build` 결과는 `out/`에 생성된다. Production은 `out/`을 보안 설정된 `app://` Protocol로 열고, Electron Main·Preload 연결은 별도 구현 작업으로 진행한다. Frontend 공통 규칙은 [`apps/docs/README.md`](../docs/README.md)를, Desktop 전용 규칙은 [`docs/README.md`](docs/README.md)와 [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md)를 확인한다.

Issue #29 학습 정리 화면은 개발 서버의 `http://127.0.0.1:3000/learning-summary-demo/`에서 합성 데이터로만 시연한다. 정상·근거 부족·실패를 선택하고 가상 세션을 종료하면 상태와 복사를 확인할 수 있다. 운영 빌드에서는 Route가 비활성화되고 Fixture가 포함되지 않는다. 실제 세션·관련성 분석·AI·Server API 기능으로 오해해서는 안 된다.
