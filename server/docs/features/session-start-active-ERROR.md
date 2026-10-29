# 세션 시작·활성 조회 오류 기록

- 관련 Issue: [#100](https://github.com/What-a-move/FocusOn/issues/100)
- 상태: 시작·조회 API 구현 및 검증 중.
- 최초 테스트 실행 시 테스트 파일 두 줄이 Checkstyle 길이 제한을 초과했다. 줄을 나눈 후 해당 테스트와 Checkstyle이 통과했다.
- 기존 #87 병합은 사용자 요청으로 취소했고 변경은 별도 stash로 보존했다. #100 작업도 이동 전 stash가 남아 있으므로 삭제하지 않는다.
- 실제 Google 로그인과 연결된 Extension을 이용한 서버 HTTP 검증은 아직 수행하지 않았다. PostgreSQL 동시 요청 및 모의 준비 상태로 루프백 HTTP 검증을 수행했다.
- PostgreSQL에 `CREATE_SESSION`만 허용하는 제약과 코드의 `SESSION_START` 값이 서로 달라 저장 시 실패할 수 있었다. 값을 `CREATE_SESSION`으로 통일하고 PostgreSQL에서 저장·재시도를 확인했다.
- 소수점 목표 시간이 정수로 변환되어 통과할 가능성을 막기 위해 요청 DTO에서 정수 자릿수 검증을 추가했다.
- 두 Gradle 검증을 동시에 실행해 결과 파일 충돌이 발생했다. 한 번에 하나씩 다시 실행해 PostgreSQL 세션 테스트를 통과시켰다.
- 카메라 동의는 서버 DB에서 확인하지만 OS 카메라 권한을 서버에서 확인할 수 있는 입력·저장 계약은 아직 확인하지 못했다. 카메라 사용 활성화의 권한 정책 확인이 필요하다.
