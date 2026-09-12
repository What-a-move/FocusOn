# Desktop 결정 기록

> Desktop 설계·기술·권한 처리에 관한 합의를 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - Desktop 실행 환경

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

macOS 전용 Desktop 앱은 Electron + Next.js + React + TypeScript 기반으로 개발한다.

### 결정 이유

Next.js 정적 빌드 기반의 React 화면 기술을 유지하면서 macOS 앱 확인, 화면 캡처, 알림, 권한 처리를 Electron에 연결하기 쉽다.

### 영향 범위

- Renderer 화면 구조
- Main·Preload·Renderer 분리
- macOS 권한 처리
- Extension과의 상태 연결

## 결정 002 - 민감 데이터 기본 처리

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

원본 카메라 영상과 원본 화면을 기본 저장하지 않고, 분석에 필요한 상태값과 최소 메타데이터만 사용한다.

### 결정 이유

사용자의 개인정보 노출과 불필요한 저장을 줄이기 위해서다.

### 후속 확인

- [x] 카메라 권한 거부 UX 확정
- [ ] 화면 캡처 데이터의 메모리 처리 범위 확정
- [ ] Server 전송 필드 확정

### 결정 003 - Desktop 중심 학습 세션과 Extension 동기화

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)·Server
- 대상 앱: Desktop·Extension
- 상태: 확정

#### 결정 내용

Desktop과 Chrome Extension은 Google 로그인으로 연결된 동일 사용자의 학습 세션을 공유한다. 학습 세션의 기준 상태와 시간은 Server가 관리하고, Desktop은 학습 시작·카메라·화면 분석을 주로 담당하며 Extension은 타이머 조회와 일시정지·재개·종료 명령을 제공한다.

#### 결정 이유

두 클라이언트가 각자 타이머를 계산하면 시간 차이가 발생할 수 있다. Server를 기준으로 두면 Desktop에서 시작한 세션을 Extension에서 확인하거나, Extension에서 일시정지한 상태를 Desktop에 동일하게 반영하기 쉽다.

#### 고려한 대안

- Desktop과 Extension을 Native Messaging으로 직접 연결하는 방식
- 각 클라이언트가 독립적으로 타이머를 계산하는 방식

#### 후속 확인

- [ ] Google 로그인 후 Extension 최초 연결 방식(일회용 코드 또는 QR) 확정
- [ ] 초기 MVP 동기화 방식(API 주기 조회 또는 WebSocket) 확정
- [ ] 네트워크 끊김과 동시 명령 처리 기준 확정

### 결정 004 - 화면 부유형 두더지 피드백

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

#### 결정 내용

학습 시작 시 Desktop 화면 위에 떠 있는 두더지 마스코트를 표시한다. 집중 상태에서는 파인 땅에서 한 손으로 팻말을 들고 응원 문구를 보여주고, 집중 이탈 상태에서는 표정·자세·말풍선을 바꿔 복귀를 안내한다. 상단 메뉴 막대 아이콘은 상태 확인·설정·앱 종료를 위한 보조 메뉴로만 사용한다.

#### 결정 이유

분석 결과를 숫자나 경고창만으로 전달하는 대신 FocusOn만의 반복 사용 가능한 화면 부유형 피드백 경험을 제공하기 위해서다.

#### 사용자 설정

- 팻말에 `남은 시간` 표시
- 팻말에 매번 바뀌는 `응원 문구` 표시
- 응원·경고 피드백 표시 여부
- 피드백 강도와 알림 빈도

#### 후속 확인

- [ ] 두더지 상태별 디자인 목록 확정
- [ ] 문구 생성·선택 방식과 금칙어 기준 확정
- [ ] 동일 사이트 재방문 시 문구 강도 기준 확정

## 결정 005 - Electron Build·Loading·배포 방식

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Main·Preload는 TypeScript Compiler로 `dist-electron/`에 Build한다.
- Renderer는 Next.js Static Export의 `out/`을 사용한다.
- 개발 환경은 `http://127.0.0.1:3000`, Production은 보안 설정된 `app://` Custom Protocol로 Renderer를 연다.
- Packaging은 현재 설치된 electron-builder를 사용한다.
- 배포 시 `arm64`와 `x64`를 각각 Build해 `dmg`와 `zip`을 제공한다.
- 정식 배포는 Developer ID Application 서명, Hardened Runtime, Apple Notarization과 Stapling을 적용한다.
- 자동 Update는 MVP에서 제외한다.

### 결정 이유

현재 설치된 도구를 활용하면서 Main·Preload와 Next.js Renderer의 Build 책임을 분리할 수 있다. Production에서 `file://`보다 접근 범위를 제한한 전용 Protocol을 사용하고, Architecture별 Build를 분리하면 이후 Native Module이 추가될 때 Universal Merge 문제를 줄일 수 있다.

## 결정 006 - Window와 앱 생명주기

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- 앱은 Single Instance로 실행한다.
- Main Window를 닫으면 Window만 숨기고 앱과 학습 세션은 유지한다.
- `Cmd+Q` 또는 명시적인 종료 메뉴에서만 앱을 완전히 종료한다.
- Settings는 MVP에서 Main Window의 `/settings` Route로 구현한다.
- Floating Window는 기본적으로 클릭 가능하며 지정된 Handle에서만 Drag한다.
- 위치는 Display ID와 화면 내부 비율로 저장하고 화면 구성이 바뀌면 Primary Display 안으로 보정한다.

### 결정 이유

학습 세션과 Floating Feedback을 유지하면서 macOS 앱의 일반적인 닫기·종료 동작을 제공하고, 초기 Window 수와 상태 복잡도를 줄이기 위해서다.

## 결정 007 - IPC 오류와 macOS 권한 UX

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 상태: 확정

### 결정 내용

- Preload에는 기능별 IPC 함수만 노출하고 범용 Channel 전송 함수를 제공하지 않는다.
- IPC 실패 응답은 `code`와 `retryable`만 전달하며 Renderer가 사용자 안전 문구로 변환한다.
- Camera·Screen Recording·Notification은 실제 사용 직전에 각각 이유를 설명하고 요청한다.
- 권한 거부 시 Timer와 앱은 유지하고 해당 분석 기능만 중지한다.
- 제외 앱 또는 제외 도메인 상태에서는 화면·OCR·AI·Camera 분석을 모두 중지한다.

### 결정 이유

Renderer에 내부 오류와 강한 Electron 권한이 노출되는 것을 막고, 사용자가 권한을 이해하고 선택할 수 있게 하기 위해서다.

## 새 결정 기록

다음 결정은 `결정 008`부터 추가한다.

### 결정 008

- 결정일:
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop / Extension / 공통
- 주제:
- 상태: 제안 / 확정 / 변경됨 / 폐기
- 결정 내용:
- 결정 이유:
- 고려한 대안:
- 영향받는 파일:
- 관련 Issue·PR:
