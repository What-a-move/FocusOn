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

## 협업자 PR #84 통합 재검토 (2026-10-28)

- 격리된 리뷰어가 origin/dev 6c93515 보존, pairing V4→V5의 내용 동일성, Flyway 중복 및 테스트 범위를 검토했다. 확정 지적 없음.
- H2에는 PostgreSQL partial UNIQUE가 없으므로 닉네임 동시 요청은 실제 PostgreSQL에서 검증하고, 동의·설정 동시성은 H2에서도 검증한다. 사전 조회가 두 요청에서 실제로 겹쳤는지는 측정하지 않았다.

## 익명 설치 한도 저장 공간 (2026-10-28)

- PR #92 리뷰의 공유 bucket 고갈 위험을 확인했다. 설치별 한도를 IP별로 분리하고 각 IP의 활성 설치 기록을 32개로 제한했다. IP 제한과 인증 API 공간은 별도다. 형식 오류도 기존 IP+installationId 10회/10분 한도에 포함한다.
- 포화 정리 잠금은 설치 한도 전용이며 일반 인증 요청의 제한 잠금을 직접 점유하지 않는다. 독립 재검토에서 추가 확정 P1/P2는 발견되지 않았다.
- 공유 NAT의 33번째 신규 설치도 일시 429를 받을 수 있고 10,000개 IP 그룹의 동시 포화 및 다중 인스턴스 한도는 남는다. Notion 정본에는 아직 이 보호 상한을 반영하지 않았다.
