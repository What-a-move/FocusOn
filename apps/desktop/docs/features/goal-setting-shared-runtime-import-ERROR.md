# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: goal-setting-001
- 오류 식별명: shared-runtime-import
- 기능명: 목표 설정 (D06·D07)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-24
- 작성자: jae1
- 심각도: 보통 (개발 서버에서 `/goal/` 화면이 열리지 않음)
- 상태: 해결
- 관련 Issue·PR: #44

## 오류 제목

- Renderer가 `electron/shared/goal-contract.cts`의 값을 import해 Next 빌드가 모듈을 찾지 못함

## 실행 환경

- macOS 버전: Darwin 25.6.0
- Mac 모델·칩: 개발용 Mac (Apple Silicon)
- Electron 버전: 44 (이 오류는 Electron 없이 `pnpm dev` 화면에서 발생)
- Node.js·pnpm: pnpm 11.19.0, Next.js 16.3.4 Turbopack
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `features/goal/goal-api.ts`에 `export { MAX_QUESTIONS } from '../../electron/shared/goal-contract.cjs'`를 둔다.
2. `apps/desktop`에서 `pnpm dev`를 실행한다.
3. `http://127.0.0.1:3000/goal/`을 연다.

## 기대 결과

- 목표 입력 화면이 열린다.

## 실제 결과

- Next 개발 서버 Build Error 화면이 뜨고 목표 화면이 열리지 않았다. `tsc --noEmit`과 lint는 통과했다.

## 오류 메시지

```text
Module not found: Can't resolve '../../electron/shared/goal-contract.cjs'
./apps/desktop/features/goal/goal-api.ts (6:1)
```

## 원인 및 해결

- 추정 원인: 없음(오류 메시지와 import 경로로 바로 확인).
- 실제 원인: `electron/shared/*.cts`는 Electron용 TypeScript Build(`tsconfig.electron.json`)로만 `.cjs`가 만들어진다. TypeScript는 `.cjs` import를 원본 `.cts`로 해석해 타입 검사를 통과시키지만, Next(Turbopack)는 Renderer 번들에서 실제 `.cjs` 파일을 찾으므로 값 import는 실패한다. 기존 Renderer 코드는 `import type`만 써서 문제가 없었다.
- 해결 방법: Renderer는 공유 파일에서 타입만 가져오고, 화면에 필요한 상수 `MAX_QUESTIONS = 2`는 `goal-api.ts`에 같은 값으로 둔다. Main 쪽 검증은 공유 파일의 값을 그대로 쓴다.
- 변경 파일: `apps/desktop/features/goal/goal-api.ts`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다.
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`, `pnpm build`)

## 재발 방지 및 남은 작업

- Renderer(`app/`, `features/`)에서 `electron/shared`를 가져올 때는 `import type`만 쓴다. 값이 필요하면 Renderer 쪽에 두고 같은 값이라고 주석으로 남긴다.
- 화면을 추가하면 `tsc`만 믿지 말고 `pnpm build` 또는 개발 서버로 실제 번들을 확인한다.
