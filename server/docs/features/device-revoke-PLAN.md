# Backend 기능 기획서 — 기기 해제

## 기본 정보

- 기능명: 기기 해제 (`DELETE /api/v1/devices/{deviceId}`)
- 기능 ID: device-revoke
- 작성자: 박진욱
- 작성일: 2026-11-18
- 우선순위: MVP - 기본
- 관련 Issue: #274
- 예정 Branch: `feat/274-server-device-revoke`
- 관련 Client·AI 문서: Notion FocusOn API 명세서 `DELETE /api/v1/devices/{deviceId}`, `GET /api/v1/devices`

## 기능 목적

- 해결할 문제: Desktop은 사용자당 ACTIVE 3대까지만 로그인할 수 있다(`AUTH_DEVICE_LIMIT_EXCEEDED`). 지금은 기기 목록 조회만 있어 다른 기기를 정리할 방법이 없다.
- Backend가 담당할 역할: 본인 소유 기기를 폐기하고, 그 기기의 로그인(token family·Refresh Token)을 무효화한다. Desktop이면 연결된 Extension도 같은 transaction에서 함께 폐기한다.

## API 정의

- Endpoint: `/api/v1/devices/{deviceId}`
- HTTP Method: `DELETE`
- 인증 필요 여부: 필요 (`Authorization`)
- 요청 권한: 활성 Desktop만 호출. 본인 소유 ACTIVE 기기만 대상.

### Request

요청 바디 없음. path variable `deviceId`(`GET /devices`가 준 문자열 ID).

### Success Response

`204 No Content`, 본문 없음.

### Failure Response

| 상황 | HTTP | code |
| --- | --- | --- |
| Access Token 만료 | 401 | `AUTH_ACCESS_TOKEN_EXPIRED` |
| Bearer 없음·폐기, Extension이 호출(ID 형식과 무관) | 401 | `AUTH_RELOGIN_REQUIRED` |
| 없는 기기, 남의 기기, 이미 해제·로그아웃된 기기 | 404 | `DEVICE_NOT_FOUND` |

## 데이터 설계

- 관련 Entity: `UserDeviceEntity`, `TokenFamilyEntity`, `RefreshTokenRecordEntity`, `PairingCodeEntity`
- 변경 필드: `user_devices.status=REVOKED`, `revoked_at`; `token_families.status=REVOKED`, `revoked_reason=DEVICE_REVOKED`, `revoked_at`; 활성 Refresh Token `REVOKED`
- 마이그레이션: 없음. `DEVICE_REVOKED`는 V1·V11 CHECK에 이미 있다.
- 저장하지 않을 민감 데이터: 없음(새로 저장하는 값 없음)

## 처리 흐름

1. Controller가 ID를 해석하기 전에 Desktop 호출인지 확인한다.
2. `TokenLifecycleService.revokeDevice`가 사용자 행 → 호출자 family 순서로 잠근다(로그아웃·연결 해제와 같은 순서).
3. 호출자 family가 활성인지 확인한다.
4. 대상 기기가 본인 소유이고 ACTIVE인지 확인한다. 아니면 404.
5. 대상 family·Refresh Token을 폐기하고 기기를 REVOKED로 바꾼다.
6. 대상이 Desktop이면 발급 중인 연결 코드를 취소하고, 연결된 Extension도 같은 방식으로 폐기한다.

```text
DeviceController → TokenLifecycleService → UserRepository / TokenFamilyRepository / UserDeviceRepository / RefreshTokenRecordRepository / PairingCodeRepository
```

## 예외 처리

| 상황 | HTTP 상태 | 오류 코드 | 처리 방법 |
| --- | --- | --- | --- |
| Access Token 만료 | 401 | `AUTH_ACCESS_TOKEN_EXPIRED` | `POST /auth/refresh` 뒤 한 번 재시도 |
| 인증 실패·Extension 호출 | 401 | `AUTH_RELOGIN_REQUIRED` | 재로그인 |
| 입력값 오류(숫자가 아닌 ID) | 404 | `DEVICE_NOT_FOUND` | 기기 목록 재조회 |
| 데이터 없음·남의 기기·이미 해제 | 404 | `DEVICE_NOT_FOUND` | 기기 목록 재조회 |

## Notion에 없어 정한 값

- 이미 REVOKED·LOGGED_OUT인 기기는 404로 응답한다. `GET /devices`가 ACTIVE만 보여 주므로 목록에 없는 기기와 같게 취급한다.
- 호출한 Desktop 자기 자신도 해제할 수 있다. 결과는 로그아웃과 같고 상태만 `REVOKED`다.
- Extension 호출은 `GET /devices`와 같이 401 `AUTH_RELOGIN_REQUIRED`로 거절한다.
- 해제된 Desktop에서 진행 중이던 세션은 이 API에서 바로 끝내지 않는다. heartbeat가 끊겨 180초 뒤 `ABANDONED`로 정리된다.

## 연동 범위

- Desktop: 로그인된 Desktop의 기기 관리 화면에서 호출한다.
  - 다른 기기를 해제했으면 `GET /devices`를 다시 부른다.
  - 자기 자신을 해제했으면 Bearer가 더 이상 인증되지 않으므로 `GET /devices`를 부르지 않고, 저장된 token을 지운 뒤 바로 로그인 화면으로 보낸다.
  - 3대 한도(`AUTH_DEVICE_LIMIT_EXCEEDED`)에 걸린 새 설치는 아직 로그인 전이라 이 API를 부를 수 없다. "이미 로그인된 다른 Desktop에서 기기를 해제한 뒤 다시 로그인해 주세요"라고 안내한다.
- Chrome Extension: 직접 호출하지 않는다. 연결 Desktop이 해제되면 다음 요청부터 401이다.
- AI: 없음.
- 영향받는 API·Entity: 해제된 기기의 모든 인증 API(401), `POST /auth/refresh`.

## 완료 조건

- [x] API 계약을 공유했다(Notion 명세 기준).
- [x] 정상·실패 응답을 구현했다.
- [x] 데이터 저장·조회가 동작한다.
- [x] 테스트를 작성했다.
- [x] `CONTEXT.md`, `NEXT_TASK.md`를 갱신했다.
