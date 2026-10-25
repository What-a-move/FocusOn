# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: screen-observation-001
- 오류 식별명: dialog-margin-preflight
- 기능명: 화면 관측 기반과 분석 제외 앱 (화면 분석 1단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-25
- 작성자: jae1
- 심각도: 보통 (화면이 시안과 다르게 보임)
- 상태: 해결
- 관련 Issue·PR: #54

## 오류 제목

- 온보딩 `제외할 앱 고르기` 창이 가운데가 아니라 왼쪽 위에 붙어 열림 (`focus-session-dialog-margin-preflight-ERROR.md` 재발)

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44 (이 오류는 `pnpm dev` 화면에서 확인)
- Node.js·pnpm: Next.js 16.3.4, Tailwind CSS v4
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `/onboarding/sites/`에서 `앱 추가`를 누른다.
2. `showModal()`로 `제외할 앱 고르기` 창이 열린다.
3. 창 위치를 본다.

## 기대 결과

- 창이 화면 가운데에 뜬다.

## 실제 결과

- 창이 왼쪽 위 모서리에 붙어 열렸다.

## 오류 메시지

```text
getComputedStyle(document.querySelector('.app-dialog')).margin → '0px'
```

## 원인 및 해결

- 추정 원인: 집중 화면 때와 같은 Tailwind Preflight의 margin 초기화.
- 실제 원인: 추정과 같다. 지난번에는 `.focus-window dialog`에만 `margin: auto`를 되돌려, 온보딩의 `.consent-dialog`(동의 상세)와 새 `.app-dialog`는 여전히 margin 0이었다. 동의 상세 창에도 같은 문제가 숨어 있었다.
- 해결 방법: 화면별로 고치지 않고 `app/globals.css`에 `dialog { margin: auto; }`를 두어 모든 `<dialog>`를 한 번에 되돌렸다.
- 변경 파일: `app/globals.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (`margin` → `183px 430px`, 가운데 표시)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`, `pnpm lint`, `pnpm build`)

## 재발 방지 및 남은 작업

- 전역 규칙으로 옮겼으므로 새 `<dialog>`에 따로 margin을 줄 필요가 없다. 기존 `.focus-window dialog`의 `margin: auto`는 같은 값이라 그대로 둔다.
