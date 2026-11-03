# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: onboarding-flow-002
- 오류 식별명: app-dialog-style-regression
- 기능명: 온보딩 (분석 제외 단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 낮음 (동작은 정상, 디자인이 시안과 다름)
- 상태: 해결
- 관련 Issue·PR: 원인 #146(동의 원문 디자인), 발견 #54 실제 기기 QA

## 오류 제목

- `제외할 앱 고르기` 대화상자가 720px로 넓어지고 제목이 얇은 글씨로 나옴

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 개발 실행
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `/onboarding/sites/`에서 `앱 추가`를 누른다.

## 기대 결과

- 420px 대화상자, 굵은 21px 제목(`exclusions.html` 앱 대화상자).

## 실제 결과

- 동의 원문 창과 같은 720px 폭·여백, 제목이 기본 굵기.

## 오류 메시지

```text
없음
```

## 원인 및 해결

- 추정 원인: 설정 화면 dialog 스타일이 온보딩까지 덮었다.
- 실제 원인: 추정과 달랐다. #146에서 동의 원문 창을 넓히려고 `dialog.consent-dialog[open]`(우선순위 0,2,1)을 추가했는데, 앱 고르기 대화상자도 `consent-dialog` 클래스를 같이 써서 `.onboarding-window .app-dialog`(0,2,0) 폭을 이겼다. 같은 PR에서 `.consent-dialog h2` 굵기 규칙을 지우면서 이 대화상자 제목 굵기도 사라졌다.
- 해결 방법: 넓은 창 규칙을 `dialog.consent-dialog:not(.app-dialog)[open]`으로 좁히고, `.app-dialog h2` 굵기 규칙을 되살렸다. 목록이 늦게 와서 첫 포커스가 `닫기`에 가던 것도 첫 고를 수 있는 앱으로 옮겼다.
- 변경 파일: `features/onboarding/consent-doc.css`, `features/onboarding/onboarding.css`, `features/onboarding/ExcludedAppsField.tsx`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (폭 420px, 제목 700)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (동의 원문 창은 계속 넓은 창)

## 재발 방지 및 남은 작업

- 공용 클래스(`consent-dialog`)에 스타일을 더할 때는 그 클래스를 쓰는 dialog를 모두 찾아(`grep consent-dialog`) 캡처로 확인한다.
