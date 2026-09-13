# Backend API 설계 원칙

> 출처: [How We Design Our APIs at Slack](https://slack.engineering/how-we-design-our-apis-at-slack/)의 6가지 원칙을 FocusOn Server(Spring Boot)에 맞게 재구성했다.
> 새 엔드포인트를 설계할 때 아래 원칙을 검토하고, 기획서(`*-PLAN.md`)의 "예외 처리"·"완료 조건" 절에 이 원칙과 관련된 판단을 남긴다.
> 응답 형식·ErrorCode·인증 헤더의 확정 규칙은 `server/docs/DEVELOPMENT_RULES.md`를 따른다. 이 문서는 그 위에 적용하는 설계 원칙이다.

## 1. 한 가지를 잘하기

- 엔드포인트 하나는 하나의 구체적인 사용 사례만 책임진다. 여러 목적을 한 응답에 담지 않는다.
- 예: `SESSION_003`(세션 일시정지·재개·종료·연장)처럼 상태 전이만 담당하는 엔드포인트와, `SESSION_002`(세션 상세 조회)처럼 조회만 담당하는 엔드포인트를 분리한다.
- 컬렉션을 반환하는 응답(`MATERIAL_001` 학습 자료 목록 등)은 페이지네이션 여부를 설계 시점에 결정한다(5번 참고).

## 2. 빠르게 시작할 수 있게 하기

- 기획서에 요청·응답 예시(JSON)를 반드시 포함해 문서만 보고도 호출을 예측할 수 있게 한다.
- 에러 케이스도 예시와 함께 명시한다.

## 3. 직관적 일관성 추구

- 경로: `/api/v1/{도메인}`, 요청·응답 필드는 camelCase를 사용한다.
- 공통 응답 포맷과 ErrorCode 네이밍(`{DOMAIN}_NNN`, 예: `REPORT_001`, `GOAL_004`)은 Notion `FocusOn API 데이터베이스`에서 이미 쓰이는 API 코드 체계를 그대로 따른다.
- REST 동사(GET/POST/PATCH/DELETE)와 상태 코드(200/201/400/401/403/404/409/500)를 의미에 맞게 사용한다.
- 리소스를 전체 교체하는 수정은 PUT, 일부 필드만 수정하는 경우는 PATCH를 사용한다(`server/docs/DECISION_RECORD.md` 결정 009, 현재 "결정 필요" 상태이므로 새 엔드포인트 설계 시 이 원칙을 기본값으로 적용하고 확정되면 기존 API도 맞춘다).
- 약어보다 의도가 드러나는 이름을 쓴다(`sessionId` O, `sid` X).

## 4. 의미 있는 오류 반환

- `server/docs/DEVELOPMENT_RULES.md` BE-002의 공통 ErrorCode 표를 우선 사용하고, 없으면 새 코드를 추가한 뒤 그 표와 `DECISION_RECORD.md`에 기록한다.
- 하나의 오류 코드가 서로 다른 원인을 뭉뚱그리지 않는다. 원인이 다르면 코드를 분리한다.
- 처리하지 못한 예외가 전부 `500 INTERNAL_ERROR`로 뭉개지지 않도록, 새 엔드포인트가 발생시킬 수 있는 예외 유형(검증 실패, 인증 실패, 리소스 없음, 상태 충돌 등)을 구현 중 확인한다.

## 5. 확장성과 성능 고려

- 컬렉션을 반환하는 API는 설계 시점에 페이지네이션 여부를 명시적으로 결정한다. 이번 범위에 넣지 않기로 했다면 그 이유를 기획서의 "리스크 및 고려사항"에 남긴다.
- 컬렉션 안에 컬렉션을 중첩해서 반환하지 않는다. 필요하면 별도 엔드포인트로 분리한다.
- 무제한 검색·조회처럼 오남용 소지가 있는 엔드포인트는 최소 조건이나 속도 제한을 검토하고, 범위에 넣지 않기로 했다면 그 판단도 기획서에 남긴다.

## 6. 하위 호환성 유지

- 기존 응답 필드를 제거하거나 타입·의미를 바꾸지 않는다. 필드 추가는 항상 안전하다.
- 부득이하게 깨지는 변경이 필요하면 기획서에 "기존 Desktop·Extension·AI 클라이언트에 미치는 영향"을 명시하고 검토를 요청한다.

## Notion 명세와의 관계

- Notion `FocusOn API 데이터베이스`에 이미 정의된 API 코드·Request·Response는 실제 값을 그대로 가져오지 않고 형식만 참고한다(실제 예시는 기획서에, Notion에는 스키마만 — `notion-api-spec.md` 참고).
- 기존 Notion 예시에 있는 최상위 `code` 필드(`"code": "SUCCESS"`)는 모든 예시에서 값이 동일해 실질적인 분기 정보가 없다. 새 엔드포인트에서는 `server/docs/DEVELOPMENT_RULES.md` BE-001 기준(최상위 `code` 없음)을 따르고, 정말 여러 성공 케이스를 구분해야 하는 API가 생기면 그 API에 한해 `DECISION_RECORD.md`에 근거를 남기고 추가한다.

## 관련 문서

- `docs/API_CONTRACT.md`
- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/rules/branch-workflow.md` (기획서 작성 단계에서 이 문서를 검토)
- `server/docs/rules/notion-api-spec.md`
