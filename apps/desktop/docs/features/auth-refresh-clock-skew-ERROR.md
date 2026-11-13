# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: AUTH-REFRESH-001
- 오류 식별명: refresh-clock-skew
- 기능명: 로그인 수명주기(토큰 갱신)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-12 16:32 UTC(서버 시각) / 16:49 UTC(앱 시각)
- 작성자: 허재원(Claude Code 보조)
- 심각도: 높음
- 상태: 해결
- 관련 Issue·PR: #229

## 오류 제목

서버 시계가 15분 넘게 뒤처지면 정상 refresh 응답을 `INVALID_RESPONSE`로 버리고, 다음 갱신에서 강제 로그아웃됨.

## 실행 환경

- macOS 26.6.1 / Mac16,1(arm64), Electron 44.1, Node 24.14.1·pnpm 11.19.0.
- Backend: 같은 Mac의 Docker Desktop 로컬 서버.
- 화면 기록·카메라 권한은 이 인증 오류와 무관하며 권한 변경 없음.

## 재현 방법

1. Desktop에 로그인한 채로 Mac을 잠자기에 넣었다 깨운다(앱 로그 `power.suspend`).
2. 깨어난 뒤 Docker Desktop VM 시계가 Mac보다 약 17분 뒤처진 상태에서 Desktop이 `POST /api/v1/auth/refresh`를 보낸다.
3. 서버가 `200`과 새 토큰을 돌려준다. 이때 `accessTokenExpiresAt`은 서버 시각 + 15분이라 Mac 시각으로는 이미 지난 시각이다.
4. 단위 테스트 재현: `tests/electron/auth-service.test.cjs`의 `skewedServer(-17분)` 응답으로 refresh·exchange·복구 흐름을 실행한다(수정 전 5개 모두 실패).

## 기대 결과

형식이 맞는 토큰 응답은 시계 차이와 상관없이 받아들이고, 새 refresh token을 Keychain에 저장한 뒤 로그인을 유지한다.

## 실제 결과

- 앱 로그가 같은 요청을 `status 200, code INVALID_RESPONSE`로 남겼다. 새 토큰은 저장되지 않았다.
- 서버는 이미 refresh token을 회전했기 때문에, 다음 갱신(앱 17:06 / 서버 16:33:01)이 예전 refresh token을 다시 보냈다.
- 서버가 `401 AUTH_TOKEN_REUSE_DETECTED`로 응답했고, session-runtime이 `trigger logout`으로 강제 로그아웃했다.

## 오류 메시지

```text
api.request.fail  path=/api/v1/auth/refresh status=200 code=INVALID_RESPONSE
api.request.fail  path=/api/v1/auth/refresh status=401 code=AUTH_TOKEN_REUSE_DETECTED
session-runtime   trigger logout
```

## 원인 및 해결

- 추정 원인: 잠자기 뒤 Docker Desktop VM 시계가 Mac 시계를 따라잡지 못해 약 17분 뒤처졌다. 근거는 같은 요청의 서버 로그 시각(16:32:56)과 앱 로그 시각(16:49:59) 차이뿐이고, VM 시계를 직접 확인하지는 않았다.
- 실제 원인: `electron/main/auth-api.cts`의 `parseTokenPair`가 서버 시계 기준인 `accessTokenExpiresAt`을 로컬 `Date.now()`와 바로 비교해, 지났으면 응답 전체를 `null`(`INVALID_RESPONSE`)로 버렸다. Access Token 수명이 15분이라 서버 시계가 15분 넘게 뒤처지면 막 발급된 토큰도 버려진다. 서버는 응답 시점에 이미 refresh token을 회전했으므로 버린 순간 로그인을 잃는다. Google code 교환(`parseExchange`)도 같은 파서를 써서 같은 문제가 있었다(교환은 한 번 쓰는 code라 로그인 실패로 나타남). 수정 전 단위 테스트 5개가 모두 실패해 확인했다.
- 해결 방법:
  - `AuthApiClient.call`이 응답 `Date` 헤더(서버 시계)를 파서에 넘긴다.
  - `parseTokenPair`는 `만료 시각 − 서버 Date`로 남은 수명을 구해 `로컬 now + 남은 수명`으로 바꿔 돌려준다. 서버 시계가 앞서거나 뒤처져도 같은 수명이 된다.
  - `Date` 헤더가 없거나 서버 기준으로도 남은 수명이 없으면 응답을 버리지 않고 60초짜리 로컬 만료로 저장한다(갱신 여유 30초보다 길게 둬 요청마다 갱신하지 않게 함).
  - 형식(필수 문자열·파싱 가능한 시각) 검사는 그대로 둔다.
- 변경 파일: `electron/main/auth-api.cts`, `tests/electron/auth-service.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (단위 테스트: 서버 −17분·+20분, exchange, `Date` 헤더 없음, 복구 후 재갱신 없음 5개 통과)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (실제 잠자기 재현은 아직 하지 않음)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron` 188개 통과, tsc·eslint 오류 없음)

## 재발 방지 및 남은 작업

- 서버가 준 시각을 로컬 시계와 바로 비교하지 않는다. 비교가 필요하면 응답 `Date` 헤더로 남은 시간을 구한다.
- 실제 Mac 잠자기 → Docker 시계 지연 상태에서 로그인이 유지되는지 수동 QA가 남았다.
- 서버 쪽 시계 동기화(Docker VM 시계)는 `server/` 범위가 아니라 이 작업에서 고치지 않았다.
