# 로그인 수명주기 독립 코드 리뷰

- 날짜: 2026-10-26 / Issue #59
- 방식: 구현 대화 없이 origin/dev 대비 Server diff·PLAN·공통 계약·개발 규칙만 전달한 별도 에이전트 정적 리뷰.
- 범위: refresh 원자성·reuse 폐기 commit·logout cascade·잠금 순서·재로그인 경쟁·기기 소유권·부모 Desktop 로그인·공개 한도 격리·필터·민감값.

## 결과

P1/P2로 보고할 실제 결함 없음. Critical/High/Medium/Low의 별도 actionable 지적 없음.

- 잠금 전 projection만 조회하고 user → family 잠금 뒤 token 상태를 다시 읽는다.
- ROTATED flush 후 새 ACTIVE record·family 만료 연장·JWT 발급을 같은 transaction에서 처리한다.
- reuse 폐기를 commit한 다음 transaction 밖에서 401로 변환하므로 현재 HTTP 경로에서 폐기가 rollback되지 않는다.
- logout은 기존 principal family를 잠금 뒤 재검증하며 직접 연결된 같은 사용자 Extension만 폐기한다.
- refresh는 Bearer 필터를 우회하고 body token으로 인증하며, logout은 검증된 Desktop Bearer와 204 빈 본문을 사용한다.
- 공개 refresh IP 한도 공간은 인증 사용자 한도와 분리된다.
- DTO 문자열과 오류 응답에서 원문 token을 노출하는 경로를 확인하지 못했다.

리뷰어는 코드 수정·테스트 실행·외부 댓글을 하지 않았다. 동시성 판단은 정적 검토 결과이며 실행 증거는 별도 QA에 기록한다. 실제 Google 사용자 왕복 성공을 이 리뷰로 대신하지 않는다.
