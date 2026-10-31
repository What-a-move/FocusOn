# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: settings-02
- 오류 식별명: dev-launch
- 기능명: 설정 화면 실제 서버 QA (개발 실행)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-30
- 작성자: jae1
- 심각도: 보통 (개발 실행에서만, 로그인·권한 QA가 막힘)
- 상태: 해결 (실행 방법 정리)
- 관련 Issue·PR: #80

## 오류 제목

- 개발 실행에서 ① 로그인이 바로 실패하고 ② 화면 기록을 허용해도 꺼짐으로 보인다.

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 · 11.19.0
- 화면 기록 권한: 허용 (시스템 설정 목록 이름 `Electron`)
- 카메라 권한: 해당 없음

## 재현 방법

1. `apps/desktop/.env`에 값을 넣고 `pnpm dev:electron`만 실행한다. → Google 로그인을 누르면 바로 실패한다.
2. 셸에 값을 넘겨 터미널에서 실행한 뒤 시스템 설정 › 화면 및 시스템 오디오 녹음에서 `Electron`을 켠다. → 앱을 다시 켜도 화면 기록이 꺼짐으로 보인다.

## 기대 결과

- 로그인이 진행되고, 허용한 화면 기록이 허용됨으로 보인다.

## 실제 결과

- ① `요청을 마치지 못했어요. / 로그인 서비스를 사용할 수 없어요. 잠시 후 다시 시도해 주세요.` 로그 파일에 요청이 하나도 없다.
- ② 권한 화면이 계속 꺼짐이다.

## 오류 메시지

```text
① SERVICE_UNAVAILABLE (서버 요청 없음)
② systemPreferences.getMediaAccessStatus('screen') → 'denied' (터미널에서 실행한 앱)
   같은 Electron.app을 open -a로 새로 실행 → 'granted'
```

## 원인 및 해결

- 추정 원인: ① 서버나 Google OAuth 설정 오류 ② 폴더 이동으로 예전 `Electron` 권한 항목이 깨짐.
- 실제 원인:
  - ① Electron은 `.env`를 읽지 않는다(README에 적힌 대로 셸에서 넘겨야 함). `FOCUSON_GOOGLE_CLIENT_ID`가 없으면 결정대로 브라우저를 열지 않고 `SERVICE_UNAVAILABLE`을 돌려준다.
  - ② macOS는 화면 기록 권한을 **실행한 쪽(responsible process)** 기준으로 판단한다. 터미널의 자식으로 실행하면 Electron이 아니라 그 터미널 앱의 권한을 본다. 또 권한은 앱이 시작될 때만 반영돼서, 켜 둔 채로 허용하면 재실행 전까지 꺼짐이다.
- 해결 방법: 권한까지 확인하는 QA는 `open -a`로 Electron을 독립 앱으로 실행하고 `.env` 값은 `--env`로 넘긴다(README "권한 QA 실행"). 권한을 바꾼 뒤에는 앱을 다시 실행한다.
- 변경 파일: `README.md`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (허용만)
- [x] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (코드 변경 없음)

## 재발 방지 및 남은 작업

- 개발 실행에서 권한 화면 문구는 `FocusOn`인데 macOS 목록 이름은 `Electron`이라 헷갈린다. 오류 문구 개선 작업에서 개발 실행 안내를 함께 정리한다.
- 서명 없는 개발 앱은 알림도 보낼 수 없다(`settings-dev-notification-unsigned-ERROR.md`).
