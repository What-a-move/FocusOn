# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: 1
- 오류 식별명: countup-set-state-in-effect
- 기능명: session-result
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27
- 작성자: Claude (jae1 작업)
- 심각도: 낮음
- 상태: 해결
- 관련 Issue·PR: #73

## 오류 제목

- 숫자가 올라가는 애니메이션 hook이 lint `react-hooks/set-state-in-effect`에 걸림

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Mac 모델·칩: Apple Silicon
- Electron 버전: 44.1.0 (해당 없음, Renderer 개발 중)
- Node.js·pnpm: Node 24.14.1
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `features/report/SessionResult.tsx`의 `useCountUp`에서 동작 줄이기 설정이면 effect 안에서 바로 `setValue(target)`을 부른다.
2. `npx eslint features/report`를 실행한다.

## 기대 결과

- lint 오류 없음

## 실제 결과

- error 1건으로 lint가 실패한다.

## 오류 메시지

```text
react-hooks/set-state-in-effect
✖ 1 problem (1 error, 0 warnings)
```

## 원인 및 해결

- 추정 원인: requestAnimationFrame 안의 setState를 lint가 잘못 잡았다.
- 실제 원인: 동작 줄이기 분기에서 effect 본문이 setState를 동기로 바로 불러 렌더가 한 번 더 일어난다. rAF 콜백 안의 setState는 문제가 아니었다.
- 해결 방법: 동작 줄이기면 애니메이션 길이를 0으로 두고 같은 rAF 경로에서 첫 프레임에 목표값을 넣는다.
- 변경 파일: `features/report/SessionResult.tsx`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (실제 Electron 미확인)
- [x] 관련 기능의 회귀가 없다. (`reducedMotion: reduce` 캡처에서 숫자가 바로 표시됨)

## 재발 방지 및 남은 작업

- effect에서 바로 상태를 바꾸지 말고 콜백·이벤트 안에서 바꾼다.
