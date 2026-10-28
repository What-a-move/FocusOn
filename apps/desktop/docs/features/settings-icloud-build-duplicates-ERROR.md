# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: settings-01
- 오류 식별명: icloud-build-duplicates
- 기능명: 설정 화면
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-27
- 작성자: jae1
- 심각도: 보통 (빌드가 멈추지만 제품 코드와 무관)
- 상태: 해결 (2026-10-28 저장소를 iCloud 밖으로 옮겨 근본 해결)
- 관련 Issue·PR: #80

## 오류 제목

- `pnpm build`의 `tsc --noEmit`이 `.next/types/routes.d 2.ts` 같은 중복 파일 때문에 실패한다.

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: v24.14.1 · 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음
- 저장소 위치: `~/Desktop` (iCloud Drive "데스크탑 및 문서" 동기화 켜짐)

## 재현 방법

1. 저장소를 iCloud가 동기화하는 `~/Desktop` 아래에 둔다.
2. `pnpm dev:desktop`이 켜진 상태에서 `pnpm build`처럼 `.next`·`out`을 빠르게 다시 쓰는 작업을 이어서 한다.
3. 다시 `pnpm build`를 실행한다.

## 기대 결과

- 타입 검사가 통과하고 정적 화면 빌드까지 끝난다.

## 실제 결과

- `.next/types`에 `cache-life.d 2.ts`, `routes.d 2.ts`, `validator 2.ts`, `package 2.json`, `app 3/`이 생겨 같은 타입이 두 번 선언되고 `tsc`가 멈춘다. `out/`에도 `session 2/`, `_next 2/` 등 64개 사본이 있었다.

## 오류 메시지

```text
.next/types/cache-life.d 2.ts(3,1): error TS6200: Definitions of the following identifiers conflict with those in another file: unstable_cache, ...
.next/types/routes.d 2.ts(61,8): error TS2300: Duplicate identifier 'LayoutProps'.
```

## 원인 및 해결

- 추정 원인: 이번 작업의 새 라우트(`/settings`, `/settings/exclusions`)가 Next.js 타입 생성과 충돌했다.
- 실제 원인: 라우트와는 관계없었다. 사본의 생성 시각(09:04)이 이번 작업 시작 전이었고, 원본(09:23)과 내용 크기가 같았다. `~/Desktop`이 iCloud 파일 공급자(`com.apple.file-provider-domain-id`)로 동기화되고 있어서, 빌드가 파일을 빠르게 지우고 다시 쓰는 동안 iCloud가 충돌 사본(`이름 2`)을 남긴 것이다. 같은 이유로 `.git/index 2`, `apps/extension/.next/.../index.pack 2.pack`도 있었다. `tsconfig.json`이 `.next/types/**/*.ts`를 포함하기 때문에 사본까지 타입 검사에 들어갔다.
- 해결 방법: git이 무시하는 빌드 결과물(`apps/desktop/.next`, `apps/desktop/out`)에서 이름 끝에 " 2"·" 3"이 붙은 사본만 지우고 다시 빌드했다. `.git` 안의 사본은 git이 읽지 않아서 건드리지 않았다.
- 변경 파일: 없음 (빌드 결과물만 정리)

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (사본 삭제 뒤 `pnpm build` 통과, 15개 정적 라우트 생성)
- [x] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (iCloud 동기화가 켜져 있는 한 다시 생길 수 있다)
- [x] 관련 기능의 회귀가 없다. (Electron 테스트 139개 통과)

## 재발 방지 및 남은 작업

- 같은 오류가 다시 나면 아래 명령으로 빌드 결과물의 사본만 지운다.
  `find apps/desktop/.next apps/desktop/out -depth \( -name "* [0-9]" -o -name "* [0-9].*" \) -exec rm -rf {} +`
- 근본 해결은 저장소를 iCloud가 동기화하지 않는 폴더(예: `~/Developer`)로 옮기는 것이다. 사용자 환경 결정이라 이번 작업에서는 옮기지 않았다.

## 2차 발생 (2026-10-28): iCloud가 파일을 클라우드로만 남겨 빌드가 멈춤

- 증상: 커밋 전 `pnpm test:electron`의 `tsc -p tsconfig.electron.json`이 CPU 0%로 5분 넘게 멈췄다.
- 추정 원인: 타입 검사가 무한 대기에 빠졌다.
- 실제 원인: macOS "Mac 저장 공간 최적화"가 저장소 파일 8,520개를 클라우드로만 남기고 로컬 내용을 지웠다(`ls -lO`에 `dataless` 표시). `node_modules/typescript/lib/*.d.ts`, git 내부 파일 649개, 소스·문서 554개가 포함됐다. `tsc`가 이 파일을 읽으려 하자 iCloud 다운로드를 기다렸고, iCloud가 다른 프로젝트 파일까지 함께 받고 있어 파일 하나에 약 10초가 걸렸다. `brctl download .`로 저장소 전체를 요청한 것은 대기열만 늘려 도움이 되지 않았다.
- 해결: 이번 변경 파일 30개가 로컬에 온전한지 확인한 뒤, GitHub에서 iCloud 밖 `~/Developer/FocusOn`으로 새로 clone하고 변경 파일만 복사했다. 의존성은 `pnpm install --offline --frozen-lockfile`로 로컬 pnpm 저장소에서 설치했다(2.5초). 기존 `~/Desktop/What a move/FocusOn` 폴더는 지우지 않았다.
- 확인: 새 위치에서 `pnpm build` 통과, `pnpm lint` 오류 0개. `pnpm test:electron`은 첫 실행에서 65개 중 7개 실패로 끝났으나 원인을 남기지 못했고, 같은 명령과 `dist-electron`을 지운 뒤 실행까지 세 번 다시 돌려 모두 139개 통과했다. 첫 실패는 재현되지 않아 원인 미확정으로 둔다.
- 재발 방지: 저장소는 iCloud가 동기화하지 않는 `~/Developer/FocusOn`에서 작업한다.
