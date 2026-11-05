# 세션 종료 개발 오류 기록

## 정적 검사·테스트 enum 수정

- 최초 build에서 record 사이 빈 줄과 테스트 SQL의 101자 줄이 Checkstyle에 걸려 수정했다.
- 추가 실행 기기 테스트에서 공개 응답값 EXTENSION을 내부 DeviceType enum으로 사용해 컴파일이 실패했다. DB·인증 내부 값 CHROME_EXTENSION으로 수정했다. 공개 API 계약은 변경하지 않았다.
- 수정 후 H2 전체 build 및 분리 PostgreSQL 13개 테스트가 통과했다.
