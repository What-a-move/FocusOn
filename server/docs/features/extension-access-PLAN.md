# 확장 CORS 및 계정당 연결1개 계획

- Issue #151 재사용, 사용자 개발 승인(2026-11-08), `feat/151-extension-access`, server 전용.
- 사용자 제공 확정 정책과 기존 인증·연결 코드를 대조한다. #163 종료 API는 별도 브랜치에 보존한다.

## CORS

- /api/v1/**에만 적용. CORS_ALLOWED_ORIGINS 환경변수의 chrome-extension://[a-p]{32} 정확한 origin 목록만 허용한다. wildcard·null·웹 origin은 설정 단계에서 거절한다.
- local 기본값은 고정 개발 확장ID, docker 기본값은 빈 허용목록이다. Compose에 변수를 전달하고 .env.example에 설명한다.
- GET/POST/PUT/DELETE/OPTIONS, Authorization/Content-Type/Idempotency-Key/If-Match-Version/X-Device-Id/X-Trace-Id 허용. Retry-After 노출, credentials 비활성.
- Security CorsFilter를 인증 필터보다 먼저 적용한다. 정상 preflight만 인증 없이 종료하며 일반 OPTIONS와 실제 API는 인증을 유지한다.

## 단일 확장 정책

- 설치→사용자 잠금 순서를 유지하고 새 연결 시 같은 사용자의 다른 ACTIVE 확장 family/refresh와 device를 REVOKED로 전환한다. family 사유 REPLACED.
- 같은 installationId는 deviceId 재사용·이전family 폐기 흐름을 유지한다. 다른 사용자 소유 활성 설치는 기존 LINK007을 유지한다.
- V11 신규 migration: REPLACED 사유 추가. 기존 복수 활성 확장은 가장 최근 ACTIVE family 생성시각(없으면 device 생성시각), id 순으로1개만 유지하고 나머지를 폐기한다. 데이터 삭제 없이 device/family/refresh 상태만 변경한다. 이후 사용자별 ACTIVE CHROME_EXTENSION partial unique index로 DB 제약을 둔다.
- migration은 배포 시 이전 확장의 로그인을 끊는다. 개발 DB에는 자동 적용하지 않고 격리DB에서 기존 데이터 전후 보존을 검증한다.

## 사전 점검

- 임의 origin·인증우회·쿠키 활성화 없음, CORS만으로 Chrome 권한철회 문제가 모두 해결된다고 주장하지 않는다.
- 동시 서로 다른 설치는 사용자 잠금으로 직렬화하고 DB 제약을 보강한다. 기존 family/refresh 폐기와 새 연결은 같은 transaction이다.
- 기존 다중확장을 유지하는 테스트 기대값은 정책에 맞춰 변경하되 타 사용자 격리를 따로 확인한다.
- API 필드·Frontend 코드·세션 상태 전이는 변경하지 않는다. local-events와 권한 철회 후 실제 자동멈춤은 세션 API와 Chrome 환경 후속 QA다.

## 검증

- 허용/불허 origin·method·header, 실제 API401/응답헤더 노출, 일반OPTIONS 인증.
- 같은 설치 재사용, 다른 설치 교체, 다른 계정 격리, 동시 연결1개·이전토큰 LINK_TOKEN_REVOKED.
- 격리 PostgreSQL V1~V11 및 V10 복수확장 fixture 업그레이드·unique 거절, Backend 전체 build/Checkstyle.
- 사용자 요청의 검사 절차는 기획 점검·자동 테스트·diff 검토로 수행한다. 독립 리뷰를 받지 못했다면 완료로 표시하지 않는다.
