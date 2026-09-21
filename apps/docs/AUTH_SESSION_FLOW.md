# Frontend 인증·세션 흐름

## 목적

Google 로그인 사용자, Desktop, Chrome Extension, Server가 같은 사용자를 안전하게 식별하도록 인증 흐름과 실패 처리를 정의한다.

## 기준 흐름

```text
Google 로그인
  → Server가 사용자 검증
  → Frontend가 인증 상태 확인
  → Desktop과 Extension 연결
  → Server의 학습 세션 조회
  → 만료 시 인증 복구 또는 재로그인
```

학습 세션의 상태와 기준 시간은 Server가 확정한다. Desktop과 Extension은 인증된 동일 사용자의 Server 상태를 조회해 화면을 갱신한다.

## 상태 구분

| 상태 | 의미 | UI 처리 |
| --- | --- | --- |
| `SIGNED_OUT` | 로그인하지 않음 | 로그인 방법 표시 |
| `AUTHENTICATING` | 사용자 검증 중 | 중복 요청 차단과 Loading 표시 |
| `SIGNED_IN` | 사용자 검증 완료 | 앱 기능 제공 |
| `LINK_REQUIRED` | Desktop·Extension 연결 필요 | 연결 방법 표시 |
| `EXPIRED` | 인증 만료 | 인증 복구 또는 재로그인 안내 |
| `OFFLINE` | Server 연결 불가 | 마지막 확인 시각과 재연결 안내 |

상태 이름은 API 계약 확정 시 `packages/shared-types`의 타입과 일치시킨다.

## Token 처리

- Token, Cookie, 연결 Code를 Console과 오류 문서에 기록하지 않는다.
- 인증 정보를 URL Query에 장기간 유지하지 않는다.
- React Component와 Zustand Store에 Token 원문을 저장하지 않는다.
- Desktop Renderer와 Content Script에 장기 인증 정보를 직접 노출하지 않는다.
- Desktop과 Extension의 실제 저장 방식은 보안 검토 후 `DECISION_RECORD.md`에서 확정한다.

## Desktop·Extension 연결

- 연결 Code는 짧은 유효시간과 일회성 사용을 기본 방향으로 검토한다.
- 연결 성공 전에는 다른 사용자의 세션 정보를 표시하지 않는다.
- 연결 해제 시 저장된 연결 정보와 Cache를 정리한다.
- 한쪽 앱의 연결 실패가 다른 앱이나 Chrome Page 동작을 멈추게 하지 않는다.

## 만료와 오류

- 인증 만료 응답을 일반 Server 오류로 표시하지 않는다.
- 안전하게 재실행할 수 있는 조회만 자동 복구한다.
- 세션 시작·종료처럼 상태를 바꾸는 요청은 성공 여부를 확인하기 전 중복 전송하지 않는다.
- 로그아웃 후 TanStack Query Cache와 사용자별 Zustand 상태를 정리한다.

## 확정이 필요한 항목

- Google 로그인 구현 방식
- Desktop 인증 정보 저장 위치
- Extension 인증 정보 저장 위치
- Desktop·Extension 최초 연결 방식
- Access Token 갱신 방식과 만료 시간
