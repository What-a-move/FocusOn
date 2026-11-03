# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: #22-06
- 오류 식별명: `ELECTRON_TEST_FILE_FAILS_ON_FIRST_RUN`
- 기능명: 로그인·온보딩 인증 흐름
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-21 ~ 2026-10-22
- 작성자: Claude
- 심각도: 낮음 (테스트 실행 환경 문제로 보이며 제품 동작에는 영향 없음)
- 상태: 재현 불가
- 관련 Issue·PR: [#22](https://github.com/What-a-move/FocusOn/issues/22), [PR #39](https://github.com/What-a-move/FocusOn/pull/39)

## 오류 제목

- 새 작업 폴더에서 처음 `pnpm test:electron`을 실행하면 테스트 파일 하나가 파일 단위로 실패함

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 / 11.19.0
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `git worktree`로 새 작업 폴더를 만들고 `pnpm install --offline --frozen-lockfile`을 실행한다.
2. `apps/desktop`에서 `pnpm test:electron`을 처음 실행한다.

## 기대 결과

- 모든 테스트가 통과한다.

## 실제 결과

- 세 번 관찰했다. `auth-ipc.test.cjs` 2회, `auth-service.test.cjs` 1회가 개별 테스트가 아니라 파일 단위로 실패했다(`✖ tests/electron/auth-ipc.test.cjs ... 'test failed'`). 같은 파일을 단독 실행하거나 다시 실행하면 모두 통과했다.
- `dist-electron`을 지우고 다시 빌드한 뒤 세 번 연속 실행했을 때는 재현되지 않았다.

## 오류 메시지

```text
✖ tests/electron/auth-ipc.test.cjs (635.8065ms)
  'test failed'
```

## 원인 및 해결

- 추정 원인: 의존성 설치 직후 첫 실행에서 `require('electron')`이나 빌드 산출물 로딩이 지연·경합되어 파일 로딩 단계에서 실패하는 것으로 보인다.
- 실제 원인: 확인하지 못함 (자세한 실패 출력을 남기기 전에 재현이 멈춤)
- 해결 방법: 없음. 실패 시 같은 명령을 한 번 더 실행하면 통과한다.
- 변경 파일: 없음

## 확인 결과

- [ ] 같은 방법으로 재현되지 않는다. (설치 직후 첫 실행에서만 간헐적)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다. (해당 없음)
- [x] 관련 기능의 회귀가 없다. (재실행 시 27개 모두 통과)

## 재발 방지 및 남은 작업

- 다시 발생하면 `node --test --test-reporter=spec tests/electron/*.test.cjs` 출력 전체를 저장해 실제 원인을 확인하고 이 문서를 갱신한다.
- CI에 Desktop Electron 테스트를 추가할 때 이 현상이 반복되면 설치 직후 한 번 예열 실행하거나 원인 수정 후 상태를 바꾼다.
