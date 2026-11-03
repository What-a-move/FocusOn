# Frontend 아키텍처

## 기본 구조

```text
Page 또는 플랫폼 진입점
  → Feature UI Component
  → Feature Hook
  → Service 또는 Platform Adapter
  → Server API / Electron IPC / Chrome API
```

각 단계는 바로 아래 단계만 호출하는 것을 기본으로 한다. Page가 URL, 인증 헤더, Electron IPC 채널, Chrome API 세부 구현을 직접 관리하지 않는다.

## 단순 조회 예외

재사용되지 않는 단순 조회 화면이고 별도 사용자 입력·캐시 갱신·플랫폼 상태가 없다면 빈 Component와 Hook을 억지로 만들지 않고 `Page → Service`로 호출할 수 있다.

- 예외를 사용해도 Axios 인스턴스와 오류 변환은 공통 API Client를 사용한다.
- 로딩·재시도·캐시 무효화·여러 화면 공유가 필요해지면 Feature Hook으로 분리한다.
- Electron IPC와 Chrome API는 단순 조회여도 Platform Adapter를 통한다.

## 계층별 책임

| 계층 | 책임 | 하지 않는 일 |
| --- | --- | --- |
| Page | 화면 조립과 진입점 제공 | 복잡한 상태 변경·API 구현 |
| Component | 사용자에게 상태 표시, 이벤트 전달 | Server 응답 형식 직접 해석 |
| Hook | 화면 상태와 사용자 동작 연결 | 운영체제·브라우저 API 직접 노출 |
| Service | API 요청, 응답 변환, 오류 정규화 | JSX 렌더링 |
| Platform Adapter | Electron·Chrome 기능을 안전한 함수로 제공 | 화면 문구 결정 |
| Shared Types | 앱 사이의 공통 데이터 모양 제공 | 런타임 검증과 API 호출 |

## 앱별 경계

### Desktop

- Renderer는 React 화면을 담당한다.
- Preload는 Renderer에 허용할 Electron API만 노출한다.
- Main은 창, macOS 권한, 활성 앱, 화면 캡처 같은 운영체제 기능을 담당한다.
- Renderer에서 Node.js 또는 Electron Main API를 직접 사용하지 않는다.

### Extension

- Popup은 세션 상태와 사용자 제어 화면을 담당한다.
- Content Script는 허용된 페이지 정보만 읽는다.
- Service Worker는 탭 이벤트, 메시지 전달, 상태 복구를 담당한다.
- 페이지 코드가 Extension 내부 상태나 인증 정보에 직접 접근하지 못하게 한다.

## 공통화 기준

- 두 앱이 동일한 데이터 계약을 사용하면 `packages/shared-types`로 이동한다.
- UI가 비슷해도 실행 환경과 사용자 흐름이 다르면 성급하게 공통 컴포넌트로 묶지 않는다.
- 같은 로직이 세 번 이상 반복되거나 두 앱이 반드시 동일하게 계산해야 할 때 공통화를 검토한다.
- Server가 기준인 세션 상태를 각 앱이 독립적으로 확정하지 않는다.
