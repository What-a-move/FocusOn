# 학습 세션 일시정지·재개 계획

- Issue: #132 / Branch: `feat/132-session-pause-resume`
- 범위: 본인 세션 일시정지·재개, 상태·버전·멱등성·권한과 누적 시간 검증
- 정본: Notion API 명세서의 pause·resume 항목, 세션 상태 전이 정책 3.2·4.1, `docs/API_CONTRACT.md`
- 일시정지 요청: `pauseReason`, `accumulatedActiveMs`; 성공: 상태·버전·누적 시간.
- 재개 요청: `readiness.screenAnalysisReady`; 성공: RUNNING·버전·lastResumedAt.
- RUNNING에서 사용자 일시정지 시 클라이언트 누적 값을 검증해 저장하고 마지막 재개 시각을 비운다. 재개 시 필수 조건을 다시 확인한다.
- 상태 변경과 멱등 기록은 하나의 트랜잭션에서 처리하고 기존 사용자 잠금 순서를 따른다.
- 사용자 결정: Notion API에 따라 `X-Device-Id`는 필수로 요구하지 않는다. 토큰의 Desktop 기기와 세션 소유권·실행 기기는 검증한다.
- Notion 재개 API는 준비 부족에 403을 명시하지만 기존 시작 API의 동일 오류 코드는 409다. 처리 범위와 기존 응답의 호환성을 확인한다.
- 사용자 결정: 일시정지 누적 시간은 현재 RUNNING 구간 시작 시 확정 누적값 + 서버 경과 시간 + 30초까지 허용한다. `APP_RESTART`는 서버의 마지막 확정 시간만 유지한다. 구간 시작 누적값은 V10에 보존하며 Heartbeat도 이 기준을 공유해야 한다.
- 제외: 연장·종료·Heartbeat 및 무신호 자동 종료.
- 검증 순서: 사용자 Swagger 테스트 후 PostgreSQL 동시성, Backend build·Checkstyle 및 문서 갱신.
- 사용자가 Swagger API 테스트를 완료했다고 알린 뒤 격리 PostgreSQL에서 V10 적용과 동시 일시정지·동일 키 동시 재개를 검증했다. DB 내부 잠금 대기 자체는 계측하지 않았다.
- 실제 로그인 Swagger에서 정상 일시정지·재개 및 버전·준비 조건·멱등 키·입력·없는 세션 오류를 확인했다. 자세한 범위는 REPORT를 따른다.
- 격리 PostgreSQL에 V9의 합성 세션 3건을 준비하고 Flyway V10 전후 기존 행 전체·행 수·새 기준값 보존을 확인했다.
