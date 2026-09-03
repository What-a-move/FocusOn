# Desktop 다음 작업

> Desktop 작업자는 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: Electron Main/Renderer 기본 구조 확정
- 담당 영역: Desktop
- 우선순위: 높음
- 상태: 예정

## 작업 목적

React 화면과 Electron의 macOS 기능을 안전하게 분리해 이후 활성 앱 확인, 화면 캡처, 권한 처리를 연결할 수 있도록 한다.

## 선행 조건

- Desktop 기본 화면이 실행되어야 한다.
- Electron 진입점과 Renderer 통신 방식에 대한 결정이 필요하다.
- 관련 기획서를 `features/`에 작성해야 한다.

## 예상 작업 순서

1. Electron Main·Preload·Renderer 책임을 정리한다.
2. 안전한 IPC 통신 규칙을 결정한다.
3. 앱 실행·종료·권한 거부 흐름을 구현한다.
4. 로컬에서 Desktop 빌드와 실행을 확인한다.

## 이후 작업 후보

- 학습 세션 타이머
- macOS 활성 앱과 Bundle Identifier 확인
- 분석 제외 앱 설정
- 화면 캡처 권한과 캡처 중지
- MediaPipe 카메라 권한 및 상태 연결

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] Electron 영역별 책임을 문서화했다.
- [ ] 정상 실행과 종료를 확인했다.
- [ ] 권한 거부 상황을 확인했다.
- [ ] 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 관련 Issue와 Branch가 있다.

