# 개발 환경

## 기본 기술

| 영역 | 기술 |
| --- | --- |
| Desktop | Electron, React, TypeScript |
| Extension | Chrome Extension Manifest V3, React, TypeScript |
| 공통 패키지 | pnpm Workspace |
| Server | Spring Boot |
| AI | 담당자 협의 후 확정 |
| 카메라 분석 | MediaPipe 기반 클라이언트 처리 우선 검토 |

## 실행 명령

```bash
pnpm install
pnpm --filter @focuson/desktop dev
pnpm --filter @focuson/extension dev
pnpm --filter @focuson/desktop build
pnpm --filter @focuson/extension build
```

## 환경 변수

- 비밀 값은 `.env`에 저장하고 Git에 올리지 않는다.
- `.env.example`에는 키 이름과 예시 형식만 기록한다.
- API Key, 비밀번호, OAuth Secret을 Issue·PR·로그에 남기지 않는다.
- 환경 변수 이름은 대문자와 언더스코어를 사용한다.

## 실행 전 확인

- Node.js LTS가 설치되어 있는지 확인한다.
- pnpm 버전이 팀 기준과 일치하는지 확인한다.
- macOS 카메라·화면 기록 권한을 확인한다.
- Chrome Extension 개발자 모드가 켜져 있는지 확인한다.

