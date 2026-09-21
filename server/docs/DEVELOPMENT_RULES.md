# Backend 개발 불변 규칙 (DEVELOPMENT_RULES)

> Backend(Server) 담당자가 매 작업에서 반복 확인하는 규칙을 Rule ID로 정리한다.
> 관련 Issue: #4
> 규칙이 바뀌면 이 문서를 갱신하고 `DECISION_RECORD.md`에 결정 이력을 남긴다.

## BE-001 공통 응답 형식

- 모든 API는 루트 `docs/API_CONTRACT.md`에 정의된 형식을 그대로 따른다.
- 성공 응답: `{ "success": true, "data": {}, "message": "" }`
- 실패 응답: `{ "success": false, "error": { "code": "", "message": "" } }`
- 응답 최상위에 별도 `code` 필드를 추가하지 않는다. Notion `FocusOn API 데이터베이스`의 일부 예시에는 최상위 `code`가 섞여 있으나, 이번 정리에서 `docs/API_CONTRACT.md` 기준으로 통일한다 (`DECISION_RECORD.md` 결정 003 참고, Desktop·Extension·AI 확인 전까지 "제안" 상태).
- 클라이언트는 `error.code` 값으로 분기하고 `message` 문구에 의존하지 않는다.
- 확인 방법: PR 작성 시 새 API 응답 예시를 `docs/API_CONTRACT.md` 형식과 비교한다.

## BE-002 공통 ErrorCode 목록 (초안)

| Code | 의미 | 기본 HTTP 상태 |
| --- | --- | --- |
| `INVALID_REQUEST` | 요청 값 검증 실패 | 400 |
| `UNAUTHORIZED` | 인증 실패, 토큰 없음/만료 | 401 |
| `FORBIDDEN` | 권한 없음 | 403 |
| `NOT_FOUND` | 대상 리소스 없음 | 404 |
| `CONFLICT` | 중복 요청, 상태 충돌(예: 오래된 `version`) | 409 |
| `INTERNAL_ERROR` | 서버 내부 오류 | 500 |

- 이 표는 초안이며 Notion `ErrorCode` 페이지가 비어 있던 것을 대체하는 시작점이다(`DECISION_RECORD.md` 결정 004).
- 기능별로 새 코드가 필요하면 이 표에 추가하고 `DECISION_RECORD.md`에도 기록한다.
- 확인 방법: 새 예외 처리 추가 시 이 표에 있는 코드를 우선 사용하고, 없으면 표를 갱신한다.

## BE-003 계층 책임 분리

- Controller: 요청·응답 변환과 입력 검증 트리거만 담당한다. 비즈니스 로직을 작성하지 않는다.
- Service: 비즈니스 로직과 트랜잭션 경계를 담당한다.
- Repository: 데이터 접근만 담당한다.
- 확인 방법: PR 리뷰에서 Controller 파일에 조건·반복 기반 비즈니스 판단이 있는지 확인한다.

## BE-004 인증 방식

- 인증이 필요한 모든 API는 `Authorization: Bearer {token}` 헤더를 사용한다.
- `POST /api/v1/auth/google/exchange` 성공 시 FocusOn 자체 토큰을 발급하며, Google Access Token은 Desktop·Extension 사이에서 재사용하지 않는다(`docs/API_CONTRACT.md` 인증 절 참고).
- 토큰 검증 실패는 `UNAUTHORIZED`, 권한 부족은 `FORBIDDEN`으로 응답한다.
- 확인 방법: 기능 기획서(`*-PLAN.md`)의 "인증 필요 여부" 항목에 반드시 표시한다.

## BE-005 로깅 및 개인정보 규칙

- 루트 `docs/DATA_PRIVACY.md`를 그대로 따른다.
- 로그에는 요청 시각, 기능명, 오류 코드, 세션 식별자만 남기는 것을 기본으로 한다.
- 비밀번호, API Key, 인증 토큰, 원본 화면·카메라 데이터는 로그와 `*-ERROR.md`에 남기지 않는다.
- 확인 방법: PR 설명에 "로그에 민감 정보 없음"을 확인해 남긴다.

## 확인 방법 요약

| Rule ID | 대상 | 확인 방법 |
| --- | --- | --- |
| BE-001 | 모든 API 응답 | `docs/API_CONTRACT.md`와 형식 비교 |
| BE-002 | 오류 응답 | ErrorCode 표에 있는 코드만 사용했는지 확인 |
| BE-003 | Controller / Service / Repository | PR 리뷰에서 계층 분리 확인 |
| BE-004 | 인증 필요 API | Authorization 헤더·토큰 발급 흐름 확인 |
| BE-005 | 로그 / 문서 | 민감 정보 포함 여부 확인 |

## 관련 문서

- `docs/API_CONTRACT.md`
- `docs/DATA_PRIVACY.md`
- `server/docs/CONTEXT.md`
- `server/docs/DECISION_RECORD.md`
- Notion: `FocusOn API 데이터베이스`
