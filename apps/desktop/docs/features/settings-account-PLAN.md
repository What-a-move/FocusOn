# Desktop 기능 기획서

## 기본 정보

- 기능명: 설정 계정·기기 연결 탭
- 기능 ID: D24·D25
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-11-02
- 우선순위: MVP - 기본
- 관련 Issue: #127
- 예정 Branch: `feat/127-desktop-settings-account`
- 관련 Notion·Figma: `FocusOn-design/settings.html`(account·devices 패널), Notion 개인정보·동의 정책, 인증·토큰·디바이스 정책, 결정 029(이 탭을 다음 Issue로 미룸)

## 기능 목적

- 사용자의 문제: 닉네임을 바꾸거나, 동의 내역을 확인·철회하거나, Chrome 확장 연결을 확인·해제·다시 연결할 곳이 없다.
- Desktop에서 제공할 가치: 설정 안에서 계정과 기기 연결을 직접 관리한다.

## 사용자 흐름

1. 설정 → `계정`: 닉네임 변경, `동의 내역 · 전문 보기`, `화면 분석 동의 철회`(확인 창) → 철회 뒤 같은 자리에 `다시 동의`, `로그아웃`(확인 창), `계정 삭제`(준비 중, 비활성).
2. 설정 → `기기 연결`: 이 Mac에 연결된 Chrome 확장 상태(연결됨·마지막 확인 / 연결 안 됨), `코드 받기`로 5분 연결 코드, 연결되면 코드가 닫히고 상태가 바뀐다. 연결돼 있으면 `해제`(확인 창).

## Desktop 상세 동작

- 닉네임: 온보딩과 같은 `PUT /users/me/profile`. 2~12자 한글·영문·숫자, 409는 입력칸 아래 `이미 사용 중인 닉네임이에요.` 성공하면 사이드바 이름도 바뀐다.
- 동의 내역: `GET /users/me/consents`. 전문 보기는 온보딩 동의 단계와 같은 문서(`features/onboarding/consent-docs.ts`).
- 화면 분석 동의: `PUT /users/me/consents/SCREEN_ANALYSIS` `{ isGranted, consentVersion: 서버 currentVersion, consentSource: 'DESKTOP_SETTINGS' }`. 철회하면 서버 readiness가 미완료(권한·동의 단계)가 되어 새 집중을 시작할 수 없다. 저장 뒤 readiness를 다시 읽어 화면 상태(onboardingStatus)에 반영한다.
- 기기: `GET /devices`에서 `type=EXTENSION`이고 `linkedDesktopDeviceId`가 이 Desktop deviceId인 항목만 보여 준다. 해제는 그 기기만 `DELETE /extension-pairings/{id}`(204). 해제 뒤 readiness를 다시 읽는다.
- 연결 코드: 온보딩과 같은 `POST /pairing-codes`. 코드를 보여 주는 동안 3초마다 `GET /devices`로 연결을 확인한다.
- 계정 삭제: 서버 API가 없어 줄은 두고 버튼을 막는다(`아직 준비 중이에요`, PM 결정 2026-11-02).
- 색: 철회·해제·삭제는 되돌리기 어려운 동작이지만 오류가 아니므로 빨간색을 쓰지 않는다(결정 031). 확인 창으로 한 번 더 묻는다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 없음

## 화면 및 사용자 경험

- 화면 위치: `features/settings/AccountPanels.tsx`, 설정 탭 순서는 시안대로 계정·집중·분석·권한·기기 연결·화면. 주소가 없으면 집중 탭.
- 시안에서 바꾼 것: `진행 중인 집중은 자동으로 멈춰요`(해제 설명)는 Desktop이 그렇게 동작하지 않아 `다시 연결할 때까지 새 집중을 시작할 수 없어요`로 썼다. 빨간 버튼(`danger`)은 기본 버튼으로.
- 로딩·오류: 동의 내역·기기 목록을 못 불러오면 `LoadError`(제목·원인·다시 시도). 저장 실패는 토스트(`failText`).

## 데이터 및 IPC/API

- 새 Channel: `settings:consents:get`, `settings:screen-consent:set`(`{ granted }`), `settings:devices:get`, `settings:extension:unlink`(`{ deviceId }` 숫자 문자열)
- 공통 요청에 `DELETE`와 본문 없는 204 성공을 추가했다.
- 저장 데이터: 없음

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 닉네임 중복(409) | 이미 사용 중인 닉네임이에요. | 입력칸 유지 |
| 철회·해제 실패 | 철회하지(해제하지) 못했어요. + 원인 | 화면 값 그대로 |
| 다른 Desktop의 Extension id | — | 요청하지 않고 `DEVICE_NOT_FOUND` |
| 연결 코드 만료 | 코드 닫힘 | 다시 `코드 받기` |

## 보안·개인정보

- 기기 목록은 서버가 공개 식별자만 준다. 설치 식별자·토큰은 받지 않는다.
- 동의 철회는 확인 창을 거친다. 이미 남은 기록은 지우지 않는다(개인정보 정책 철회 기준).

## 완료 조건

- [x] 기획 확인
- [x] 계정·기기 연결 탭 구현과 오류 상태
- [x] 상태별 캡처로 PM 디자인 확인
- [x] 실제 계정 QA: 닉네임 변경, 동의 철회 → 다시 동의, 연결 해제 → 다시 연결
