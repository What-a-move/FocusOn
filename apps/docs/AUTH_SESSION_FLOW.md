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

Desktop Google 로그인에서는 Main이 임시 loopback callback을 열고 `state`와 PKCE verifier를 만든다. callback의 `state` 일치를 확인한 뒤 받은 code, verifier, state, 인증 요청에 사용한 정확한 `redirectUri`, 기기 정보를 `POST /api/v1/auth/google/exchange`에 보낸다. Backend는 `redirectUri`를 `http://127.0.0.1:<임시 포트>`로 제한하고 같은 값으로 Google token endpoint에서 code를 교환한다. `state` 일치 검증의 책임은 Main에 있다.

## 상태 구분

| 상태 | 의미 | UI 처리 |
| --- | --- | --- |
| `SIGNED_OUT` | 로그인하지 않음 | 로그인 방법 표시 |
| `AUTHENTICATING` | 사용자 검증 중 | 중복 요청 차단과 Loading 표시 |
| `SIGNED_IN` | 사용자 검증 완료 | 앱 기능 제공 |
| `LINK_REQUIRED` | Desktop·Extension 연결 필요 | 연결 방법 표시 |
| `EXPIRED` | 인증 만료 | 인증 복구 또는 재로그인 안내 |
| `OFFLINE` | Server 연결 불가 | 마지막 확인 시각과 재연결 안내 |

이 표는 앱의 **인증 UI 상태**다. Notion 온보딩 흐름의 프로필 완료·Extension 연결·필수 권한 상태와는 별도이며, 한 상태로 합치지 않는다. API의 인증 오류는 아래 만료·오류 규칙에 따라 처리한다.

## Token 처리

- Token, Cookie, 연결 Code를 Console과 오류 문서에 기록하지 않는다.
- 인증 정보를 URL Query에 장기간 유지하지 않는다.
- React Component와 Zustand Store에 Token 원문을 저장하지 않는다.
- Desktop Renderer와 Content Script에 장기 인증 정보를 직접 노출하지 않는다.
- Access Token은 메모리에만 둔다. 유효 시간은 15분이다.
- Refresh Token은 30일 유효하며 사용할 때마다 rotation한다. Desktop은 macOS Keychain, Extension은 각자의 안전 저장 영역에 보관한다.
- Desktop과 Extension은 서로 독립된 token family를 사용한다. Desktop은 최대 3대까지 연결한다.
- `POST /api/v1/auth/refresh`는 JSON body의 `refreshToken`으로 호출하며 `Authorization` 헤더는 보내지 않는다.

## Desktop·Extension 연결

- 연결 Code는 5분 유효·3회 실패 제한·1회 사용이다. 새 코드를 발급하면 이전 코드는 즉시 폐기한다.
- 연결 성공 201은 `data.accessToken`, `refreshToken`, `accessTokenExpiresAt`(UTC ISO 8601), `device: {deviceId,deviceType: EXTENSION}`을 반환한다. 서버 내부 `CHROME_EXTENSION`은 공개 응답에서 매핑한다.
- 틀린 코드 1~2회는 400 `LINK_006_CODE_MISMATCH`와 `details.remainingAttempts`, 3번째는 410 `LINK_004_ATTEMPTS_EXCEEDED`다. `retryable: false`는 자동 재전송 금지이며 사용자의 수정 후 재입력은 가능하다.
- Desktop 연결 화면의 readiness 조회는 3초 간격이며 코드 수명 안에서만 실행한다. 완료·만료·화면 이탈 때 중단하고 다른 API·재시도와 일반 인증 한도 60회/분을 공유한다.
- 연결 성공 전에는 다른 사용자의 세션 정보를 표시하지 않는다.
- 연결 해제 시 저장된 연결 정보와 Cache를 정리한다.
- 한쪽 앱의 연결 실패가 다른 앱이나 Chrome Page 동작을 멈추게 하지 않는다.
- Desktop 인증 상태가 없거나 Refresh Token이 만료·폐기되면 기존 코드 `AUTH_RELOGIN_REQUIRED`로 인증 정보를 정리하고 Desktop 로그인 화면으로 이동한다. 별도 `LINK_005` 오류 코드는 사용하지 않는다.

## 만료와 오류

- `AUTH_ACCESS_TOKEN_EXPIRED`는 single-flight refresh 1회 후 원 요청을 1회만 재시도한다.
- refresh token이 없거나 만료·폐기됐으면 `AUTH_RELOGIN_REQUIRED`로 token을 지우고 로그인 화면으로 이동한다. Google OAuth 재인증 오류와 구분하고 refresh 무한 루프를 만들지 않는다.
- 인증 만료 응답을 일반 Server 오류로 표시하지 않는다. 안전하게 재실행할 수 있는 조회만 자동 복구한다.
- 세션 시작·종료처럼 상태를 바꾸는 요청은 성공 여부를 확인하기 전 중복 전송하지 않는다.
- 로그아웃 후 TanStack Query Cache와 사용자별 Zustand 상태를 정리한다.

## Notion 확정 기준

- Google OAuth 2.0 Authorization Code + PKCE를 사용한다.
- Access Token은 15분, Refresh Token은 30일이며 refresh 때마다 rotation한다.
- Desktop은 macOS Keychain, Extension은 각자 안전 저장 영역에 Refresh Token을 보관한다.
- Desktop과 Extension은 독립된 token family를 사용한다.
- 상세 계약은 Notion `인증 · 토큰 · 디바이스 정책`과 `FocusOn API 명세서`를 따른다.
