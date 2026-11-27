# Backend 기능 기획서 — 세션 끝 회고 저장

## 기본 정보

- 기능명: 세션 끝 한 줄 회고(session_retrospectives)
- 기능 ID: session-retrospective
- 작성자: 박진욱
- 작성일: 2026-11-26
- 우선순위: MVP - 기본
- 관련 Issue: #318
- 예정 Branch: `feat/318-server-session-retrospective`
- 관련 문서: Notion API 명세 `PUT /api/v1/sessions/{sessionId}/retrospective`, ERD `session_retrospectives`(재설계 1차)

## 기능 목적

- 해결할 문제: 세션이 끝난 뒤 사용자의 체감과 「별로였어」 사유를 모을 곳이 없다. 앱 문제 사유는 오알림·빈도 개선의 근거가 된다.
- Backend가 담당할 역할: 끝난 세션에 회고를 하나 저장(덮어쓰기)하고, 메모를 안전하게 받는다.

## API 정의

- `PUT /api/v1/sessions/{sessionId}/retrospective`(Authorization, X-Device-Id, Desktop)
- Request `{ rating: GOOD|OKAY|BAD, reasonCodes?, note? }` — 사유·메모는 BAD일 때만, 사유 중복 불가, 메모 0~100자.
- Response 200 `{ sessionId, rating, reasonCodes, note, updatedAt }`
- Failure: 400 `VALIDATION_ERROR`(메모 민감정보는 `details.fieldErrors.note: "SENSITIVE"`), 404 `SESSION_NOT_FOUND`, 409 `SESSION_STATE_INVALID`.

## 데이터 설계

- V34 `session_retrospectives`(session_id PK·FK CASCADE, rating CHECK, reason_codes_json JSONB, note VARCHAR(100), created_at, updated_at), BAD가 아니면 사유·메모 없음 CHECK.
- V33은 #317이 쓰므로 #317 머지 뒤 머지한다.
- 계정 삭제 정리 대상에 포함.

## 처리 흐름

1. Desktop·기기 헤더 확인 → 입력 검증(민감정보는 학습 정리와 같은 `SensitiveText` 기준) → 세션 행 잠금 → 끝난 세션 확인 → 있으면 덮어쓰기, 없으면 생성.
2. 메모 원문은 로그·오류 응답에 넣지 않는다.

## 범위 밖

- 회고 조회 API, 앱 문제 사유 집계, 보관 기간 정리, Desktop 개인정보 안내 문구.

## 완료 조건

- [x] 끝난 세션에만 저장·덮어쓰기.
- [x] 입력 규칙·민감정보 거절·소유권 테스트.
- [x] 마이그레이션·테스트·문서 갱신.
