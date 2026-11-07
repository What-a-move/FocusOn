# 확장 접근 정책 개발 오류

- 동시 연결 테스트 lambda의 한 줄 표현과 실제 HTTP 테스트 annotation 줄 길이가 Checkstyle에 걸렸다. 문장 분리·줄바꿈으로 수정한 뒤 전체 build·Checkstyle을 통과했다.
- 기존 동일 계정 확장 2개를 유지하는 테스트는 새 정책과 충돌했다. 다른 계정 확장을 유지하는 소유권 테스트로 조정하고, 동일 계정 교체·동시 연결 테스트를 별도로 추가했다.
- 사용자 중단으로 새 HTTP 테스트의 최종 검증이 끊겨 재개 후 H2 전체와 분리 PostgreSQL 검증을 순차 완료했다.

## 리뷰: 제약 검증의 강한 잠금 유지

- NOT VALID 없이 제약을 추가하면 기존 token_families를 V11의 강한 잠금 transaction에서 스캔한다.
- V11의 check를 NOT VALID로 추가하고 새 V12에서 VALIDATE하도록 분리했다. SQL fixture에서 false→true 제약 상태와 ShareUpdateExclusiveLock, 열린 transaction 중 다른 연결 UPDATE 성공을 확인했다.

## P2: 만료된 최신 확장을 보존하는 선택 오류

- 원인: ACTIVE family 생성시각만 비교해 expires_at과 연결 Desktop의 인증 조건을 놓쳤다. 이전 유효 연결을 폐기하고 만료 연결만 남길 수 있었다.
- 수정: TokenFamilyGuard의 인증 가능 조건을 순위 첫 기준으로 반영했다. 같은 인증 가능 그룹에서만 기존 시각·ID 정렬을 사용한다. 모두 인증 불가이면 최신 하나를 남기며 토큰의 수명·상태를 되살리지는 않는다.
- 회귀 테스트는 전용 임시 schema에서 V10을 만든 후 9가지 fixture를 넣고 실제 Flyway V11/V12를 적용한다. DB 상태·기기/family/refresh 행 수와 다른 계정 보존을 함께 검사한다.
- 테스트 정적 검사에서 snapshot 변수 사용거리 경고2건을 final로 수정했다.
