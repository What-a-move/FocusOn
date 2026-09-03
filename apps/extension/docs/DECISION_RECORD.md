# Extension 결정 기록

> Chrome Extension의 권한·수집·분석·Desktop 연결에 관한 합의를 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - Extension 규격

- 결정일: 2026-09-03
- 담당 영역: Chrome Extension
- 상태: 확정

### 결정 내용

Chrome Extension은 Manifest V3를 기준으로 개발한다.

### 결정 이유

현재 Chrome 확장 기능의 표준 구조에 맞춰 Popup, Content Script, Service Worker를 분리하기 위해서다.

### 영향 범위

- `public/manifest.json`
- Content Script
- Service Worker
- Chrome 권한과 보안 정책

## 결정 002 - 분석 제외 도메인

- 결정일: 2026-09-03
- 담당 영역: Chrome Extension
- 상태: 확정

### 결정 내용

사용자가 설정한 제외 도메인은 페이지 본문 분석과 Server·AI 전송을 중지한다.

### 결정 이유

사용자가 원하지 않는 사이트의 정보가 분석 대상이 되는 것을 막기 위해서다.

### 후속 확인

- [ ] 하위 도메인 포함 여부
- [ ] 설정 저장 위치
- [ ] Desktop과 제외 목록 동기화 방법
- [ ] 제외 상태 이벤트를 저장할지 여부

## 새 결정 기록

다음 결정은 `결정 003`부터 추가한다.

### 결정 003

- 결정일:
- 주제:
- 상태: 제안 / 확정 / 변경됨 / 폐기
- 결정 내용:
- 결정 이유:
- 고려한 대안:
- 영향받는 파일:
- 관련 Issue·PR:

