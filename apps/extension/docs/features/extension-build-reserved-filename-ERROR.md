# Extension 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: build-01
- 오류 식별명: reserved-filename
- 기능명: Extension 빌드
- 담당 영역: Frontend(`apps`)
- 대상 앱: Chrome Extension
- 발생 일시: 2026-10-30
- 작성자: jae1
- 심각도: 높음 (Extension을 Chrome에 불러올 수 없음)
- 상태: 해결
- 관련 Issue·PR: #107

## 오류 제목

- `out/`을 압축해제된 확장 프로그램으로 불러오면 `_`로 시작하는 이름 때문에 Chrome이 로딩을 거부한다.

## 실행 환경

- macOS 버전: 26.6.1
- Chrome 버전: 사용자 정식 Chrome (Chrome for Testing 153에서는 재현되지 않음)
- Extension 버전: 0.1.0
- Desktop 연결 상태: 연결 전
- 권한 상태: 해당 없음

## 재현 방법

1. `apps/extension`에서 `pnpm build`를 실행한다.
2. Chrome `chrome://extensions` → 개발자 모드 → 압축해제된 확장 프로그램을 로드합니다 → `out/` 선택.

## 기대 결과

- Extension이 로딩되고 팝업이 열린다.

## 실제 결과

- 매니페스트를 로드할 수 없다는 오류와 함께 로딩되지 않는다. 문제 파일을 지우면 다음 `_` 이름에서 같은 오류가 난다.

## 오류 메시지

```text
Cannot load extension with file or directory name __next._tree.txt. Filenames starting with "_" are reserved for use by the system.
Cannot load extension with file or directory name _next. Filenames starting with "_" are reserved for use by the system.
```

## 원인 및 해결

- 추정 원인: Next.js 16 정적 Export가 새로 만드는 RSC 데이터 파일(`__next.*.txt`)만 문제다.
- 실제 원인: Chrome은 Extension 최상위의 `_`로 시작하는 파일·폴더를 모두 예약 이름으로 본다. `__next.*.txt`뿐 아니라 정적 자원 폴더 `_next`와 404 폴더 `_not-found`도 해당한다. 이전 확인(`pairing-flow-inline-script-csp-ERROR.md`)은 이 검사를 하지 않는 Chrome for Testing 153에서 해서 드러나지 않았다.
- 해결 방법: 빌드 후처리에서 ① `__next.*.txt`와 `_not-found/`를 지운다(정적 팝업은 일반 페이지 이동으로 동작) ② `_next/`를 `next/`로 바꾸고 HTML·JS·CSS·JSON·TXT 안의 `/_next/` 경로도 바꾼다(Next 런타임이 이 경로로 청크 위치를 찾는다) ③ 최상위에 `_` 이름이 남으면 빌드를 실패시킨다.
- 변경 파일: `scripts/prepare-extension-build.mjs`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (사용자 정식 Chrome에서 로딩 확인)
- [x] 새 탭·기존 탭에서 확인했다. (팝업 렌더링, 콘솔 오류 0건)
- [x] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [x] 제외 도메인을 확인했다. (해당 없음)
- [x] Desktop 연결을 확인했다. (실제 서버로 연결 코드 입력 → 연결 성공)
- [x] 관련 기능의 회귀가 없다. (`pnpm test` 29개 통과)

## 재발 방지 및 남은 작업

- Extension 로딩 확인은 Chrome for Testing이 아니라 **정식 Chrome**에서 한다.
