# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

> 이 문서는 2026-10-26 기록 점검(Issue #65)에서 뒤늦게 작성했다. 당시 오류 메시지 전문은 남아 있지 않아, 확인 가능한 내용과 고친 코드를 기준으로 적는다.

## 오류 기본 정보

- 오류 번호: focus-session-003
- 오류 식별명: render-impure-time
- 기능명: 집중 화면 (D10·D11·D15·D16)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-25
- 작성자: jae1
- 심각도: 보통 (lint 오류로 빌드 검사 실패)
- 상태: 해결
- 관련 Issue·PR: #48 / PR #50

## 오류 제목

- 집중 화면이 렌더 중에 현재 시각과 ref를 읽어 lint가 실패함

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44
- Node.js·pnpm: Next.js 16.3.4, pnpm 11
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `FocusSession`에서 남은 시간을 그릴 때 렌더 함수 안에서 `performance.now()`와 ref 값을 읽는다.
2. `pnpm lint`를 실행한다.

## 기대 결과

- lint가 통과하고, 같은 입력이면 같은 화면을 그린다.

## 실제 결과

- `react-hooks/purity`·refs 규칙 오류 2건으로 lint가 실패했다.

## 오류 메시지

```text
Cannot access refs during render
✖ 4 problems (2 errors, 2 warnings)   # 경고 2건은 기존 <img> 경고
```

## 원인 및 해결

- 추정 원인: React Compiler 계열 규칙이 렌더 중 부수 효과를 막는다.
- 실제 원인: 렌더 중 `performance.now()`(호출할 때마다 값이 바뀜)와 ref 값을 읽어 렌더가 순수하지 않았다. React 19 lint 규칙(`react-hooks/purity`, refs)이 이를 오류로 본다.
- 해결 방법: 받은 시각(`view.receivedAt`)과 현재 시각(`now`)을 state로 옮기고, 1초 타이머와 세션 변경 이벤트에서만 갱신한다. 렌더는 state만 읽는다.
- 변경 파일: `features/session/FocusSession.tsx`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (`pnpm lint` 오류 0)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`)

## 재발 방지 및 남은 작업

- 시간처럼 매번 바뀌는 값은 렌더 안에서 읽지 않고 이벤트·타이머에서 state로 넣는다.
