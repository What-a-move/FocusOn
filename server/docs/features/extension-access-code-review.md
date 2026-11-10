# 확장 접근 정책 자체 점검

- 전체 diff·PLAN·기존 Security/Pairing/Refresh 흐름을 대조했다. 별도 에이전트 또는 CodeRabbit 독립 리뷰를 받았다는 의미는 아니다.
- CorsFilter가 인증 필터보다 먼저 정상 preflight를 처리한다. 실제 API 인증과 일반 OPTIONS 인증은 유지하며 wildcard·credentials를 열지 않는다.
- 설치 잠금→사용자 잠금 순서를 보존했다. 다른 설치의 토큰·기기를 같은 트랜잭션에서 폐기하고 flush한 뒤 새 기기를 활성화해 partial unique를 지킨다. refresh/logout의 사용자→family 잠금 순서와 역전하지 않는다.
- 다른 계정 소유 설치는 기존 LINK007로 거절하며, 다른 사용자의 연결에는 영향을 주지 않는다. 같은 설치는 기존 deviceId 및 이전 family의 UNLINK 폐기 동작을 유지한다.
- V11은 기기/family/refresh 행을 삭제하지 않는다. 기존 연결 폐기에 따른 세션 자동 멈춤은 별도 5번 담당 흐름이며 이번 변경이 구현 완료를 뜻하지 않는다.
- 자체 점검에서 추가 확정 문제는 찾지 못했다. 독립 리뷰와 실제 Chrome QA는 남아 있다.

## CodeRabbit 리뷰 #170 (74f4375 기준)

- 실제독립리뷰 지적: V11의 check 제약 전체검증이 ACCESS EXCLUSIVE transaction 안에서 수행됨. V11 NOT VALID 및 새 V12 VALIDATE로 수정했다. 제약 스캔의 잠금은 줄지만 V11의 정리/index 생성 잠금은 남는다.
- 개선제안: pairing/refresh POST preflight 테스트 부족. 각 경로의 허용origin·메서드·헤더 검증2개 추가.
- 주석 비율은 경고이며 기능오류와 구분한다. 변경한 주요 함수와 테스트의 의도 설명을 추가했다. 최신 수정 커밋의 재리뷰는 별도 확인한다.

## 외부 P2: 인증 불가 최신 연결 보존

- family가 ACTIVE여도 만료 또는 Desktop 연결 유실로 인증 불가일 수 있다는 지적을 확인했다. isActive 및 hasActiveLinkedDesktop의 사용자·기기·family·Desktop·부모 family 조건을 SQL 순위의 최우선 CASE로 반영했다.
- family 상태만이 아닌 expires_at, Desktop 유형·활성·소유권, 부모 family의 ACTIVE·소유권·수명을 판정한다. 삭제 사용자도 인증 불가다.
- 모두 유효한 경우에는 최신 연결, 모두 무효인 경우에는 최신 이력1개를 유지한다. 무효 연결을 활성 토큰으로 되살리지 않는다.
