# FocusOn Desktop

FocusOn의 macOS 데스크톱 앱 Renderer다. Next.js가 정적 화면을 생성하고, Electron Main·Preload가 macOS 기능을 담당한다.

## 담당 기능

- 학습 세션 시작·일시정지·종료
- 타이머와 휴식 알림
- macOS 활성 앱 확인
- 화면 분석과 분석 제외 앱 처리
- 카메라 권한 및 MediaPipe 분석 연동
- Chrome Extension과 상태 연결

## 실행

```bash
pnpm --filter @focuson/desktop dev
pnpm --filter @focuson/desktop build
```

`next build` 결과는 `out/`에 생성된다. Electron Main·Preload 연결은 별도 작업으로 진행한다. 공통 개발 규칙은 루트의 [`docs/README.md`](../../docs/README.md)를 확인한다.
