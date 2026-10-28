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

- 기준: Backend 브랜치 `feat/#58-extension-pairing`, Frontend는 `origin/dev`(4ee5493 이후 동일 코드 확인). 최신 dev 6c93515은 협업자 V4가 추가돼 아직 이 브랜치에 통합하지 않았다.
- Desktop `parsePairingCode`: `codeDisplay` 형식 ABC-123, 파싱 가능한 `expiresAt`만 읽으며 `pairingCodeId`를 버린다. 코드는 표시할 수 있지만 정상 화면 닫기 시 취소 API에 필요한 ID 보존·호출은 Frontend 작업이다.
- Extension `submitPairing`: 하이픈 제거된 대문자 코드와 `installationId`를 공개 POST로 전송한다. `data.accessToken`, `refreshToken`, `accessTokenExpiresAt`, `device.deviceId` 및 201을 읽는다. Backend가 이 필드를 모두 제공하는지 백엔드 HTTP 통합 테스트로 확인했다.
- Extension `LINK_006`은 숫자 remainingAttempts를 읽는다. `LINK_004`는 현재 새 코드 발급을 안내하지만, Backend의 설치별 3분 잠금과 다르다. `Retry-After`를 읽는 구조는 있으나 해당 오류 화면에 대기 시간을 표시하지 않는다.
- Extension에 `LINK_007_INSTALLATION_ALREADY_LINKED` 전용 안내가 없다. 연결 해제 API가 미구현이어서 다른 계정 활성 설치를 해제하는 전 과정은 동작하지 않는다.
- Desktop은 readiness GET/PUT과 `steps.extensionLinked`에 의존한다. Backend는 이 endpoint를 아직 제공하지 않아 프론트 전체 온보딩 흐름은 미완성이다. 이 API는 별도의 저장 모델·완료 정책이 필요하다.
- 실제 Desktop·Chrome Extension 실행 왕복은 이번 검사에서 수행하지 않았다. Backend 변경만으로 프론트 오류 화면이나 종료 시 취소 호출이 바뀌지는 않는다.
