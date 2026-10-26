# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

> 이 문서는 2026-10-26 기록 점검(Issue #65)에서 작성했다. 오류는 같은 날 Issue #61 작업 중 실제로 발생했다.

## 오류 기본 정보

- 오류 번호: tab-analysis-001
- 오류 식별명: typescript6-types-default
- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-26
- 작성자: jae1
- 심각도: 보통 (타입 검사 실패)
- 상태: 해결
- 관련 Issue·PR: #61 / PR #62

## 오류 제목

- `@types/chrome`을 설치했는데 `chrome`을 찾지 못해 타입 검사가 실패함

## 실행 환경

- macOS 버전: 26.6.1
- Chrome 버전: 해당 없음 (빌드·테스트 단계)
- Extension 버전: 0.1.0
- Desktop 연결 상태: 미사용
- 권한 상태: 해당 없음

## 재현 방법

1. `pnpm --filter @focuson/extension add -D @types/chrome`
2. 직접 만든 `types/chrome.d.ts`를 지운다.
3. `tsc --noEmit -p .`

## 기대 결과

- Service Worker 코드의 `chrome.*` 타입을 찾는다.

## 실제 결과

- `chrome` 이름을 모두 찾지 못했다.

## 오류 메시지

```text
extension/background/index.ts(22,25): error TS2304: Cannot find name 'chrome'.
extension/background/index.ts(89,47): error TS7006: Parameter 'details' implicitly has an 'any' type.
```

## 원인 및 해결

- 추정 원인: 패키지가 설치되지 않았다.
- 실제 원인: 패키지는 설치돼 있었다. 이 저장소의 TypeScript 6은 `compilerOptions.types`의 기본값이 빈 배열이라, `node_modules/@types`를 자동으로 포함하지 않는다.
- 해결 방법: `tsconfig.json`에 `"types": ["chrome", "node"]`를 명시했다.
- 변경 파일: `tsconfig.json`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [x] 관련 기능의 회귀가 없다. (`pnpm --filter @focuson/extension test`, `pnpm build:extension`)

## 재발 방지 및 남은 작업

- 새 `@types/*` 패키지를 쓸 때는 `tsconfig.json`의 `types`에 함께 추가한다.
