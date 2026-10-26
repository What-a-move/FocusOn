# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

> 이 문서는 2026-10-26 기록 점검(Issue #65)에서 작성했다. 오류는 같은 날 Issue #61 작업 중 실제로 발생했다.

## 오류 기본 정보

- 오류 번호: tab-analysis-002
- 오류 식별명: node-test-parameter-property
- 기능명: Chrome 탭·페이지 분석 (화면 분석 3단계)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-26
- 작성자: jae1
- 심각도: 낮음 (테스트 실행 실패)
- 상태: 해결
- 관련 Issue·PR: #61 / PR #62

## 오류 제목

- `node --test`가 클래스 생성자 매개변수 속성 문법을 실행하지 못함

## 실행 환경

- macOS 버전: 26.6.1
- Chrome 버전: 해당 없음 (빌드·테스트 단계)
- Extension 버전: 0.1.0
- Desktop 연결 상태: 미사용
- 권한 상태: 해당 없음

## 재현 방법

1. `TabAnalyzer`를 `constructor(private readonly deps: Deps) {}`로 작성한다.
2. `pnpm --filter @focuson/extension test`

## 기대 결과

- 테스트가 실행된다.

## 실제 결과

- 파일을 불러오지 못해 테스트 1개가 실패했다.

## 오류 메시지

```text
SyntaxError [ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX]: TypeScript parameter property is not supported in strip-only mode
```

## 원인 및 해결

- 추정 원인: Node 버전 문제.
- 실제 원인: Extension 테스트는 Node 24의 TypeScript 타입 지우기(strip-only)로 바로 실행한다. 이 방식은 JavaScript로 바꿔야 하는 문법(매개변수 속성, enum 등)을 지원하지 않는다.
- 해결 방법: 필드를 선언하고 생성자에서 `this.deps = deps`로 넣도록 바꿨다.
- 변경 파일: `extension/background/tab-analyzer.ts`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [x] 관련 기능의 회귀가 없다. (`pnpm --filter @focuson/extension test`, `pnpm build:extension`)

## 재발 방지 및 남은 작업

- node --test로 실행하는 파일에는 매개변수 속성·enum·namespace를 쓰지 않는다. 파일 머리 주석에 남겨 두었다.
