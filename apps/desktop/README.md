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
pnpm dev:desktop                                  # 터미널 1: Next.js 화면
pnpm --filter @focuson/desktop dev:electron       # 터미널 2: Electron 창
pnpm build:desktop                                # Main·Preload·Renderer Build
pnpm --filter @focuson/desktop preview:electron   # 정적 Build 앱 미리보기
pnpm --filter @focuson/desktop test:electron
pnpm --filter @focuson/desktop lint
```

화면 스타일은 Tailwind CSS v4와 `app/globals.css`를 사용한다. 로그인·온보딩 시작·홈의 배치와 색은 저장소 옆 `FocusOn-Design/login.html`, `onboarding.html`, `main.html`을 기준으로 맞췄다. 화면 에셋은 `public/design/`에 복사했다. `next build` 결과는 `out/`에 생성된다. Production은 `out/`을 보안 설정된 `app://` Protocol로 연다. Frontend 공통 규칙은 [`apps/docs/README.md`](../docs/README.md)를, Desktop 전용 규칙은 [`docs/README.md`](docs/README.md)와 [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md)를 확인한다.

## Google OAuth 개발 설정

Google Cloud에서 **데스크톱 앱** 유형의 OAuth Client ID를 만든 뒤 Electron을 실행하는 셸에 `FOCUSON_GOOGLE_CLIENT_ID`로 전달한다. Backend origin도 `FOCUSON_API_BASE_URL`로 설정한다. 값 형식은 [`.env.example`](.env.example)을 참고한다. `.env.example`은 자동으로 로드되지 않는다.

```bash
FOCUSON_GOOGLE_CLIENT_ID=발급받은-데스크톱-클라이언트-ID FOCUSON_API_BASE_URL=http://127.0.0.1:8080 pnpm --filter @focuson/desktop dev:electron
```

현재 Main은 기본 브라우저와 `127.0.0.1`의 임시 callback listener로 Google code를 받은 뒤 Backend `google/exchange`에 전달한다. 성공 응답의 Access Token은 Main 메모리, Refresh Token은 macOS Keychain에 저장한다. 앱 재시작 시 refresh·auth/me로 상태를 복구하고 로그아웃은 서버 폐기 후 Keychain을 비운다. Renderer는 로그인 버튼과 인증 상태별 화면을 제공한다. 서버가 `onboardingStatus: INCOMPLETE`를 주면 온보딩 첫 화면, `COMPLETED`를 주면 홈을 표시한다. 닉네임 중복 확인·저장과 온보딩 다음 단계, 홈의 목표 설정·기록 API는 후속 작업이다. Backend 인증 API가 준비되지 않았으면 실제 로그인 성공으로 처리하지 않는다. Client ID나 Backend origin 설정이 없으면 브라우저를 열지 않는다.
