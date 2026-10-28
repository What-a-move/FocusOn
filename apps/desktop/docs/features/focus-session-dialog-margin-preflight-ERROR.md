# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: focus-session-002
- 오류 식별명: dialog-margin-preflight
- 기능명: 집중 화면 (D10·D11·D15·D16)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-25
- 작성자: jae1
- 심각도: 보통 (화면이 시안과 다르게 보임)
- 상태: 해결
- 관련 Issue·PR: #48

## 오류 제목

- Tailwind 기본 초기화가 `<dialog>` margin을 0으로 만들어 확인 창이 왼쪽 위에 붙음

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44 (이 오류는 `pnpm dev` 화면에서 확인)
- Node.js·pnpm: pnpm 11.19.0, Next.js 16.3.4, Tailwind CSS v4
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. 집중 화면에서 목표 시간 도달 상태로 만든다(시간 종료 확인 창).
2. `showModal()`로 창이 열린다.
3. 창 위치를 본다.

## 기대 결과

- 창이 화면 가운데에 뜬다.

## 실제 결과

- 창이 왼쪽 위 모서리에 붙어 열렸다.

## 오류 메시지

```text
getComputedStyle(dialog).margin → '0px'
```

## 원인 및 해결

- 추정 원인: 없음(브라우저 계산 스타일로 바로 확인).
- 실제 원인: 브라우저 기본 스타일은 모달 `<dialog>`를 `margin: auto`로 가운데 둔다. Tailwind Preflight가 모든 요소의 margin을 0으로 초기화해 이 기본값이 사라졌다.
- 해결 방법: `.focus-window dialog`에 `margin: auto`를 다시 주고 이유를 주석으로 남겼다.
- 변경 파일: `features/session/focus-session.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`, `pnpm build`)

## 재발 방지 및 남은 작업

- `<dialog>`를 새로 쓰는 화면은 `margin: auto`를 함께 둔다. 비슷한 사례: 온보딩 목록 번호(`onboarding-flow-step-guide-list-reset-ERROR.md`).
