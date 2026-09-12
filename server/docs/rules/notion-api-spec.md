# Notion API 명세서 작성 규칙

대상: Notion "FocusOn API 데이터베이스"(`server/docs/rules/branch-workflow.md` 11단계 — PR이 `dev`에 머지된 게 확인되면 이번에 추가·변경된 엔드포인트를 여기에 반영한다).

## 현재 컬럼 구조 (실제 확인됨, 2026-09-12)

API 코드 / Method / Request / Response / 도메인 / 설명 / 엔드포인트 / 역할 / 예외·오류 처리 / 인증 필요

## 필드 작성 규칙

- **API 코드**: `{도메인}_{순번}` 형식(예: `SESSION_001`, `GOAL_004`, `REPORT_001`). 이미 이 형식으로 15개 이상 등록되어 있다. 같은 도메인 안에서 실제로 구현·머지된 순서대로 번호를 매긴다(기획 순서가 아니라).
  - 이 코드는 `server/docs/DEVELOPMENT_RULES.md` BE-002의 `ErrorCode`(`INVALID_REQUEST` 등)와는 별개다. API 코드는 "이 엔드포인트가 무엇인지" 식별하는 용도이고, ErrorCode는 "오류 응답의 `error.code` 값"이다. 혼동하지 않는다.
- **엔드포인트 / Method**: 실제 경로와 HTTP Method를 적는다.
- **Request / Response**: **현재는 실제 예시 값(예: `"id": 1`, `"title": "string"`)이 섞여 등록되어 있다.** 새로 추가하는 항목부터는 형식(필드명 + 타입/설명)만 적는 것을 권장한다.
  ```json
  {
    "sessionId": "string (uuid)",
    "status": "IN_PROGRESS | PAUSED | ENDED"
  }
  ```
  실제 예시 값은 기획서(`server/docs/features/{기능명}-PLAN.md`)에 있으므로 Notion에는 스키마 참고용만 남긴다. 기존에 이미 실제 값으로 등록된 항목을 이 규칙 때문에 소급해서 전부 고치지는 않는다 — 새로 추가·변경하는 항목부터 적용한다.
- **응답 최상위 `code` 필드**: 현재 등록된 모든 성공 응답에 `"code": "SUCCESS"`가 고정값으로 들어있다(15개 항목 전수 확인, 값이 갈리는 경우 없음). `server/docs/DEVELOPMENT_RULES.md` BE-001, `DECISION_RECORD.md` 결정 003에 따라 새 엔드포인트는 이 필드를 넣지 않는 것을 기본으로 한다. 정말 여러 성공 케이스를 구분해야 하는 API가 생기면 그 API에 한해 실제로 갈리는 값을 정의하고 결정 기록을 남긴 뒤 추가한다.
- **예외·오류 처리**: 서술형 문단 대신 번호를 매긴 목록으로 정리한다. 형식 예:
  ```
  1. 401 UNAUTHORIZED - 토큰이 없거나 만료됨
  2. 404 NOT_FOUND - 존재하지 않는 sessionId
  3. 409 CONFLICT - 이미 종료된 세션에 대한 재개 요청
  ```
- **인증 필요**: 해당 API에 `Authorization: Bearer {token}`이 필요한지 체크한다(`server/docs/DEVELOPMENT_RULES.md` BE-004).

## 반영 시점

- PR이 `dev`에 머지된 걸 확인한 뒤 진행한다(머지 전 상태를 미리 적어두지 않는다).
- 그 PR에서 실제로 추가·변경된 엔드포인트만 다룬다. 과거에 이 규칙이 생기기 전 머지되어 Notion에 아직 없거나 형식이 다른 엔드포인트를 발견하면, 임의로 다 고치지 말고 작업자에게 먼저 확인한다.

## 관련 문서

- `docs/API_CONTRACT.md`
- `server/docs/DEVELOPMENT_RULES.md`
- `server/docs/DECISION_RECORD.md` (결정 003, 004: 응답 형식·ErrorCode)
