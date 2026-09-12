# Desktop 다음 작업

> Frontend(`apps`) 담당자는 Desktop 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Electron Main·Preload 기본 구조 구현
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 우선순위: 높음
- 상태: 예정

## 작업 목적

확정된 구조에 따라 React 화면과 Electron의 macOS 기능을 실제 코드로 분리해 이후 활성 앱 확인, 화면 캡처, 권한 처리를 연결할 수 있도록 한다.

## 선행 조건

- Desktop 기본 화면이 실행되어야 한다.
- `ELECTRON_ARCHITECTURE.md`, `IPC_CONTRACT.md`, `WINDOW_LIFECYCLE.md`의 확정 기준을 따라야 한다.
- 관련 기획서를 `features/`에 작성해야 한다.

## 예상 작업 순서

1. Main·Preload TypeScript 진입점과 전용 Build 설정을 만든다.
2. 개발 `127.0.0.1` Loading과 Production `app://` Loading을 구현한다.
3. Single Instance와 Main Window 숨김·재실행 흐름을 구현한다.
4. 확정한 IPC 공통 결과와 최소 Channel을 구현한다.
5. 로컬에서 Desktop Build와 실행을 확인한다.

## 이후 작업 후보

- 학습 세션 타이머
- 화면 부유형 두더지 마스코트 피드백 및 표시 모드 설정
- Google 로그인 후 Chrome Extension 연결
- Desktop·Extension 학습 세션 상태 동기화
- macOS 활성 앱과 Bundle Identifier 확인
- 분석 제외 앱 설정
- 화면 캡처 권한과 캡처 중지
- MediaPipe 카메라 권한 및 상태 연결

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] Electron 영역별 책임을 문서화했다.
- [ ] 정상 실행과 종료를 확인했다.
- [ ] 권한 거부 상황을 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `ELECTRON_ARCHITECTURE.md`를 확인했다.
- [ ] `IPC_CONTRACT.md`에서 이번 기능에 필요한 Channel과 Payload를 확인했다.
- [ ] `MACOS_PERMISSION_FLOW.md`와 `WINDOW_LIFECYCLE.md`의 확정 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 관련 Issue와 Branch가 있다.
