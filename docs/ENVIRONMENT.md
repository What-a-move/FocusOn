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
| 카메라 분석 | macOS 클라이언트 AVFoundation + Apple Vision |

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

최초 1회 `server/.env.example`을 `server/.env`로 복사하고 로컬 PostgreSQL 비밀번호를 설정한다. `openssl rand -base64 32`로 생성한 값을 `JWT_SECRET_BASE64`에도 넣는다. Docker Compose는 PostgreSQL만 시작할 때도 Backend의 필수 환경 변수인 이 값을 확인한다. `.env`는 Git에 올리지 않는다.

```bash
cd server
docker compose up -d postgres
set -a
. ./.env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

Gradle에서 직접 Backend를 실행할 때는 위처럼 `.env`를 현재 셸에 읽어야 한다. Spring Boot는 `.env` 파일을 자동으로 읽지 않는다. `.env` 값은 셸에서 읽을 수 있는 형식으로 작성한다.

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
- Desktop OAuth는 기본 브라우저·PKCE와 `http://127.0.0.1:<임시 포트>` loopback을 사용한다. Desktop이 실제 사용한 redirectUri를 서버가 동일하게 Google에 전달하며 임의의 고정 웹 callback을 추가하지 않는다.
- 로컬 env 파일 이름은 위치별 `.env` 하나로 통일한다: `server/.env`, `apps/desktop/.env`, `apps/extension/.env`. 세 파일 모두 Git에서 제외되며, 각 위치의 `.env.example`을 복사해 만든다.
- Desktop은 `apps/desktop/.env`에 `FOCUSON_GOOGLE_CLIENT_ID`, `FOCUSON_API_BASE_URL`을 둔다. Electron은 이 파일을 자동으로 읽지 않으므로 저장소 루트에서 `set -a && source apps/desktop/.env && set +a`로 읽은 뒤 `pnpm --filter @focuson/desktop dev:electron`을 실행한다.
- Extension은 `apps/extension/.env`에 `NEXT_PUBLIC_FOCUSON_API_BASE_URL`을 둔다. Next.js Build가 자동으로 읽으므로 `pnpm build:extension`만 실행한다.
- Backend는 같은 Desktop client ID를 `GOOGLE_DESKTOP_CLIENT_ID`로 설정한다. 발급 JSON에 secret이 있으면 `GOOGLE_DESKTOP_CLIENT_SECRET`을 서버 `.env`에만 설정하고 Desktop에 넣지 않는다.
- Google 설정이 비어 있어도 서버 health는 동작하지만 교환은 503으로 실패한다. `.env.example`은 실제 자격 증명으로 사용하지 않는다.
- `server.forward-headers-strategy=none`으로 직접 연결 IP만 신뢰한다. 운영 reverse proxy를 추가할 때 신뢰 proxy와 IP 한도를 별도 구성한다.

### 로그인 남용 방어와 proxy

Google 교환은 기존 IP+installationId별 5회/10분 외에 IP별 300회/10분 보호 상한을 먼저 검사한다. 설치 ID 변경으로 한 IP가 로그인 키 공간을 채우는 것을 제한한다. 단일 서버 메모리 방식이며 분산 IP 공격 방어는 별도다. `server.forward-headers-strategy=none`이므로 reverse proxy 뒤에서는 proxy 주소를 공유한다. 배포 시 신뢰 proxy와 클라이언트 IP 전달 경계를 확정하고 공유 트래픽 기준으로 상한을 검토한다. 임의의 X-Forwarded-For는 신뢰하지 않는다.
