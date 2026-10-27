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
- 자동 재시도하지 않는다. 대기 뒤 사용자가 재입력한다. 성공 또는 잠금 만료 시 실패 횟수 초기화.
- 같은 계정 재연결은 deviceId 재사용·이전 token 폐기. 새 token을 안전하게 교체한다.
- 다른 계정 활성 설치:409 LINK_007_INSTALLATION_ALREADY_LINKED. 기존 연결을 먼저 해제하도록 안내한다. 소유자 정보는 응답하지 않는다.
- 기존 LINK_002/003/005는 새 코드 발급 안내를 유지한다.

## 남은 연동

이 브랜치는 Backend에서 위 흐름을 구현·QA했으며 dev에는 아직 없다. Frontend 반영 후 실제 Desktop→Extension 통합 QA가 필요하다. DELETE extension-pairings(연결 해제) API 자체와 기기 관리는 이번 #58 밖의 후속 작업이다.
