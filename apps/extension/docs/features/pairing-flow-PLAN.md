# Extension 일회용 연결 코드 기획서

## 기본 정보

- 기능명: Desktop 온보딩 Extension 연결
- 기능 ID: extension-pairing
- 담당 영역: Frontend (`apps/extension`)
- 대상 화면: Chrome Extension Popup
- 관련 Issue: [#36 Frontend 온보딩](https://github.com/What-a-move/FocusOn/issues/36)
- 관련 API: `POST /api/v1/pairing-codes`, `POST /api/v1/extension-pairings`

## 목표와 범위

Desktop이 발급한 일회용 코드를 Extension Popup에 입력해 같은 계정의 기기로 연결한다. Popup은 코드 입력, 형식 검증, 연결 대기·오류·완료 상태를 보여준다.

코드 제출(`POST /api/v1/extension-pairings`)과 token 저장을 구현했다(2026-10-22). API 성공 응답과 token 저장이 모두 끝난 뒤에만 연결 완료를 표시한다. 이미 연결된 브라우저는 Popup을 열면 연결 완료 화면을 보여준다.

## 계약

- Desktop은 인증된 사용자의 `POST /api/v1/pairing-codes` 호출로 코드를 발급한다. Request body는 없다.
- 코드는 대문자·숫자 6자리이며 Desktop 화면에는 `ABC-123`처럼 표시한다. 유효 시간은 5분, 최대 실패 횟수는 3회다.
- Extension은 `POST /api/v1/extension-pairings`에 `pairingCode` (`ABC123`처럼 하이픈 제외)와 `installationId`를 보낸다. 이 endpoint는 인증 헤더를 요구하지 않는다.
- 성공하면 Extension 전용 Access Token과 Refresh Token을 받는다. Desktop token과 공유하지 않는다.
- 오류 코드는 `LINK_001_INVALID_FORMAT`, `LINK_006_CODE_MISMATCH`, `LINK_002_EXPIRED`, `LINK_003_ALREADY_USED`, `LINK_004_ATTEMPTS_EXCEEDED`, `LINK_005_CANCELLED`다. HTTP 429는 재시도 대기 안내를 제공한다.
- Token 저장 위치는 `DECISION_RECORD.md` 결정 011을 따른다: Refresh Token은 `chrome.storage.local`, Access Token은 `chrome.storage.session`(TRUSTED_CONTEXTS), installationId는 `chrome.storage.local`.
- 성공 응답(201) `data`: `accessToken`, `refreshToken`, `accessTokenExpiresAt`(UTC ISO 8601), `device: { deviceId, deviceType: "EXTENSION" }`. `deviceId`는 연결 해제에 쓰도록 `chrome.storage.local`에 둔다. (2026-10-22 확정, Notion 반영)
- 틀린 코드(1~2번째 실패): `400 LINK_006_CODE_MISMATCH` + `details.remainingAttempts`. 3번째 실패는 `410 LINK_004_ATTEMPTS_EXCEEDED`. (2026-10-22 확정)
- Popup은 Next.js 정적 Export다. Manifest V3 CSP 때문에 `pnpm build`가 인라인 스크립트를 외부 파일로 옮긴다(`pairing-flow-inline-script-csp-ERROR.md`).

## 사용자 흐름

1. Desktop 온보딩에서 발급된 코드를 확인한다.
2. Extension Popup에 코드를 입력한다. 입력 중 대문자로 정리하고 `ABC-123` 형식을 안내한다.
3. 서버 연결이 준비되면 하이픈을 제거해 API에 제출한다.
4. 성공 응답 뒤에만 연결 완료를 표시한다. 실패하면 계약 오류에 맞는 안내와 재시도 방법을 보여준다.

## 화면 상태

- 입력 전 / 형식 오류
- API 준비 전 안내
- 연결 요청 중
- 연결 성공
- 만료·사용됨·시도 초과·취소·네트워크 오류

## 완료 조건

- [x] 입력은 대문자·숫자 6자리이며 화면에는 `ABC-123` 형식으로 표시한다.
- [x] 잘못된 형식은 제출하지 않는다.
- [x] API 성공 응답 전 연결 완료 상태를 표시하지 않는다.
- [x] 오류 코드를 사용자 문구로 바꾸고 재시도 조건을 안내한다. (Chromium + 테스트용 가짜 서버로 만료·429·틀린 코드 확인)
- [x] Extension token 저장 정책을 확정한 뒤에만 실제 API 연결을 완료한다. (결정 011)
- [ ] Desktop에서 코드 발급부터 연결 완료 상태 반영까지 Backend 준비 후 검증한다.
