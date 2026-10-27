# Swagger 테스트 설정 독립 코드 리뷰

- 일자: 2026-10-27
- Issue: [#79](https://github.com/What-a-move/FocusOn/issues/79)
- 대상: `chore/#79-swagger-testing`의 `origin/dev 65da4f0` 대비 `server/` 변경
- 방법: 구현 대화가 없는 별도 에이전트에게 diff·PLAN·컨벤션·API/개인정보 기준만 전달했다. 구현자는 지적을 수정하고 재검토를 요청했다.

## 최종 결과

미해결 Critical/High/Medium/Low 없음. 최초 Low 1건은 아래와 같이 수정했고 독립 재검토에서 해소를 확인했다. 실제 Google 성공과 PostgreSQL HTTP 결과는 별도 QA 문서에서 구분한다.

### 1. 🟢 Low — CLI의 길이 검사 문자열과 실제 전달 문자열 불일치 (해결)

**문제:** `server/scripts/google_pkce_request.py`의 CLI 검증에서 `strip()`한 문자열 길이만 검사하고 원문을 JSON에 썼다. 공백 129개 뒤 `x`를 둔 installation ID가 검사에 통과하지만 서버 `@Size(max=128)`에서 거절돼, Google 인증 후 사용할 수 없는 요청이 생성됐다. Python 문자 수와 Java UTF-16 길이가 다른 이모지도 같은 경계에 영향을 준다.

**해결 방안:**

1. CLI 입력을 먼저 trim하고 그 값을 UTF-16 코드 단위로 검사·전달한다. QA 도구의 불필요한 바깥 공백을 제거하고 Java DTO와 일치시킬 수 있다. 문자열 정규화 기준이 명시돼야 한다.
2. 원문을 그대로 유지하되 원문 UTF-16 길이를 검사한다. 입력을 바꾸지 않지만 불필요한 바깥 공백 때문에 사용자가 재입력해야 한다.

**반영:** 1번을 적용했다. 공백 포함 ID의 정규화·80 UTF-16 단위를 넘는 deviceName 거절 회귀 테스트를 추가했다. 잘못된 HTTP Host 거절도 실제 loopback 테스트에 추가했다. 독립 재검토에서 기존 지적 해소·추가 결함 없음, Python 10개 통과를 확인했다.

## 확인 범위

- 최초 구성의 공통 Swagger 비활성, 별도 swagger 프로필·api-docs 속성 조건, GET 문서 전용 좁은 보안 체인, 제품 API 인증 분리.
- 전역 Bearer와 공개 exchange·refresh의 `security: []`, principal 입력 비노출.
- PKCE S256, 무작위 state, Host·경로·중복 query 검증, 취소/타임아웃, 미사용 code 반환.
- 0600 임시 파일, 실패 시 파일 삭제, Google Client Secret·인증 원문 비출력.
- 설치된 springdoc 3.1.0 소스로 빈 validatorUrl 동작과 사용법의 인증·회전·logout 순서 확인.
- 리뷰자가 Python 10개를 직접 재실행했다. Java 빌드는 구현자 실행 결과를 확인했으며 리뷰자가 별도 재실행했다고 기록하지 않는다.

## 추가 리뷰: local .env 자동 읽기 (2026-10-27)

구현 대화가 없는 별도 에이전트가 이번 HEAD diff와 추가 승인 PLAN·컨벤션만 검토했다. 실제 자격 증명은 읽지 않고 합성 파일로 Spring Boot 4.1.1 Config Data 로딩을 확인했다. root/server 실행 위치·파일 간 우선순위·OS override·포트 fallback·파일 누락·docker/test 제외·local,swagger 동작이 일치했으며 Critical/High/Medium/Low 없음으로 결론 냈다.

## 최종 리뷰: Swagger local 통합 (2026-10-27)

구현 대화가 없는 별도 에이전트가 이번 HEAD diff·최종 PLAN·컨벤션을 정적으로 검토했다. local 기본 Swagger·loopback·GET 문서 체인과 제품 API Bearer, docker/test 비공개, 테스트 프로필 순서에 따른 H2/테스트 JWT 우선 적용, 실행 문서가 일치하며 Critical/High/Medium/Low 없음이었다. 리뷰자는 테스트를 직접 실행하지 않았고 실제 .env를 읽지 않았다. 앞선 별도 swagger 프로필 방식은 결정029로 대체됐다.
