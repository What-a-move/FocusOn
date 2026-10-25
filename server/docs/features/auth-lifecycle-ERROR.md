# 로그인 수명주기 개발·QA 오류

## 1. Desktop native observer 빌드 환경 불일치

- 날짜: 2026-10-26 / Issue #59 / 심각도: 보통 / 상태: 환경 작업으로 보류
- 재현: 기존 lockfile 의존성 설치 후 apps/desktop에서 pnpm build:electron.
- 기대: TypeScript·preload·Swift observer 빌드 성공.
- 실제: TypeScript·preload는 성공했지만 xcrun swiftc에서 SwiftBridging module 중복 및 SDK/compiler 버전 불일치로 실패했다.
- 원인: 로컬 CommandLineTools SDK는 swiftlang-6.0.3.1.5, compiler는 swiftlang-6.0.3.1.10을 보고했다. 전체 Xcode는 확인되지 않았다.
- 대응: 시스템 SDK·Frontend 코드를 임의로 수정하지 않았다. 이미 컴파일한 인증 모듈로 22개 인증 테스트와 실제 HTTP·macOS Keychain QA를 별도로 수행했다.
- 남은 작업: 맞는 Apple toolchain 환경에서 전체 Desktop native/UI 빌드를 검증한다. Backend 및 인증 단위 테스트 성공과 구분한다.

## 2. 만료 JWT 테스트 fixture의 클레임 불일치

- 심각도: 낮음 / 상태: 해결
- 합성 만료 token이 tokenType 대신 type을 사용하고 device/family ID를 숫자로 넣어 AUTH_ACCESS_TOKEN_EXPIRED 대신 일반 재로그인 실패가 발생했다.
- AccessJwtService 실제 계약의 tokenType 및 문자열 ID로 fixture를 수정했다. 제품 검증을 완화하지 않았다.
- 수정 후 전체 55개 테스트 및 PostgreSQL 25개 테스트를 통과했다.
