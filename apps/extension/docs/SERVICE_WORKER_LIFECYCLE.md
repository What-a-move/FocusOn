# Chrome Extension Service Worker 생명주기

> 현재 상태: `public/service-worker.js`는 안내 주석만 있는 진입 파일이다. Event Listener, 상태 복구, Server 연결은 아직 구현하지 않았다.
> 결정 상태: 자동 추적 Event, 저장 위치와 15초 체류 처리 방식은 확정했다.

## 핵심 원칙

Manifest V3 Service Worker는 항상 실행되는 Background Page가 아니다. Chrome이 필요할 때 실행하고 유휴 상태에서는 종료할 수 있으므로 메모리만 믿지 않는다.

## 시작과 Event 등록

- Event Listener는 Worker 최상위 Scope에서 동기적으로 등록한다.
- 비동기 초기화가 끝난 뒤 Listener를 등록하지 않는다.
- `runtime.onInstalled`는 설치·업데이트 1회 초기화에 사용한다.
- `runtime.onStartup`과 각 Event 처리 전에 필요한 상태를 복구한다.
- 같은 초기화가 반복되어도 결과가 깨지지 않게 Idempotent하게 작성한다.

Idempotent는 같은 작업을 여러 번 실행해도 결과가 한 번 실행한 것과 같은 성질이다.

## 저장 기준

| 데이터 | 저장 위치 방향 | 이유 |
| --- | --- | --- |
| Event 처리 중 임시 값 | 함수 지역 변수 | 처리 종료 후 불필요 |
| 제외 Domain | `chrome.storage.local` | 기기별 개인정보 설정을 유지 |
| 현재 Tab·Navigation 식별값 | `chrome.storage.session` | Worker 재시작 후 복구하고 Browser 종료 시 제거 |
| 학습 세션 기준 상태 | Server | 여러 Client의 공통 기준 |
| 세션·연결 상태의 짧은 Cache | `chrome.storage.session` | Popup 표시와 Worker 복구 보조 |
| Popup 조회 Cache | TanStack Query | Popup UI의 Server 상태 관리 |

- 제외 Domain은 `chrome.storage.local`에 저장하고 `setAccessLevel()`로 신뢰된 Extension Context에서만 접근한다.
- Server가 학습 세션과 연결 상태의 최종 기준이며 Local Cache를 기준 상태로 승격하지 않는다.
- Token과 Page 본문을 `local` 또는 `sync`에 저장하지 않는다.
- 로그인 Token 저장 방식은 인증·기기 연결 흐름을 확정할 때 별도로 결정한다.

## 자동 추적 Event

- `tabs.onActivated`로 사용자가 보고 있는 활성 Tab 전환을 확인한다.
- `webNavigation.onCommitted`에서 `frameId === 0`인 최상위 Frame의 실제 이동을 확인한다.
- 활성 학습 세션이 없으면 Tracking을 시작하지 않는다.
- Background Tab과 Incognito Window는 기본 분석하지 않는다.
- 두 Event가 같은 이동을 알리면 `tabId`와 Navigation ID로 한 번만 처리한다.

## 15초 체류 분석

1. Worker가 활성 Tab의 최상위 Page 이동을 확인하고 제외 Domain과 활성 세션을 검사한다.
2. Worker가 Navigation ID와 시작 시각을 `chrome.storage.session`에 기록한다.
3. Worker가 Content Script에 `DWELL_TRACKING_STARTED`를 보내고 Content Script가 15초 Timer를 시작한다.
4. 15초가 지나면 Content Script가 `DWELL_THRESHOLD_REACHED`를 보내 Worker를 다시 깨운다.
5. Worker는 현재 활성 Tab, Navigation ID, URL, 제외 상태와 Server 세션 상태를 다시 확인한다.
6. 모두 일치할 때만 `TAB_CONTEXT_REQUESTED`로 최소 Page 정보를 요청한다.

- Tab 비활성화·Page 이동·세션 일시정지·제외 Domain 진입 시 `DWELL_TRACKING_CANCELLED`를 보내고 저장된 Navigation을 무효화한다.
- 취소 Message를 받지 못한 오래된 Timer가 실행돼도 Worker 재검증에서 폐기한다.
- Service Worker의 전역 `setTimeout`은 Worker 종료 시 사라지므로 사용하지 않는다.
- Chrome 120의 `chrome.alarms` 최소 주기는 30초이므로 정확한 15초 체류 Timer로 사용하지 않는다.

## Network와 재연결

- 조회와 분석 요청은 Timeout을 가진다.
- Worker 종료가 요청 성공으로 오해되지 않게 Server 응답을 확인한다.
- 상태 변경 요청은 중복 실행을 막는 Request ID나 Server Version을 사용한다.
- Desktop·Server 연결 실패 후 무한 Loop로 재연결하지 않는다.
- 다시 실행되면 Server의 최신 세션 상태를 조회한다.
- Server가 연결·세션 상태의 최종 기준이며 `chrome.storage.session` 값은 화면 복구용 임시 Cache로만 사용한다.

## 제외 Domain 연동

- Worker가 현재 Domain을 정규화한 뒤 `hostname === excluded` 또는 `hostname.endsWith('.' + excluded)` 방식으로 정확한 Domain과 하위 Domain을 확인한다.
- 단순 `endsWith(excluded)`만 사용해 `not-example.com` 같은 다른 Domain이 잘못 제외되지 않게 한다.
- 제외 Domain에서는 Content Script에 Page 본문을 요청하지 않는다.
- 제외 상태만 Server에 보내고 Desktop이 받으면 화면·OCR·AI·Camera 분석을 모두 중지한다.
- Server 전송에 실패하면 제외 상태를 성공으로 오해하지 않으며 Desktop은 최신 상태를 확인할 수 없는 동안 분석을 중지한다.
- 제외 Domain을 벗어나면 새로운 Navigation ID와 Server 세션 상태를 확인한 뒤 Tracking을 다시 시작한다.

## 지원 Version

- 최소 지원 Version은 Chrome 120이다.
- `chrome.storage.session`과 Manifest V3 Service Worker의 종료·복구를 기본 전제로 구현한다.

## Message 처리

- Message 수신마다 발신자와 Payload를 검증한다.
- 오래 걸리는 작업은 응답 Channel이 닫히는 상황을 처리한다.
- Popup이 닫혀 있을 수 있음을 정상 상태로 취급한다.
- Content Script가 없는 제한 Page에 Message를 보낼 때 오류를 정상 처리한다.

## 테스트 기준

- Extension 설치·업데이트
- Chrome 재시작
- Worker 수동 종료 후 Event 재수신
- Popup 열기·닫기 반복
- Tab 이동 중 15초 Timer 취소
- Network 단절과 복구
- 동일 Event 중복 수신

## 공식 참고

- [Chrome Extension Service Worker Lifecycle](https://developer.chrome.com/docs/extensions/develop/concepts/service-workers/lifecycle)
- [Chrome Extension Message Passing](https://developer.chrome.com/docs/extensions/develop/concepts/messaging)
