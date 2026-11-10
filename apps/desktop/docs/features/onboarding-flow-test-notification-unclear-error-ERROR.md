# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: `onboarding-flow-test-notification-unclear-error`
- 기능명: 온보딩 권한 확인(알림)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-07
- 작성자: jae1
- 심각도: 보통 (원인을 알 수 없는 안내로 온보딩이 막힘)
- 상태: 해결
- 관련 Issue·PR: `feat/desktop-onboarding-polish`

## 오류 제목

- 시험 알림 `허용하기`를 누르면 원인을 알 수 없는 일반 오류 문구가 길게 나옴

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0 (개발 실행, 알림 이름은 Electron)
- Node.js·pnpm: pnpm 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. 온보딩 권한 확인에서 알림 `허용하기`를 누른다.

## 기대 결과

- 알림이 오거나, 꺼져 있으면 "알림이 꺼져 있어요"와 `설정 열기`가 보인다.

## 실제 결과

- "시험 알림을 보내지 못했어요. FocusOn이 지금 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."가 보이고 무엇을 해야 할지 알 수 없다.

## 오류 메시지

```text
같은 Electron으로 알림만 따로 띄운 결과: failed 작업을 완료할 수 없습니다.(UNErrorDomain 오류 1.)
```

## 원인 및 해결

- 추정 원인: 이 Mac에서 개발 실행(Electron)의 알림이 꺼져 있다. 그리고 `notification:show` Main 처리에 예외 처리가 없어 실패가 IPC 예외로 끝나고, 화면은 이를 `SERVICE_UNAVAILABLE`(일반 오류 문구)로 보여 줬다. 로그도 남지 않아 어느 쪽인지 알 수 없었다.
- 실제 원인: 일부 확인. 같은 Electron에서 알림이 `UNErrorDomain 1`(알림 허용 안 됨)로 거절되는 것은 재현했다. 사용자 화면이 왜 거절(`shown: false`) 대신 일반 오류로 갔는지는 로그가 없어 확인하지 못했다. 2026-11-07 시스템 설정에서 Electron 알림을 켜고 수정한 앱으로 다시 시도해 시험 알림이 정상으로 왔다(사용자 확인, 실패 로그 없음).
- 해결 방법:
  - Main은 거절이든 예외든 `shown: false`로 답하고, `notify.fire` WARN 로그에 `LOCAL_NOTIFICATION_BLOCKED`·`LOCAL_NOTIFICATION_FAILED`로 구분해 남긴다.
  - 화면은 "알림이 꺼져 있어요. ‘설정 열기’로 켜 주세요"와 `설정 열기`를 보여 준다. 앱과 연결이 끊긴 경우에만 "앱을 다시 연 뒤 시도해 주세요"를 짧게 보여 준다.
- 변경 파일: `electron/main/permission-ipc.cts`, `features/onboarding/PermissionsStep.tsx`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다.
- [ ] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (Electron 단위 테스트 161개 통과)

## 재발 방지 및 남은 작업

- 개발 실행은 시스템 설정 › 알림에 `Electron`으로, 설치 앱은 `FocusOn`으로 따로 보인다. 개발 중 알림 시험은 `Electron` 알림을 켜야 한다.
