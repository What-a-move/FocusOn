# Extension 연결 독립 코드 리뷰

- Issue: #58 / 기준 origin/dev b293bf1 / 2026-10-28
- 구현 대화를 전달하지 않은 별도 에이전트가 diff·PLAN·컨벤션만으로 검토했다. 파일 수정·테스트 실행은 하지 않았다.
- 범위: 공개 인증 필터·한도, 설치별 실패 commit·잠금 순서, 재연결 token 폐기, SQL/FK/보관, DTO 비밀 비노출.

## 1. 🟡 Medium — 만료된 ACTIVE 코드가 취소로 덮임 (P2, 해결)

문제: PairingCodeRepository.cancelActive가 expires_at 경과 뒤 sweep 전 재발급·logout·재로그인에서 CANCELLED/terminal_at=now로 변경했다. 기대 오류 LINK_002가 LINK_005로 바뀌고 30일 보관 시작도 늦어졌다.

해결 방안:
1. 해당 Desktop의 만료 행을 먼저 EXPIRED/terminal_at=expires_at으로 확정하고 유효 행만 취소한다. 기존 사용자 잠금·transaction을 유지한다. 조회가 두 번이지만 상태·보관 의미가 명확하다. **채택.**
2. CASE 갱신 한 번으로 만료·취소를 분기한다. 왕복은 적지만 enum·nullable timestamp의 동시 갱신식을 읽기 어렵다.

반영: expireForDesktop→cancelUnexpired를 같은 사용자 transaction 안에서 호출한다. 만료 직후 재발급·logout·재로그인 회귀 테스트를 추가했다.

## 재리뷰

- 기존 P2 해결 확인. 신규 확정 P1/P2 없음. Critical/High/미해결 Medium/Low 없음.
- 전체 실행 검증은 별도 QA 문서에 기록한다. CodeRabbit 또는 GitHub PR 리뷰를 받은 것으로 표현하지 않는다.

## 최신 dev 통합·24시간 정리 재리뷰 (2026-10-28)

- 별도 리뷰에서 V3→V4, 24시간 경계·cleanup/입력 경쟁·3분 잠금 유지와 최신 dev 보존을 확인했다. 신규 확정 P1/P2 없음.
- 리뷰에서 마이그레이션 README의 오래된 V3 연결 파일 예시를 지적해 README와 migration-convention.md를 실제 V3/V4에 맞췄다.

## 프론트 응답 계약 테스트 재검토 (2026-10-28)

- 구현과 격리된 리뷰어가 추가된 Backend HTTP 테스트를 검토했다. 발급 응답·문자열 ID·하이픈 없는 코드·연결 응답·소비 뒤 취소에 대한 확인이 실제 계약과 일치하며 확정 지적은 없었다.
- 리뷰어는 테스트를 실행하지 않았다. 실행 결과는 QA 문서를 따른다.
