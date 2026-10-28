# Extension 연결 해제 QA

2026-10-28: H2 환경 전체 Gradle build 및 Checkstyle 성공. Extension 본인 해제 후 새 계정 연결, 타 계정 해제 거절, 기존 access/refresh 폐기, 다른 Extension 유지 및 Extension 간 해제 거절을 검증했다. 실제 PostgreSQL과 실제 앱 통합 QA는 아직 수행하지 않았다. 기존 계정으로 기기를 조회하는 복구 화면은 아직 구현하지 않았다.

추가: 본인 Desktop의 ACTIVE 기기 목록과 다른 계정 기기 비노출, Extension의 기기 목록 접근 거부를 H2 테스트로 확인했다. 별도 Desktop 기기 관리 화면은 Frontend에서 구현해야 한다. `data.devices` 배열 래퍼는 Notion에 명시되지 않아 프론트 확인이 필요하다.

목록 조회 후 기존 Desktop이 해제하고 다른 계정이 같은 설치를 재연결하는 H2 통합 테스트 및 전체 Gradle build/Checkstyle을 통과했다. PostgreSQL과 실제 앱 QA는 별도다.
