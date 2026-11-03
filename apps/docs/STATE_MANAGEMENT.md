# 상태 관리 규칙

> 기술 방향: Zustand와 TanStack Query 사용을 확정했다. 현재 Package는 설치하지 않았으며 전체 문서 검토가 끝난 뒤 별도 설치 작업으로 진행한다.

## 상태 구분

| 상태 종류 | 예시 | 기준 위치 |
| --- | --- | --- |
| 화면 상태 | 모달 열림, 선택된 탭 | 컴포넌트 또는 Hook |
| 클라이언트 공유 상태 | 설정 화면, 로컬 연결 상태 | Zustand Store |
| 서버 상태 | 학습 세션, 서버 사용자 설정 | TanStack Query와 Server 응답 |
| 플랫폼 상태 | macOS 권한, Chrome 탭 | Platform Adapter |

## 선택 기준

1. 한 컴포넌트에서만 사용하면 `useState`를 사용한다.
2. 가까운 부모와 자식이 공유하면 Props로 전달한다.
3. 여러 컴포넌트가 공유하는 클라이언트 상태는 Zustand Store를 사용한다.
4. Server에서 조회하거나 변경하는 상태는 TanStack Query를 사용한다.
5. Context는 Theme이나 Provider 설정처럼 React 트리에 의존하는 값에 제한한다.
6. Server 데이터는 TanStack Query의 Cache에 있어도 Server가 최종 기준이다.

## 라이브러리 역할

| 도구 | 사용하는 상태 | 사용하지 않는 상태 |
| --- | --- | --- |
| `useState` | 한 컴포넌트의 짧은 UI 상태 | 여러 화면이 공유하는 상태 |
| Zustand | 클라이언트 공유 상태와 사용자 인터랙션 상태 | Server 응답 복제본 |
| TanStack Query | Server 조회·변경·캐시·동기화 상태 | 모달 열림 같은 순수 UI 상태 |
| Redux Toolkit | 현재 프로젝트에서는 사용하지 않음 | 학습만을 위한 중복 Store |

Redux는 상태량과 변경 규칙이 크게 늘고 변경 이력·Middleware·엄격한 Action 흐름이 필요해질 때 다시 검토한다. Redux를 도입한다면 Legacy Redux가 아니라 Redux Toolkit을 사용한다.

## FocusOn 세션 원칙

- 세션의 시작·일시정지·재개·종료 상태와 기준 시간은 Server가 확정한다.
- Desktop과 Extension은 Server 응답을 받은 뒤 UI를 갱신한다.
- 매초 Server에 요청하지 않고 서버 기준 시각을 이용해 화면 시간을 계산한다.
- 네트워크가 끊기면 마지막 동기화 시각과 연결 끊김 상태를 함께 표시한다.
- Popup과 Service Worker 메모리를 영구 저장소처럼 사용하지 않는다.

## 금지

- 동일한 Server 상태를 여러 컴포넌트에서 따로 계산하지 않는다.
- Props 전달이 불편하다는 이유만으로 모든 값을 전역 상태로 올리지 않는다.
- TanStack Query 데이터를 Zustand에 복사해 두 개의 기준을 만들지 않는다.
- 인증 토큰이나 민감한 원문 데이터를 UI Store에 장기 보관하지 않는다.
