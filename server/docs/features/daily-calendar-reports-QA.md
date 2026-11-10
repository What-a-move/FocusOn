# 일간·달력 리포트 QA

2026-11-10. 브랜치 `feat/208-daily-calendar-reports`, 기반 dev `b3d680d`.

## 검증 결과

- 신규 MockMvc HTTP4개: 실제 발급 Bearer와 DB family/기기 검증, 타 사용자 격리, 공통 응답 필드, invalid/missing query·필수 기기 헤더·다른 기기 위장·익명·연결 Extension403·윤년 빈날짜200.
- 신규 Repository/Service5개: 모든 최종/비최종 상태 필터, 고정 studyDate와 자정 통과, 목표 변경/삭제 뒤 snapshot 유지, long 합계, 빈 범위, 활동일60000ms 경계·짧은 세션 합산·0ms 세션 건수·월말/윤년·동일시작시각 ID 정렬.
- PostgreSQL 전용 Migration1개: 고유 임시 schema에서 V1~V20 적용, 실제 종료 세션 생성, V21 적용 뒤 전체 세션 JSON 보존과 인덱스 존재 확인.
- PostgreSQL17.11-alpine3.24 임시 컨테이너·임의 loopback 포트에서 리포트10개 실패0·오류0·생략0. Flyway V1~V21 및 Hibernate validate 사용. 컨테이너 제거 완료. 로그 `server/build/report208-postgres-final.log`.
- 전체 build/Checkstyle의 최초 실패는 구현된 달력을 미구현 경로로 사용하던 기존 테스트 문제였고 수정했다. 최종 전체 수치는 아래 갱신한다. 로그 `server/build/report208-build-final.log`.

## 범위와 남은 항목

MockMvc는 실제 네트워크 소켓 및 Electron 테스트가 아니다. 실제 Desktop 로그인·네트워크 요청·화면 확인은 미실행이다. AI 모델 연결은 범위 밖이다. Notion 두 정본은404로 최신 대조/동기화가 남았다. 사용자 승인 기준과 Desktop 소비자 파서는 정적으로 대조했다. 별도 컨텍스트 리뷰·원격 CI/PR 봇 리뷰도 미실행이다.

사용자 DB5432/기존8080·IDE 파일·stash는 초기화/중지/커밋하지 않았다. 테스트 전용 DB/schema만 생성·삭제했다.

## 최종 전체 검증

`./server/gradlew -p server build` 성공. JUnit XML 합계315개 중 실패0·오류0·조건부생략17(일반298개 통과). PostgreSQL 전용 업그레이드 테스트는 H2에서는 생략되고 격리 PostgreSQL에서는 통과했다. Main/Test Checkstyle도 통과했다. XML 합계는 Gradle 콘솔의 container 생략 표시와 다를 수 있다.
