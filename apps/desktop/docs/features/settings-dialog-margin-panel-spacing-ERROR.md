# Desktop 개발 중 오류 보고서

> Desktop 오류는 재현 가능한 정보 중심으로 기록한다.
> 토큰, 비밀번호, 화면 원본, 카메라 원본은 기록하지 않는다.
> 파일명은 `기능명-오류명-ERROR.md`로 작성하며 오류마다 파일을 분리한다.

## 오류 기본 정보

- 오류 번호: settings-001
- 오류 식별명: dialog-margin-panel-spacing
- 기능명: 설정 (계정·기기 연결 탭의 확인 창)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-11-02
- 작성자: jae1
- 심각도: 보통 (화면이 시안과 다르게 보임)
- 상태: 해결
- 관련 Issue·PR: #133

## 오류 제목

- 설정의 확인 창(동의 철회·연결 해제·로그아웃)이 가운데가 아니라 위쪽에 붙어 열림

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Electron 버전: 44 (`pnpm dev` 화면과 헤드리스 Chrome에서 확인)
- Node.js·pnpm: Next.js 16.3.4, Tailwind CSS v4
- 화면 기록 권한: 해당 없음
- 카메라 권한: 해당 없음

## 재현 방법

1. `/settings/#account`에서 `화면 분석 동의 철회`를 누른다.
2. `showModal()`로 확인 창이 열린다.
3. 창의 위·아래 여백을 본다.

## 기대 결과

- 창이 세로 가운데에 뜬다.

## 실제 결과

- 창 위 여백이 아래보다 61px 작아 위쪽에 붙어 보였다.

## 오류 메시지

```text
getComputedStyle(dialog).marginTop → '61px' 위·아래가 같지 않음 (auto가 아님)
```

## 원인 및 해결

- 추정 원인: 예전과 같은 Tailwind Preflight의 margin 초기화(`screen-observation-dialog-margin-preflight-ERROR.md`).
- 실제 원인: 추정과 달랐다. 전역 `dialog { margin: auto; }`는 살아 있었지만, 확인 창이 `.fo-settings-panel` 안에 있어서 형제 간격 규칙 `.fo-settings-panel > * + * { margin-top: … }`이 `<dialog>`에도 붙었다. 이 선택자가 `dialog`보다 구체적이라 위쪽 margin만 덮어써 가운데 정렬이 깨졌다.
- 해결 방법: `app/globals.css`에 `dialog:modal { margin: auto; }`를 더해 모달로 열린 창은 부모의 간격 규칙보다 우선해 가운데에 고정했다.
- 변경 파일: `app/globals.css`

## 확인 결과

- [x] 같은 방법으로 재현되지 않는다. (위·아래 여백 372px로 같음)
- [ ] 권한 허용·거부 양쪽을 확인했다. (권한과 무관)
- [ ] 앱 재실행 후 정상 동작한다. (개발 서버 화면에서 확인)
- [x] 관련 기능의 회귀가 없다. (`pnpm test:electron`, `pnpm lint`, `pnpm build`)

## 재발 방지 및 남은 작업

- 패널 안에 창을 두더라도 `:modal`로 열린 창은 전역 규칙이 이긴다. 새 간격 규칙을 만들 때 `<dialog>`가 자식으로 들어올 수 있음을 기억한다.
