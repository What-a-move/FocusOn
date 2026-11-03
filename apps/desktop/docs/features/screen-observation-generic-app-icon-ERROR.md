# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: screen-observation-003
- 오류 식별명: generic-app-icon
- 기능명: 화면 관측 기반과 분석 제외 앱 (화면 분석 1단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 낮음 (앱은 고를 수 있으나 아이콘으로 구분할 수 없음)
- 상태: 해결
- 관련 Issue·PR: #54 실제 기기 QA에서 발견

## 오류 제목

- 온보딩 `제외할 앱 고르기` 목록의 앱 아이콘이 모두 같은 기본 아이콘으로 나옴

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 개발 실행
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. 로그인 뒤 `/onboarding/sites/`에서 `앱 추가`를 누른다.
2. 설치된 앱 목록을 본다.

## 기대 결과

- Chrome·Discord처럼 앱마다 자기 아이콘이 보인다.

## 실제 결과

- 112개 앱이 모두 같은 회색 기본 앱 아이콘이다.

## 오류 메시지

```text
없음 (app.getFileIcon('/Applications/Google Chrome.app')가 성공하지만 32×32 기본 아이콘을 돌려준다)
```

## 원인 및 해결

- 추정 원인: 도우미가 넘긴 앱 경로가 틀려 아이콘을 못 찾는다.
- 실제 원인: 추정과 달랐다(경로는 맞음). 같은 경로로 비교하니 `app.getFileIcon`은 macOS 26에서 `.app`마다 기본 아이콘을 주고, `nativeImage.createThumbnailFromPath`(QuickLook)는 진짜 아이콘을 줬다.
- 해결 방법: `createThumbnailFromPath(path, 64×64)`로 바꾸고, 처음 한 번은 앱 48개 기준 약 1.7초 걸려 경로별로 기억한다(두 번째 열기부터 바로 표시).
- 변경 파일: `electron/main/analysis-ipc.cts`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (ChatGPT·Claude·Discord·Chrome·KakaoTalk·Music 아이콘 확인)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`)

## 재발 방지 및 남은 작업

- 아이콘·썸네일 API는 macOS 버전마다 결과가 달라질 수 있어, 바꿀 때 실제 앱 경로로 이미지를 저장해 눈으로 확인한다.
