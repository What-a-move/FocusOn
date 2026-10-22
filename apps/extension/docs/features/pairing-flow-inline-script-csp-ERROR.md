# Extension 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #36-E01
- 오류 식별명: `POPUP_INLINE_SCRIPT_BLOCKED_BY_CSP`
- 기능명: Desktop 온보딩 Extension 연결 (extension-pairing)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-22
- 작성자: Claude
- 심각도: 치명적 (Popup의 모든 동작이 멈춤)
- 상태: 해결
- 관련 Issue·PR: [#36](https://github.com/What-a-move/FocusOn/issues/36)

## 오류 제목

- Next.js 정적 Export Popup이 Chrome에서 React를 시작하지 못해 코드 입력·버튼이 동작하지 않음

## 실행 환경

- macOS 버전: 26.6.1 (Apple M4)
- Chrome 버전: Google Chrome for Testing 153.0.8010.12 (`--load-extension`으로 불러옴)
- Extension 버전: 0.1.0 (Manifest V3)
- Desktop 연결 상태: 미사용
- 권한 상태: Manifest 기본 권한

## 재현 방법

1. `apps/extension`에서 `next build --webpack`만 실행해 `out/`을 만든다 (정리 스크립트 없이).
2. Chrome에 `out/`을 압축해제된 확장 프로그램으로 불러온다.
3. Popup(`chrome-extension://<id>/index.html`)을 열고 코드 칸에 `abc123`을 입력한다.

## 기대 결과

- 입력이 `ABC-123`으로 정리되고 `연결하기` 버튼이 활성화된다.

## 실제 결과

- 입력값이 `abc123` 그대로 남고 버튼이 계속 비활성이다. 서버 정적 HTML만 보이고 React가 붙지 않는다.

## 오류 메시지

```text
Executing inline script violates the following Content Security Policy directive 'script-src 'self''.
Uncaught (in promise) Error: Minified React error #412
```

## 원인 및 해결

- 추정 원인: ① Chrome이 `_`로 시작하는 파일·폴더(`_next`)를 예약어로 보고 로딩을 거부한다. ② Manifest V3 CSP가 인라인 스크립트를 막는다.
- 실제 원인: ②만 해당한다. Next.js 정적 Export는 RSC 데이터를 `<script>self.__next_f.push(...)</script>` 인라인 스크립트로 넣는데, Manifest V3 Extension 페이지의 기본 CSP(`script-src 'self'`)가 이를 실행하지 않는다. ①은 Chrome 153에서 재현되지 않았다(`_next`가 있어도 오류 없이 로딩됨).
- 해결 방법: 빌드 뒤 `scripts/prepare-extension-build.mjs`가 각 HTML의 인라인 스크립트를 `out/_next/inline/<hash>.js` 외부 파일로 옮기고 같은 위치에 `<script src>`로 바꾼다. 순서를 유지하므로 Next.js 부트스트랩이 그대로 동작한다. 인라인 스크립트가 남으면 빌드를 실패시킨다.
- 변경 파일: `apps/extension/scripts/prepare-extension-build.mjs`, `apps/extension/package.json`(`build`에 정리 단계 추가)

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (정리 뒤 `abc123` → `ABC-123`, 버튼 활성, CSP 오류 없음)
- [ ] 새 탭·기존 탭에서 확인했다. (Popup 화면만 해당)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 제외 도메인을 확인했다. (해당 없음)
- [x] Desktop 연결 실패를 확인했다. (테스트용 가짜 서버로 만료·429·틀린 코드 응답 확인)
- [x] 관련 기능의 회귀가 없다. (`/`, `/connect`, `/dashboard` 6개 HTML 모두 변환)

## 재발 방지 및 남은 작업

- Extension Popup은 반드시 `pnpm build`(정리 단계 포함) 결과를 불러온다. `next build`만 실행한 `out/`을 쓰지 않는다.
- Next.js 버전을 올리면 인라인 스크립트 외의 CSP 위반(예: `eval`)이 생기지 않는지 Popup을 Chrome에서 다시 연다.
- 실제 Chrome 정식 버전의 `chrome://extensions` → 압축해제된 확장 프로그램 로드에서도 한 번 확인한다.
