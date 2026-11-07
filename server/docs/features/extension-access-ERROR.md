# 확장 접근 정책 개발 오류

- 동시 연결 테스트 lambda의 한 줄 표현과 실제 HTTP 테스트 annotation 줄 길이가 Checkstyle에 걸렸다. 문장 분리·줄바꿈으로 수정한 뒤 전체 build·Checkstyle을 통과했다.
- 기존 동일 계정 확장 2개를 유지하는 테스트는 새 정책과 충돌했다. 다른 계정 확장을 유지하는 소유권 테스트로 조정하고, 동일 계정 교체·동시 연결 테스트를 별도로 추가했다.
- 사용자 중단으로 새 HTTP 테스트의 최종 검증이 끊겨 재개 후 H2 전체와 분리 PostgreSQL 검증을 순차 완료했다.

## 리뷰: 제약 검증의 강한 잠금 유지

- NOT VALID 없이 제약을 추가하면 기존 token_families를 V11의 강한 잠금 transaction에서 스캔한다.
- V11의 check를 NOT VALID로 추가하고 새 V12에서 VALIDATE하도록 분리했다. SQL fixture에서 false→true 제약 상태와 ShareUpdateExclusiveLock, 열린 transaction 중 다른 연결 UPDATE 성공을 확인했다.
