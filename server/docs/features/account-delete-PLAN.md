# Backend 기능 기획서 — 계정 삭제

## 기본 정보

- 기능명: 계정 삭제 (`DELETE /api/v1/users/me`)
- 기능 ID: account-delete
- 작성자: 박진욱
- 작성일: 2026-11-19
- 우선순위: MVP - 기본
- 관련 Issue: #290
- 예정 Branch: `feat/290-server-account-delete`
- 관련 문서: `docs/DATA_PRIVACY.md`(탈퇴 계정 30일 이내 삭제·익명화, 동의 이력 3년). Notion API 명세서에는 항목 없음 → 서버 제안 형식(문서 오너 확인 필요)

## API 정의

- `DELETE /api/v1/users/me`, `Authorization` 필수, 활성 Desktop만.
- 성공 `204 No Content`.
- 실패: 무인증·만료·이미 삭제된 계정·Extension 호출 401 `AUTH_RELOGIN_REQUIRED`(만료 Access Token은 `AUTH_ACCESS_TOKEN_EXPIRED`).

## 처리 (한 transaction, 사용자 → 호출 family 잠금 순서)

1. 호출 Desktop의 family가 활성인지 확인한다.
2. `users.deleted_at = now`, 닉네임 해제(`null`, 다른 사용자가 같은 닉네임을 쓸 수 있게).
3. Google 연결 익명화: `oauth_identities.unlinked_at = now`, `provider_subject = 'deleted:<행 ID>'`. `(provider, sub)` 전체 유일성을 지키면서 같은 Google 계정의 다음 로그인이 새 계정을 만든다.
4. 모든 ACTIVE 기기를 `REVOKED`, family `DEVICE_REVOKED`, 활성 Refresh Token 폐기, Desktop의 발급 중 연결 코드 취소.

## 범위 밖 (후속 Issue)

- 학습 세션·목표·정리·분석 기록 등 개인 데이터의 30일 이내 삭제·익명화 작업.
- 진행 중 세션은 이 API에서 끝내지 않는다. heartbeat가 401로 끊겨 180초 뒤 `ABANDONED`로 정리된다.
- 삭제 취소·복구 없음.

## 완료 조건

- [x] 삭제 후 모든 기기 인증 불가, 같은 Google 계정 재로그인 시 새 계정.
- [x] Extension·무인증·이미 삭제된 계정 호출 401.
- [x] 테스트(H2 전체, PostgreSQL에서 삭제 테스트)·문서 갱신.
