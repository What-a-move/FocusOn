# Extension 결정 기록

> Chrome Extension의 권한·수집·분석·Desktop 연결에 관한 합의를 기록한다.
> 기존 결정을 삭제하지 않고 새로운 번호로 추가한다.

## 결정 001 - Extension 규격

- 결정일: 2026-09-03
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
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
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

사용자가 설정한 제외 도메인과 하위 도메인은 페이지 본문 수집과 분석 전송을 즉시 중지한다. Extension은 제외 상태만 Server에 전달하고, Desktop은 해당 상태를 받으면 화면·OCR·AI·Camera 분석을 모두 중지한다.

### 결정 이유

사용자가 원하지 않는 사이트의 정보가 분석 대상이 되는 것을 막기 위해서다.

### 확정 기준

- `example.com`을 제외하면 `www.example.com` 같은 하위 도메인도 제외한다.
- 제외 목록은 `chrome.storage.local`에 저장하고 Content Script에서 직접 접근하지 않는다.
- 제외 상태는 Server를 통해 Desktop에 전달한다.
- 원본 URL·본문 대신 정규화한 Domain과 제외 여부만 전달한다.
- Desktop이 Server에서 최신 제외 상태를 확인할 수 없으면 확인될 때까지 화면·OCR·AI·Camera 분석을 중지한다.

### 결정 003 - Desktop 학습 세션 조회·제어

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)·Server
- 대상 앱: Desktop·Extension
- 상태: 확정

#### 결정 내용

Extension은 Desktop에서 시작한 학습 세션을 조회해 타이머를 표시하고, 일시정지·재개·종료 명령을 Server에 요청한다. 세션 상태와 기준 시간은 Server가 관리하며 Extension은 독립적으로 시간을 확정하지 않는다.

#### 결정 이유

Popup이 닫히거나 Service Worker가 다시 시작되어도 Desktop과 Extension의 시간이 달라지지 않도록 하기 위해서다.

#### 후속 확인

- [ ] Google 로그인 후 Extension 연결 방식 확정
- [ ] 세션 상태 조회 주기와 실시간 동기화 방식 확정
- [ ] 네트워크 단절 시 마지막 상태 표시와 재동기화 기준 확정

### 결정 004 - Popup의 피드백 표시 범위

- 결정일: 2026-09-05
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

#### 결정 내용

집중 상태를 표현하는 두더지 마스코트의 주 표시 영역은 Desktop 화면 위에 떠 있는 부유형 창으로 둔다. Extension Popup은 타이머·세션 상태·연결 상태와 최소한의 분석 결과를 표시하며, Desktop의 부유형 마스코트와 중복되는 장식 요소는 기본 제공하지 않는다.

#### 결정 이유

Chrome Popup은 사용자가 다른 곳을 클릭하면 닫히므로 지속적인 피드백은 Desktop의 부유형 창이 담당하는 편이 안정적이다.

## 결정 005 - 활성 학습 세션 중 현재 Tab 자동 추적

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

Server에서 활성 학습 세션이 확인된 동안 사용자가 보고 있는 현재 활성 Tab을 자동 추적한다. `tabs.onActivated`는 Tab 전환, `webNavigation.onCommitted`는 최상위 Frame의 실제 Page 이동을 담당한다. Background Tab과 Incognito Window는 기본 분석하지 않는다.

### 결정 이유

사용자가 Page를 이동할 때마다 Extension을 누르지 않아도 학습 흐름을 이어서 분석하되, 학습 중이 아닌 탐색과 보이지 않는 Tab까지 과도하게 수집하지 않기 위해서다.

## 결정 006 - Extension 저장소와 Server 기준 상태

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

- 제외 Domain은 `chrome.storage.local`에 저장한다.
- 현재 Navigation과 짧은 연결 Cache는 `chrome.storage.session`에 저장한다.
- 학습 세션과 Desktop 연결 상태의 최종 기준은 Server다.
- Page 본문과 인증 Token은 `chrome.storage.local` 또는 `sync`에 저장하지 않는다.

### 결정 이유

제외 Domain처럼 기기에서 유지할 설정과 Worker 재시작 동안만 필요한 임시 상태를 분리하고, Desktop·Extension이 서로 다른 세션 상태를 가지는 문제를 막기 위해서다.

## 결정 007 - 15초 체류와 Message 계약

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

15초 체류 Timer는 Content Script에서 실행하고 Service Worker가 시작·취소·최종 검증을 담당한다. Worker는 Navigation ID를 `chrome.storage.session`에 기록하고 Timer 완료 Message를 받으면 현재 활성 Tab·URL·제외 상태·Server 세션을 다시 확인한다. Message 이름과 Payload는 `MESSAGE_CONTRACT.md`를 기준으로 사용한다.

### 결정 이유

Manifest V3 Service Worker는 유휴 상태에서 종료될 수 있으므로 Worker의 전역 Timer에 의존할 수 없다. Content Script Timer와 Worker 재검증을 함께 사용하면 Worker가 다시 시작되거나 Page가 이동한 경우에도 오래된 분석 요청을 막을 수 있다.

## 결정 008 - Chrome 120과 HTTP Page 지원

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 확정

### 결정 내용

최소 지원 Version은 Chrome 120으로 정한다. 오래된 학습 사이트를 위해 HTTP Page 분석도 지원하지만 FocusOn Server와 인증 통신은 HTTPS만 사용한다.

### 결정 이유

Manifest V3 Service Worker와 `chrome.storage.session`을 일관된 기준으로 사용하면서 HTTP로만 제공되는 학습 자료도 분석하기 위해서다.

## 결정 009 - 전체 Host 권한 승인 방식

- 결정일: 2026-09-13
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 상태: 제안

### 제안 내용

`https://*/*`와 `http://*/*`는 `optional_host_permissions`에 선언하고 사용자가 자동 추적을 켜는 동작 안에서 이유를 설명한 뒤 요청한다. `scripting` 권한을 사용해 Worker가 활성 세션의 현재 Page에만 Content Script를 주입하고, 승인하지 않은 사이트에서는 분석을 중지하고 권한 안내를 표시한다. 이 제안이 확정되기 전에는 Manifest를 변경하지 않는다.

### 확정 필요

- 전체 사이트 권한을 한 번에 요청할지
- 사이트별 권한 모드도 함께 제공할지
- 권한을 거부하거나 철회했을 때의 Popup 문구

## 새 결정 기록

다음 결정은 `결정 010`부터 추가한다.

### 결정 010

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
