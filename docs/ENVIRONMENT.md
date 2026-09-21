# 개발 환경

## 기본 기술

| 영역 | 기술 |
| --- | --- |
| Desktop | Electron, Next.js, React, TypeScript |
| Extension | Chrome Extension Manifest V3, Next.js, React, TypeScript |
| 작업 관리 | pnpm Workspace, Turborepo |
| Server | Java 25, Spring Boot 4.1.1, Gradle |
| Database | PostgreSQL 17, Flyway |
| AI | Python 서비스 scaffold, 실행 환경 미구현 |
| 카메라 분석 | 클라이언트 MediaPipe |

## 프론트엔드

```bash
pnpm install
pnpm dev
pnpm dev:desktop
pnpm dev:extension
pnpm build
pnpm lint
```

- Desktop: `http://localhost:3000`
- Extension UI: `http://localhost:3001`

## Server와 PostgreSQL

최초 1회 `server/.env.example`을 `server/.env`로 복사하고 로컬 비밀번호를 설정한다. `.env`는 Git에 올리지 않는다.

```bash
cd server
docker compose up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

Backend까지 Docker로 실행할 때:

```bash
cd server
docker compose up --build
```

- PostgreSQL: 기본 `127.0.0.1:5432`
- Backend: 기본 `127.0.0.1:8080`
- 컨테이너와 DB의 시간대: UTC
- DB 데이터: `postgres-data` Docker volume에 유지

팀원은 로컬 PostgreSQL을 별도로 설치할 필요 없이 Docker Compose로 같은 버전을 실행한다.

## AI

`AI/src`의 모듈과 `requirements.txt`는 현재 비어 있다. 실행 명령을 문서나 CI에 추가하기 전에 FastAPI·Pydantic·pytest 버전과 진입점을 구현한다.

## 환경 변수

- 비밀 값은 `.env`에만 저장하고 `.env.example`에는 키 이름과 안전한 예시만 둔다.
- API key, OAuth secret, token, DB password를 Issue·PR·로그에 남기지 않는다.
- 팀 공유 값과 개인 비밀 값을 구분한다.
- OAuth redirect URI는 실제 Desktop 로그인 방식이 확정된 뒤 Google Console과 환경 설정에 동일하게 등록한다.
