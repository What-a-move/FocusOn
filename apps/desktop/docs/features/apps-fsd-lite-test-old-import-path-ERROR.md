# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: -
- 오류 식별명: apps-fsd-lite-test-old-import-path
- 기능명: apps 폴더 구조 가벼운 FSD 정리
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-09
- 작성자: jae1
- 심각도: 낮음
- 상태: 해결
- 관련 Issue·PR: #183

## 오류 제목

- `app/lib/auth-view.ts`를 `shared/lib/`로 옮긴 뒤 Electron 테스트 1개가 모듈을 찾지 못함

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple Silicon
- Electron 버전: ^44.1.0
- Node.js·pnpm: v24.14.1 · 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `app/lib/auth-view.ts`를 `shared/lib/auth-view.ts`로 옮긴다.
2. `app/`·`features/`·`shared/`의 import만 고친다.
3. `pnpm test:electron`을 실행한다.

## 기대 결과

- 161개 테스트가 모두 통과한다.

## 실제 결과

- 160개 통과, `tests/electron/auth-view.test.cjs` 1개 실패. 타입 검사·lint·빌드는 통과했다.

## 오류 메시지

```text
Error [ERR_MODULE_NOT_FOUND]: Cannot find module '.../apps/desktop/app/lib/auth-view.ts'
imported from .../apps/desktop/tests/electron/auth-view.test.cjs
```

## 원인 및 해결

- 추정 원인: 옮긴 파일을 쓰는 곳 중 import를 고치지 않은 곳이 남았다.
- 실제 원인: 이 테스트는 `.cjs`에서 `await import('../../app/lib/auth-view.ts')`로 TS 파일을 직접 불러온다. `tsconfig`(`tsc --noEmit`) 검사 대상이 아니고, import 수정 범위(`app`·`features`·`shared`)에도 `tests/`가 빠져 있어 놓쳤다.
- 해결 방법: 경로를 `../../shared/lib/auth-view.ts`로 고쳤다. 옛 경로를 저장소 전체(문서 제외)에서 다시 검색해 남은 곳이 없음을 확인했다.
- 변경 파일: `tests/electron/auth-view.test.cjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (`pnpm test:electron` 161개 통과)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 앱 재실행 후 정상 동작한다. (`pnpm build` 통과)
- [x] 관련 기능의 회귀가 없다.

## 재발 방지 및 남은 작업

- 파일을 옮길 때는 타입 검사만 믿지 말고 `tests/`·`scripts/`까지 옛 경로를 검색한다.
