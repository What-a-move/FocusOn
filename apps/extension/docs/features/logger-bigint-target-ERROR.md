# Extension 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: logger-01
- 오류 식별명: bigint-target
- 기능명: 모듈 연동 로거
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-29
- 작성자: jae1
- 심각도: 낮음 (빌드만 멈춤, 제품 코드 영향 없음)
- 상태: 해결
- 관련 Issue·PR: #105

## 오류 제목

- `pnpm build`의 `tsc --noEmit`이 로거 테스트의 BigInt 표기(`10n`)를 막는다.

## 실행 환경

- macOS 버전: 26.6.1
- Chrome 버전: 해당 없음 (빌드 단계)
- Extension 버전: 0.1.0
- Desktop 연결 상태: 미사용
- 권한 상태: 해당 없음

## 재현 방법

1. `tests/logger.test.ts`에 직렬화 실패 확인용 값으로 `{ durMs: 10n }`을 쓴다.
2. `pnpm test`를 실행한다 (통과).
3. `pnpm build`를 실행한다.

## 기대 결과

- 테스트와 빌드가 모두 통과한다.

## 실제 결과

- `node --test`는 통과했지만 빌드의 타입 검사가 실패했다.

## 오류 메시지

```text
tests/logger.test.ts(52,64): error TS2737: BigInt literals are not available when targeting lower than ES2020.
```

## 원인 및 해결

- 추정 원인: Node 버전이 낮아 BigInt를 지원하지 않는다.
- 실제 원인: Node는 BigInt를 지원해 테스트는 통과했다. 빌드의 `tsc --noEmit`은 `tests/`까지 검사하는데 Extension `tsconfig.json`의 `target`이 `ES2017`이라 `10n` 같은 BigInt 표기를 허용하지 않는다. Desktop은 Electron 테스트가 `.cjs`라 타입 검사를 거치지 않아 같은 표기가 문제없었다.
- 해결 방법: 표기 대신 `BigInt(10)`을 쓴다. 제품 코드의 `target`은 바꾸지 않는다.
- 변경 파일: `tests/logger.test.ts`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (`pnpm build` 통과)
- [x] 새 탭·기존 탭에서 확인했다. (해당 없음)
- [x] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 제외 도메인을 확인했다. (해당 없음)
- [x] Desktop 연결 실패를 확인했다. (해당 없음)
- [x] 관련 기능의 회귀가 없다. (`pnpm test` 29개 통과)

## 재발 방지 및 남은 작업

- Extension 테스트(`tests/*.test.ts`)는 `node --test`와 빌드 타입 검사를 둘 다 거친다. ES2017 밖 문법은 테스트에서도 쓰지 않는다.
