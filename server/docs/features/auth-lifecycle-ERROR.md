# 로그인 수명주기 개발·QA 오류

## 1. Desktop native observer 빌드 환경 불일치

- 날짜: 2026-10-26 / Issue #59 / 심각도: 보통 / 상태: 환경 작업으로 보류
- 재현: 기존 lockfile 의존성 설치 후 apps/desktop에서 pnpm build:electron.
- 기대: TypeScript·preload·Swift observer 빌드 성공.
- 실제: TypeScript·preload는 성공했지만 xcrun swiftc에서 SwiftBridging module 중복 및 SDK/compiler 버전 불일치로 실패했다.
- 최초 진단: SDK는 swiftlang-6.0.3.1.5, compiler는 swiftlang-6.0.3.1.10을 보고했다. 후속 검증에서 실제 선행 원인은 오래된 swift/module.modulemap(2023)과 bridging.modulemap(2024)의 SwiftBridging 중복 정의로 좁혔다. 버전 차이 메시지만으로 SDK 자체가 호환되지 않는다고 확정하지 않는다.
- 대응: 시스템 SDK·Frontend 코드를 임의로 수정하지 않았다. 이미 컴파일한 인증 모듈로 22개 인증 테스트와 실제 HTTP·macOS Keychain QA를 별도로 수행했다.
- 후속 검증: 시스템 파일 변경 없이 옛 중복 modulemap만 가리는 임시 VFS와 캐시를 사용하면 같은 Swift·SDK로 native와 전체 Desktop build가 성공한다. 기본 빌드 스크립트에 이 임시 처리를 넣지 않았다. 정식 설치 복구는 관리자 인증이 필요하다. macOS 전체 업데이트/재시작은 실행하지 않았다. 실제 UI는 Mac 잠금으로 미검증이다.

## 2. 만료 JWT 테스트 fixture의 클레임 불일치

- 심각도: 낮음 / 상태: 해결
- 합성 만료 token이 tokenType 대신 type을 사용하고 device/family ID를 숫자로 넣어 AUTH_ACCESS_TOKEN_EXPIRED 대신 일반 재로그인 실패가 발생했다.
- AccessJwtService 실제 계약의 tokenType 및 문자열 ID로 fixture를 수정했다. 제품 검증을 완화하지 않았다.
- 수정 후 전체 55개 테스트 및 PostgreSQL 25개 테스트를 통과했다.

## 3. Desktop logout 만료 401의 잘못된 성공 처리

- 심각도: 보통(P2) / 상태: 해결 / Issue #59, PR #60.
- 조건: 전송 중 Access JWT가 만료되거나 기기 시계가 느려 서버가 AUTH_ACCESS_TOKEN_EXPIRED를 반환한다.
- 실제: AuthApiClient가 모든 401을 성공으로 바꿔 Keychain은 지워지지만 서버 family·연결 Extension·기기 슬롯은 남았다.
- 해결: 401 코드를 전달하고 AuthService가 single-flight refresh 뒤 logout을 한 번 재시도한다. 서버 오류·미확인 401·재시도 만료는 token을 보존한다.
- 확인: 독립 재검토·Desktop 회귀 7개 및 실제 HTTP/PG/Keychain 만료 경쟁 성공. Frontend 상세 오류는 apps/desktop/docs/features/auth-lifecycle-logout-expiry-ERROR.md에 기록했다.

## 4. CodeRabbit 재리뷰의 선갱신·본문 바인딩 전 한도 누락

- 상태: 수정·검증 완료 / Minor2건, API 형식 변경 없음.
- 선갱신: 유효 token으로 먼저 logout하도록 수정했다. 잔여20초·refresh 장애 조건20회에서 성공0→20회, 불필요한 refresh20→0회.
- 요청 제한: 잘못된 JSON/길이 초과가 Controller 전에 거절돼 한도에 집계되지 않았다. Filter에서 먼저 검사하고 Controller 집계를 제거했다. 각 입력301번째가400→429로 바뀌었고 정상 입력의300회 허용도 유지했다.
- 재현·검증: RefreshRateLimitApiTests3개와 Desktop20회 비교, 전체 Backend58·Desktop96·PG28·실제 HTTP33건 통과. 개인정보 없는 수치 원자료는 개인 트러블슈팅 폴더에 별도로 보관한다.
