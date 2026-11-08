# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: apps-fsd-lite-settings-font-css-dropped
- 기능명: apps 폴더 구조 가벼운 FSD 정리
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-09
- 작성자: jae1
- 심각도: 보통
- 상태: 해결
- 관련 Issue·PR: #183

## 오류 제목

- 공용 상수만 `shared/lib`로 옮겼는데 설정 화면에서 Pretendard 웹 글꼴 CSS가 빠짐

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple Silicon
- Electron 버전: ^44.1.0
- Node.js·pnpm: v24.14.1 · 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `Settings.tsx`가 `features/session/SessionSetup`에서, `Exclusions.tsx`가 `features/onboarding/ExcludedSitesStep`에서 가져오던 상수를 `shared/lib/`에서 가져오게 바꾼다.
2. `next build --webpack` 후 `out/settings/index.html`·`out/settings/exclusions/index.html`의 CSS 목록을 dev 빌드와 비교한다.

## 기대 결과

- 모든 화면의 CSS 목록과 순서가 dev와 같다.

## 실제 결과

- 두 설정 화면에서 `onboarding.css`(Pretendard `@font-face` 포함)가 빠졌고, 설정 화면에서는 `session-setup.css`도 빠졌다. 타입 검사·lint·테스트·빌드는 모두 통과해서 결과물을 비교해야만 보였다.

## 오류 메시지

```text
없음 (빌드 결과 CSS 목록 비교로 발견)
```

## 원인 및 해결

- 추정 원인: 파일을 옮기면서 CSS import가 바뀌었다.
- 실제 원인: Next는 컴포넌트 파일이 import한 CSS를 그 화면에 붙인다. 설정 화면은 상수 몇 개 때문에 `SessionSetup`·`ExcludedSitesStep` 파일 전체를 불러왔고, 그 파일들이 import한 CSS가 우연히 같이 실려 Pretendard 글꼴이 로드되고 있었다. 상수만 따로 가져오게 되자 이 우연한 연결이 끊겼다.
- 해결 방법: `Settings.tsx`·`Exclusions.tsx`가 `shared/styles/onboarding.css`를 직접 import하게 했다. `session-setup.css`의 규칙은 모두 `.setup-window`·`.onboarding-window` 아래에만 적용되는데 설정 화면에는 이 클래스가 없어서 다시 넣지 않았다.
- 변경 파일: `features/settings/Settings.tsx`, `features/settings/Exclusions.tsx`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (설정 화면 CSS가 dev와 같고, 남은 차이는 적용 대상이 없는 `session-setup.css` 1개)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다. (`pnpm build` 통과)
- [x] 관련 기능의 회귀가 없다. (전체 CSS 파일 내용은 dev와 바이트 단위로 같다)

## 재발 방지 및 남은 작업

- 컴포넌트 파일에서 상수를 가져오면 그 파일의 CSS까지 딸려 온다. 공용 상수는 `shared/lib`에 두고, 화면에 필요한 CSS는 그 화면이 직접 import한다.
- Pretendard `@font-face`를 `app/globals.css`로 옮겨 모든 화면에 한 번만 싣는 일은 후속(화면 결과물이 바뀌므로 별도 확인 필요).
