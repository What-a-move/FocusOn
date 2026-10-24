# Google 로그인 QA

- 날짜: 2026-10-25
- Issue: #49
- 환경: Java 25 / Spring Boot 4.1.1 / H2 / PostgreSQL 17.11
- 범위: 승인된 Google 교환·auth/me·공통 인증·초기 토큰 발급

## 실행 결과

| 검증 | 결과 |
| --- | --- |
| Backend clean build·Checkstyle·회귀 | 전체 29개 테스트 통과(failure/error 0), Checkstyle 통과. 합성 Google 키·모의 Google HTTP 사용 |
| PostgreSQL 17.11 | 별도 임시 QA DB에 Flyway V1·V2 적용, Hibernate validate, 로그인·저장 모델 테스트 통과 |
| 동일 Google 계정 동시 최초 로그인 | 사용자 1명으로 수렴, Desktop 3대 성공·4번째 제한 |
| 동일 설치 재로그인 | deviceId 유지·새 family와 토큰 발급·이전 토큰 요청 거절 |
| auth/me | 검증된 사용자·기기만 200. 다른 X-Device-Id·Bearer 누락은 공통 401 |
| Google ID token | 합성 RSA 서명·issuer·audience·만료·sub 검사 |
| Google 교환 | 고정 token endpoint·POST form·code·PKCE·redirect 원문 전달 검사 |
| 만료/위조 | 서명된 만료만 만료 예외, 위조된 만료 payload는 일반 인증 실패 |
| Google JWK 장애 | 모의 네트워크 오류 503·timeout504 구분 |
| 한도 | 키별 한도·Retry-After 양수·익명 공간 포화가 인증 공간에 영향 없음 |
| 민감값 | DTO·토큰 응답의 toString 원문 숨김, refresh·installation ID는 해시 저장 |

## PostgreSQL 재현 방식

기존 개발 DB를 건드리지 않고 `focuson_auth_qa_20261025` 임시 DB를 생성했다. 환경변수로 datasource·driver를 PostgreSQL로 지정하고 Flyway=true, ddl-auto=validate로 로그인·저장 모델 테스트를 실행했다. migration 이력은 1:true, 2:true였으며 해당 QA DB만 종료 후 제거했다. 계정 값·비밀번호·token은 출력하지 않았다.

## 미검증·제한

- 실제 사용자 Google 로그인 → Desktop callback → 서버 → Keychain 왕복은 미실행.
- refresh·logout·Extension pairing·기기 관리 API는 이번 구현 범위 밖.
- HTTP 테스트는 MockMvc로 Controller·필터·저장 모델을 연결했으며 실제 외부 Google만 대체했다. 합성 키 검증 통과를 실제 Google 계정 검증 완료로 표현하지 않는다.
- 원격 CI는 PR을 만들지 않았으므로 실행 결과가 없다.
- 새 Google client·key 초기 다운로드의 실제 네트워크 지연·부하 성능은 측정하지 않았다.

## 2차 적합성 검사

- 현재 Notion Google exchange·auth/me의 필드·응답·헤더·권한을 다시 읽고 Desktop auth-api 파서와 대조했다.
- 최신 origin/dev를 fetch했으며 작업 시작 이후 새 dev 변경은 없었다.
- 독립 재검토에서 새 actionable P1/P2가 보고되지 않았다.
- 테스트 2개를 추가해 정상 서명의 다른 사용자 ID, 폐기된 Desktop, Access Token 유효기간 안의 ACTIVE family 만료 시각을 재현했다. 모두 auth/me에서 401 AUTH_RELOGIN_REQUIRED로 거절했다.
- 위 추가 사례를 별도 PostgreSQL QA DB에서도 실행하고 V1·V2 성공을 재확인했다. 기존 개발 DB와 migration 파일은 변경하지 않았다.
- 실행 코드의 추가 수정은 필요하지 않았으며 실제 Google 사용자 왕복과 refresh·logout 등 후속 범위는 미검증 상태를 유지한다.
