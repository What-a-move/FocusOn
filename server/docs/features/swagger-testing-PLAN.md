# 개발용 Swagger 테스트 설정 계획

- Issue: [#79](https://github.com/What-a-move/FocusOn/issues/79)
- Branch: `chore/#79-swagger-testing` (origin/dev `65da4f0` 기준)
- 작성일·승인일: 2026-10-27
- 유형: `type:chore`, 백엔드 개발 환경 설정
- 승인: 사용자가 이슈 범위를 확인하고 ‘다음 단계 ㄱㄱ’로 구현·검증을 승인했다. PR 생성·merge·Issue 종료는 별도 승인한다.

## 목적과 범위

기존 Swagger 의존성과 수동 QA 방침을 실제 사용할 수 있도록 보완한다. `server/`만 변경하며 API payload·인증 수명·DB schema·Notion 계약을 바꾸지 않는다. 새 제품 API나 임의 토큰 발급 API를 만들지 않는다. 기능 구현이 아닌 환경 설정이므로 이 계획은 승인된 Issue의 범위를 정리한 것이다.

## 구성

1. 기본 프로필에서 UI·OpenAPI 생성을 끈다. `local,swagger`로 명시적으로 켜고 서버를 `127.0.0.1`에 바인딩한다. 개발자가 원격 서버에서 swagger 프로필을 켜거나 바인딩을 덮어쓰지 않는다.
2. `common/openapi/SwaggerConfiguration`은 swagger 프로필과 api-docs 활성 조건에서만 등록한다. 우선 순위 0의 좁은 문서 전용 SecurityFilterChain으로 GET 문서·정적 리소스만 허용한다. 기존 API 인증 체인은 변경하지 않는다.
3. OpenAPI의 전역 HTTP Bearer scheme과 exchange·refresh의 명시적 `security: []`로 현재 구현을 설명한다. `@AuthenticationPrincipal`은 입력 필드로 노출하지 않는다. 미구현 API를 문서에 만들지 않는다.
4. 표준 라이브러리 Python CLI가 Desktop Client ID·PKCE S256·무작위 state·임시 IPv4 loopback 포트를 사용한다. callback의 host·경로·중복 query·state를 검증하고, code를 소비하지 않은 채 exchange 요청 JSON만 0600 임시 파일로 쓴다. Client Secret·토큰·인증 원문은 출력하지 않는다.
5. Swagger 로그인·조회·회전·logout 순서를 사용법으로 남긴다. 실제 Desktop과 별도 installation을 쓰고 logout으로 슬롯을 반환한다.

## 데이터·연동·예외

- Entity·migration 변경 없음. CLI 임시 JSON은 사용자 로컬 일회성 인증 요청이며 완료·실패 후 삭제한다.
- Desktop·Extension·AI 수정 없음. Google 성공 시나리오는 사용자 본인 인증이 필요하다.
- callback 위조·중복·잘못된 state는 400으로 거절하고 정상 callback을 계속 기다린다. 정상 state의 Google 거절·취소와 제한 시간 만료는 파일 없이 종료한다.
- API 오류·한도·Refresh Token strict rotation은 기존 계약 그대로다. code·Refresh Token 재사용이나 제품 인증 우회는 금지한다.
- 서버 환경 외부에 Client Secret을 복사하지 않는다. URL·query·callback 응답 내용을 로그로 남기지 않는다.

## 검증 및 완료 기준

- Swagger 켠 환경 UI·JSON·설정 GET 성공, 문서 비활성 환경 비공개.
- 보호 API 무토큰401·교환 입력 오류400·refresh 본문 인증·logout204 유지.
- Bearer scheme·공개 security 예외·실제 DTO 필드·비영구 인증 저장·외부 validator 비활성 확인.
- PKCE·state·loopback·개인 파일 권한·취소/타임아웃·원문 비노출 오프라인 검증.
- Google 응답만 대체한 인증 흐름과 실제 PostgreSQL/HTTP 경계를 검증하고 실계정 성공과 구분한다.
- 독립 코드 리뷰 → 필요한 수정 → build·Checkstyle·QA 결과 기록 → Issue 체크리스트 갱신.
- 코드 리뷰·QA·사용법·CONTEXT·NEXT_TASK·결정 기록을 같은 작업에 남긴다.
