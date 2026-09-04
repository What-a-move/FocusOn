# 개발 환경

## 기본 기술

| 영역 | 기술 |
| --- | --- |
| Desktop | Electron, Next.js, React, TypeScript |
| Extension | Chrome Extension Manifest V3, Next.js, React, TypeScript |
| 공통 패키지 | pnpm Workspace |
| Server | Spring Boot |
| AI | Python 기반 예정, 실행 방식 검토 필요 |
| 카메라 분석 | MediaPipe 기반 클라이언트 처리 우선 검토 |

## 실행 명령

```bash
pnpm install
pnpm --filter @focuson/desktop dev
pnpm --filter @focuson/extension dev
pnpm --filter @focuson/desktop build
pnpm --filter @focuson/extension build
pnpm --filter @focuson/desktop lint
pnpm --filter @focuson/extension lint
./server/gradlew -p server test
```

개발 서버 포트:

- Desktop: `http://localhost:3000`
- Extension UI 확인: `http://localhost:3001`

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

## 현재 확인된 제한

- Server Java 컴파일은 통과한다.
- Server 전체 테스트는 PostgreSQL 등 데이터베이스 연결 설정이 준비된 뒤 통과시킬 수 있다.
- 현재 `server/src/main/resources/application.properties`에는 애플리케이션 이름만 있어 테스트 실행 시 DataSource 설정이 필요하다.
- AI 폴더는 전용 문서·템플릿 구조까지 구성된 상태이며 실행 명령·의존성·분석 코드는 AI 담당자가 확정해야 한다.
