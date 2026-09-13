# Extension 다음 작업

> Frontend(`apps`) 담당자는 Extension 작업 시작 전에 이 문서를 확인하고, 작업 종료 후 다음 작업을 갱신한다.

## 가장 먼저 진행할 작업

- 작업명: 현재 Tab 자동 추적과 Service Worker 전달 구조 구현
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 우선순위: 높음
- 상태: 예정

## 작업 목적

확정된 `tabs`·`webNavigation`·Message 계약에 따라 활성 학습 세션 중 현재 Tab, 페이지 제목, 도메인 정보를 수집하고 Server 세션과 연결한다.

## 선행 조건

- Extension 기본 화면과 Manifest V3 구조가 실행되어야 한다.
- 수집 가능한 데이터와 제외 도메인 정책을 확인해야 한다.
- 전체 Host 권한 승인 방식을 사용자와 확정해야 한다.
- 기능 기획서를 `features/`에 작성해야 한다.

## 예상 작업 순서

1. Host 권한 승인 방식을 확정하고 Manifest 변경 범위를 PLAN에 기록한다.
2. `tabs.onActivated`와 `webNavigation.onCommitted` Listener를 구현한다.
3. Navigation ID와 `chrome.storage.session` 기반 중복 제거를 구현한다.
4. 제외 Domain의 `chrome.storage.local` 저장과 전체 분석 중지 신호를 구현한다.
5. Content Script 15초 Timer와 확정 Message를 구현한다.
6. Server 세션 확인과 분석 요청을 연결한다.
7. Chrome 개발자 모드에서 정상·예외 흐름을 확인한다.

## 이후 작업 후보

- 15초 체류 후 페이지 재분석
- 현재 페이지 관련성 분석 요청
- 분석 제외 도메인 설정 UI
- Google 로그인 사용자와 Extension 연결
- Desktop 학습 세션 조회 및 타이머 표시
- Desktop 세션 일시정지·재개·종료 명령
- Desktop 연결 상태 표시
- Service Worker 재시작 후 상태 복구

## 완료 조건

- [ ] 기능 기획서를 먼저 작성했다.
- [ ] 필요한 권한만 Manifest에 선언했다.
- [ ] 정상 탭 변경 흐름을 확인했다.
- [ ] 제외 도메인 흐름을 확인했다.
- [ ] Desktop 연결 실패를 확인했다.
- [ ] 새 기능을 완료했다면 결과 리포트를 작성했다.

## 작업 시작 전 확인

- [ ] `EXTENSION_ARCHITECTURE.md`를 확인했다.
- [ ] `MESSAGE_CONTRACT.md`에서 이번 기능에 필요한 Message와 Payload를 확인했다.
- [ ] `MANIFEST_PERMISSION_POLICY.md`의 Host 권한 제안을 사용자와 확정했다.
- [ ] `SERVICE_WORKER_LIFECYCLE.md`의 15초 체류·저장 기준을 확인했다.
- [ ] `CONTEXT.md`를 읽었다.
- [ ] `DECISION_RECORD.md`를 읽었다.
- [ ] 관련 기능의 `*-PLAN.md`를 확인했다.
- [ ] 현재 Branch가 Issue와 연결되어 있다.
