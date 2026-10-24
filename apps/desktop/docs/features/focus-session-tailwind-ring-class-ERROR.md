# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: focus-session-001
- 오류 식별명: tailwind-ring-class
- 기능명: 집중 화면 (D10·D11·D15·D16)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-25
- 작성자: jae1
- 심각도: 보통 (화면이 시안과 다르게 보임)
- 상태: 해결
- 관련 Issue·PR: #48

## 오류 제목

- 타이머 SVG에 붙인 클래스 `ring`이 Tailwind `ring` 유틸리티와 겹쳐 검은 사각 테두리가 생김

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44 (이 오류는 `pnpm dev` 화면에서 확인)
- Node.js·pnpm: pnpm 11.19.0, Next.js 16.3.4, Tailwind CSS v4
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. 타이머 `<svg>`에 `className="ring"`을 둔다.
2. `pnpm dev` 후 `/session/`을 연다.
3. 원형 타이머 둘레를 본다.

## 기대 결과

- 원형 링만 보인다(시안 `session.html`).

## 실제 결과

- 링을 감싸는 1px 검은 사각 테두리가 보였다.

## 오류 메시지

```text
getComputedStyle(.ring).boxShadow → "... rgb(23, 23, 23) 0px 0px 0px 1px ..."
```

## 원인 및 해결

- 추정 원인: 없음(브라우저 계산 스타일로 바로 확인).
- 실제 원인: Tailwind CSS v4의 `ring` 클래스는 `box-shadow`로 1px 테두리를 그리는 유틸리티다. 시안의 클래스 이름을 그대로 옮기면서 같은 이름이 Tailwind 규칙과 합쳐졌다.
- 해결 방법: 클래스 이름을 `timer-ring`으로 바꾸고 CSS에 이유를 주석으로 남겼다.
- 변경 파일: `features/session/FocusSession.tsx`, `features/session/focus-session.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`, `pnpm build`)

## 재발 방지 및 남은 작업

- 시안 클래스를 옮길 때 Tailwind 유틸리티 이름(`ring`, `border`, `shadow`, `hidden`, `container` 등)과 겹치는지 확인한다. 겹치면 화면 이름을 붙인 클래스로 바꾼다.
