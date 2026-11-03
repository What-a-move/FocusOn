# Frontend Mocking 가이드

## 목적

Mock은 실제 Server·Electron·Chrome 기능 대신 약속된 가짜 응답을 제공하는 개발 도구다. 다른 영역의 구현을 기다리지 않고 UI와 예외 흐름을 확인할 때 사용한다.

## Mock 대상

- Server API 성공·빈 데이터·오류·지연 응답
- Electron IPC 성공·권한 거부·지원하지 않는 기능
- Chrome Message 성공·연결 끊김·접근 제한 Page
- 학습 세션의 실행·일시정지·완료 상태

## 작성 원칙

- 실제 API, IPC, Message Contract와 같은 타입을 사용한다.
- Component 안에 Mock 데이터를 직접 작성하지 않는다.
- Service 또는 Platform Adapter 경계에서 실제 구현과 교체한다.
- 실제 사용자의 Token, URL, Page Text, Screen Data를 Fixture로 사용하지 않는다.
- 성공 Mock만 만들지 않고 오류와 지연 Scenario를 함께 만든다.
- Random 값보다 재현 가능한 고정 Fixture를 우선한다.

## Scenario 이름

```text
session-running
session-not-found
server-timeout
screen-permission-denied
desktop-disconnected
restricted-page
```

## 운영 Build 차단

- Mock 활성 여부는 명시적인 개발·테스트 환경값으로 관리한다.
- Production 기본값은 항상 Mock 비활성화다.
- Mock Server와 개발용 인증 우회가 Production Bundle에 포함되지 않는지 Build 전에 확인한다.

## 도구 결정

MSW와 테스트 Runner 연동 방식은 아직 확정하거나 설치하지 않는다. 실제 API Client와 테스트 도구를 구성할 때 사용자와 비교 후 선택한다.

## 종료 조건

- 실제 API가 연결되면 같은 Scenario로 실제 응답을 확인한다.
- Contract가 바뀌면 Mock과 Fixture를 함께 갱신한다.
- 더 이상 사용하지 않는 Mock은 기능 코드와 함께 제거한다.
