# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.

## 오류 기본 정보

- 오류 번호: session-popup-001
- 오류 식별명: dev-server-blank
- 기능명: 팝업 세션 화면 (E02·E03·E06)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-25
- 작성자: jae1
- 심각도: 낮음 (개발 확인 단계에서만 발생, 빌드 결과물은 정상)
- 상태: 보류 (빌드 결과물로 확인하는 방법으로 우회)
- 관련 Issue·PR: #52

## 오류 제목

- `pnpm dev`(포트 3001)로 연 팝업 화면이 아무것도 그리지 않음

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Chrome 버전: Playwright Chromium (가짜 `chrome.storage` 주입)
- Extension 버전: 0.1.0 (개발 서버, Next.js 16.3.4 Turbopack)
- Desktop 연결 상태: 미사용 (서버 응답은 가로채서 흉내)
- 권한 상태: 해당 없음

## 재현 방법

1. `NEXT_PUBLIC_FOCUSON_API_BASE_URL=http://127.0.0.1:9999 pnpm dev`로 Extension 개발 서버를 연다.
2. 브라우저에 가짜 `chrome.storage`를 넣고 `http://127.0.0.1:3001/`을 연다.
3. 15초 기다린다.

## 기대 결과

- 연결된 상태라면 `지금은 집중 중이 아니에요`(E02)가 보인다.

## 실제 결과

- `<body>`에 빈 hidden div만 있고 팝업이 그려지지 않았다. 화면 코드 오류(pageerror)는 없었다.

## 오류 메시지

```text
WebSocket connection to 'ws://127.0.0.1:3001/_next/hmr?id=...' failed: Error during WebSocket handshake: net::ERR_INVALID_HTTP_RESPONSE
```

## 원인 및 해결

- 추정 원인: 개발 서버의 실시간 갱신(HMR) WebSocket 연결이 실패하면서 Client Component hydration이 끝나지 않았다. 첫 화면은 확인 전 상태라 `null`을 돌려주므로, hydration이 안 되면 빈 화면이 된다.
- 실제 원인: 확인하지 못했다. 같은 코드를 `pnpm build` 결과물(`out/`)로 정적 서버에 열면 모든 상태가 정상으로 그려졌다.
- 해결 방법: 팝업 화면 확인은 실제 Extension과 같은 빌드 결과물(`out/`)로 한다(`python3 -m http.server`로 `out/`을 열어 확인). 개발 서버 문제는 보류한다.
- 변경 파일: 없음

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (빌드 결과물에서는 정상)
- [ ] 새 탭·기존 탭에서 확인했다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 제외 도메인을 확인했다. (해당 없음)
- [x] Desktop 연결 실패를 확인했다. (서버 연결 불가 화면 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test`, `pnpm build`)

## 재발 방지 및 남은 작업

- Extension 화면은 최종적으로 `chrome://extensions`에서 `out/`을 불러와 확인한다. 개발 서버의 HMR 실패 원인은 필요할 때 다시 조사한다.
