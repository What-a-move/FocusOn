# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.
## 오류 기본 정보

- 오류 번호: session-popup-002
- 오류 식별명: timer-jumps-back
- 기능명: 팝업 세션 화면 (E02·E03·E06)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-11-03
- 작성자: jae1
- 심각도: 보통 (집중 중 팝업 남은 시간이 Desktop과 다르고 10초마다 뒤로 튐)
- 상태: 해결
- 관련 Issue·PR: #52 실제 연동 QA

## 오류 제목

- 집중 중 팝업 남은 시간이 조회할 때마다 처음 값으로 돌아감

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Chrome 버전: Chrome for Testing 153.0.8010.12 (Playwright Chromium, `--load-extension`)
- Extension 버전: 0.1.0 (빌드 결과물 `out/`)
- Desktop 연결 상태: 실제 로컬 서버(dev 최신)로 연결
- 권한 상태: 서버 주소 host 권한만 (사이트 접근 허용 전후 모두 재현)

## 재현 방법

1. Desktop에서 30분 집중을 시작한다.
2. 20초 넘게 지난 뒤 팝업을 열고 10초 이상 본다.

## 기대 결과

- Desktop 남은 시간과 거의 같고, 앞으로만 줄어든다.

## 실제 결과

- Desktop 29:37일 때 팝업은 30:00에서 줄어들다가 10초마다 30:00으로 돌아감.

## 오류 메시지

```text
없음 (GET /sessions/active 200, accumulatedActiveMs: 0)
```

## 원인 및 해결

- 추정 원인: 팝업 1초 타이머가 멈춘다.
- 실제 원인: 추정과 달랐다(1초 타이머는 정상). 팝업은 "서버 누적 시간 + 조회 뒤 흐른 시간"으로 그린다. 서버 누적 시간은 Desktop이 heartbeat·멈춤을 보낼 때만 바뀌는데, 10초 조회마다 받은 시각을 새 기준으로 삼아 화면 시간이 서버 값(오래된 값)으로 되돌아갔다. 지금은 서버에 heartbeat API가 없어 누적이 0에 머물러 크게 보였고, heartbeat가 생겨도 최대 그 간격만큼 뒤로 튄다.
- 해결 방법: `keepForward` — 같은 세션이 계속 `RUNNING`이면 새로 받은 누적 시간을 이미 그린 시간보다 줄이지 않는다. 멈춤·다른 세션은 서버 값을 그대로 쓴다.
- 변경 파일: `features/session/session-model.ts`, `features/session/SessionPopup.tsx`, `tests/session-client.test.ts`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (단위 테스트: 서버 값 0으로 23초 뒤 다시 받아도 23초 유지)
- [x] 멈춤 상태는 서버 값과 일치 (팝업·Desktop 모두 29:05)
- [x] 관련 기능의 회귀가 없다. (`pnpm test` 30개)

## 재발 방지 및 남은 작업

- 팝업을 새로 열면 서버가 마지막으로 받은 heartbeat 기준이라 최대 heartbeat 간격(10초)만큼 Desktop보다 많이 남아 보일 수 있다. 문제가 되면 활성 세션 응답에 마지막 heartbeat·재개 시각을 받아 보정한다.
