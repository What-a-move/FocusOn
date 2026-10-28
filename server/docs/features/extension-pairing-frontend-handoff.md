# Extension 연결 — Frontend 전달 사항

Backend Issue #58의 사용자 승인 계약이다. Frontend 코드는 이 작업에서 수정하지 않았다.

## Desktop

1. POST /api/v1/pairing-codes 응답의 pairingCodeId를 codeDisplay·expiresAt과 함께 보존한다. ID는 문자열이다.
2. 연결 화면 닫기·정상 앱 종료 때 DELETE /api/v1/pairing-codes/{pairingCodeId}를 Bearer로 호출한다. 성공204·본문 없음. 이미 소비/취소/만료/없는 ID도204이며 연결 성공을 취소하지 않는다.
3. 강제 종료·충돌·전원 차단·통신 미전달은 즉시 취소를 보장할 수 없고 발급 후5분 만료로 처리한다.
4. 기존 readiness3초 polling은 #38 담당이다. 이 작업은 readiness API를 구현하지 않는다.

## Extension

- POST /api/v1/extension-pairings 요청은 기존 {pairingCode,installationId}, Bearer 불필요. 응답201 data.accessToken/refreshToken/accessTokenExpiresAt/device{deviceId,deviceType:EXTENSION}.
- 1~2회 오입력:400 LINK_006_CODE_MISMATCH, retryable=false, details.remainingAttempts=2/1. 사용자 수정 후 재입력 가능.
- 3회:410 LINK_004_ATTEMPTS_EXCEEDED, remainingAttempts=0, retryAfterSeconds 및 Retry-After. **코드 재발급 안내 대신 설치별3분 입력 잠금**을 표시한다. 유효 코드로도 우회할 수 없다.
- 자동 재시도하지 않는다. 대기 뒤 사용자가 재입력한다. 성공 또는 잠금 만료 시 실패 횟수 초기화. 1~2회 실패는 마지막 실패 후24시간이 지나면 초기화된다.
- 같은 계정 재연결은 deviceId 재사용·이전 token 폐기. 새 token을 안전하게 교체한다.
- 다른 계정 활성 설치:409 LINK_007_INSTALLATION_ALREADY_LINKED. 기존 연결을 먼저 해제하도록 안내한다. 소유자 정보는 응답하지 않는다.
- 기존 LINK_002/003/005는 새 코드 발급 안내를 유지한다.

## 남은 연동

이 브랜치는 Backend에서 위 흐름을 구현·QA했으며 dev에는 아직 없다. Frontend 반영 후 실제 Desktop→Extension 통합 QA가 필요하다. DELETE extension-pairings(연결 해제) API 자체와 기기 관리는 이번 #58 밖의 후속 작업이다.

## 코드 대조 기록 (2026-10-28)

- 기준: Backend 브랜치 `feat/#58-extension-pairing`, Frontend는 `origin/dev`(4ee5493 이후 동일 코드 확인). 최신 dev 6c93515의 협업자 V4를 통합하고 연결 migration을 V5로 조정했다.
- Desktop `parsePairingCode`: `codeDisplay` 형식 ABC-123, 파싱 가능한 `expiresAt`만 읽으며 `pairingCodeId`를 버린다. 코드는 표시할 수 있지만 정상 화면 닫기 시 취소 API에 필요한 ID 보존·호출은 Frontend 작업이다.
- Extension `submitPairing`: 하이픈 제거된 대문자 코드와 `installationId`를 공개 POST로 전송한다. `data.accessToken`, `refreshToken`, `accessTokenExpiresAt`, `device.deviceId` 및 201을 읽는다. Backend가 이 필드를 모두 제공하는지 백엔드 HTTP 통합 테스트로 확인했다.
- Extension `LINK_006`은 숫자 remainingAttempts를 읽는다. `LINK_004`는 현재 새 코드 발급을 안내하지만, Backend의 설치별 3분 잠금과 다르다. `Retry-After`를 읽는 구조는 있으나 해당 오류 화면에 대기 시간을 표시하지 않는다.
- Extension에 `LINK_007_INSTALLATION_ALREADY_LINKED` 전용 안내가 없다. 연결 해제 API가 미구현이어서 다른 계정 활성 설치를 해제하는 전 과정은 동작하지 않는다.
- Desktop은 readiness GET/PUT과 `steps.extensionLinked`에 의존한다. Backend는 이 endpoint를 아직 제공하지 않아 프론트 전체 온보딩 흐름은 미완성이다. 이 API는 별도의 저장 모델·완료 정책이 필요하다.
- 실제 Desktop·Chrome Extension 실행 왕복은 이번 검사에서 수행하지 않았다. Backend 변경만으로 프론트 오류 화면이나 종료 시 취소 호출이 바뀌지는 않는다.

## LINK_007 복구 경로 검토 (2026-10-28)

현재 #58 브랜치에는 연결 해제 API가 아직 없으므로 LINK_007 뒤 Extension 팝업에서 해제할 수 없다. 다른 계정이 설치 ID나 연결 코드만으로 기존 소유자의 연결을 해제하는 방식은 허용하지 않는다.

- 현재 코드에서 기존 계정으로 인증된 **연결 원본 Desktop**이 로그아웃하면 그 Desktop에 직접 연결된 Extension을 REVOKED로 변경하고 토큰을 폐기한다. 이후 새 계정으로 연결할 수 있다. 이는 API 수준 동작이며 현재 Desktop 화면에서 사용 가능한지와 실제 앱 왕복은 별도 확인이 필요하다.
- 제안: 기존 Extension의 유효한 토큰과 deviceId가 있으면 팝업에서 본인의 연결을 해제한다. Desktop도 본인 소유 기기만 해제할 수 있도록 기기 조회·해제 흐름이 필요하다. 두 경로의 상세 권한·동시성·오류는 후속 Issue와 정본 검토 후 구현한다.
- Extension 토큰이 없으면 기존 Google 계정으로 Desktop에 다시 인증하여 본인 기기를 조회·해제하는 복구 경로가 필요하다. 기기 조회·해제 API와 화면이 현재 구현됐다고 가정하면 안 된다. 기존 계정 인증도 불가능하면 임의 해제를 허용하지 않으며 소유권 확인을 위한 별도 복구 정책이 필요하다.
- Backend는 아직 dev에 병합되지 않았다. 실제 Desktop·Extension 연동 QA는 dev 반영과 Frontend의 취소·오류·해제 화면 구현 후 별도로 진행한다.

### 추가 구현 중인 Desktop 복구 API

기존 계정의 Desktop access token으로 `GET /api/v1/devices`를 호출하면 `data.devices` 배열에 본인 ACTIVE 기기의 `deviceId`, `type`(DESKTOP/EXTENSION), `name`, `lastUsedAt`, `linkedDesktopDeviceId`를 제공한다. Extension을 선택해 `DELETE /api/v1/extension-pairings/{deviceId}`로 해제하는 흐름이다. 배열 래퍼 `data.devices`는 Notion에 명시되지 않아 Frontend 확인이 필요하다. 이 문단은 구현 브랜치 기준이며 dev 배포 완료를 의미하지 않는다.
