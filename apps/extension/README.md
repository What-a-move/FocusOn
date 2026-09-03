# FocusOn Chrome Extension

Chrome 브라우저의 학습 흐름을 수집하는 Extension이다.

## 담당 기능

- 현재 탭과 페이지 제목 확인
- 페이지 이동 흐름 기록
- 설정된 시간 이후 페이지 재분석 요청
- 분석 제외 도메인 처리
- Desktop 앱과 브라우저 상태 연결

## 실행

```bash
pnpm --filter @focuson/extension dev
pnpm --filter @focuson/extension build
```

Build 결과물의 `dist/` 폴더를 Chrome의 `chrome://extensions`에서 개발자 모드로 불러온다. 공통 개발 규칙은 루트의 [`docs/README.md`](../../docs/README.md)를 확인한다.

