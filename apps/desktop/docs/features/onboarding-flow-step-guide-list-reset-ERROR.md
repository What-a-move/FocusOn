# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #36-D01
- 오류 식별명: `ONBOARDING_GUIDE_NUMBERS_HIDDEN`
- 기능명: Desktop·Extension 온보딩 (onboarding-flow)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-21
- 작성자: Claude
- 심각도: 낮음
- 상태: 해결
- 관련 Issue·PR: [#36](https://github.com/What-a-move/FocusOn/issues/36)

## 오류 제목

- Extension 연결 단계의 연결 방법 목록(1., 2.)에서 번호가 사라짐

## 실행 환경

- macOS 버전: 26.6.1 (Apple M4)
- Mac 모델·칩: Apple M4
- Electron 버전: 해당 없음 (Next dev 서버를 브라우저로 확인)
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `pnpm dev:desktop` 실행 뒤 `/onboarding/extension/`을 연다.
2. 코드 칸 아래 연결 방법 안내 `<ol class="steps-guide">`를 본다.

## 기대 결과

- `FocusOn-design/onboarding.html`처럼 `1.`, `2.` 번호가 붙은 두 줄로 보인다.

## 실제 결과

- 번호 없이 두 줄만 보인다.

## 오류 메시지

```text
없음 (시각 차이)
```

## 원인 및 해결

- 추정 원인: 원본 HTML과 다른 전역 스타일이 목록 표시를 덮어쓴다.
- 실제 원인: Desktop `app/globals.css`의 `@import 'tailwindcss'`가 Preflight로 `ol, ul { list-style: none }`을 적용한다. 원본 HTML은 Tailwind 없이 브라우저 기본 목록 스타일을 쓴다.
- 해결 방법: `.onboarding-window .steps-guide`에 `list-style: decimal`을 명시했다.
- 변경 파일: `apps/desktop/features/onboarding/onboarding.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (계산된 `list-style-type: decimal` 확인)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (해당 없음)
- [x] 관련 기능의 회귀가 없다.

## 재발 방지 및 남은 작업

- 디자인 원본 HTML을 옮길 때 목록·제목·버튼처럼 Tailwind Preflight가 초기화하는 기본 스타일은 CSS에 명시한다.
