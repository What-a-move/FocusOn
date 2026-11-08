# #182 서버 QA

- 일자: 2026-11-09
- 기준: `feat/182-server-intervention-policy`, origin/dev `5462afd` 기반.
- 실제 AI·Desktop 포키 표시 QA가 아닌 서버 합성 입력/인증/DB/HTTP 검증이다.

## 최종 실행 결과

| 검증 | 결과 |
| --- | --- |
| `./server/gradlew -p server build` | 성공. 전체 226개, 실패 0, 오류 0, 생략 10. Checkstyle main/test 통과 |
| 격리 PostgreSQL 17.11 + Flyway/validate | 선택 회귀 87개, 실패 0, 오류 0, 생략 0. Checkstyle main/test 통과 |
| 임의 HTTP 포트에서 개입 GET/ack POST | 실제 HTTP 200·data 배열·문자열 ID·DISMISSED 확인. MockMvc 정상/오류도 별도 검증 |
| V15→V16 | 기존 세션 누적 보존, FK/type/ack timestamp/pending NULL/원인·slot UNIQUE 제약 검증 |
| 격리 리뷰 | P1 1건 보완 후 독립 재검토, 추가 actionable finding 없음 |

PG 실행 대상: intervention 전체 + AnalysisApiTests + AnalysisMigrationTests + SessionHeartbeatCleanupTests. 전용 disposable 컨테이너 `focuson-182-qa`, loopback 51734와 독립 QA DB만 사용했다. 개발 DB5432/기존8080 서버는 중지·초기화하지 않았다. 테스트 서버는 RANDOM_PORT를 사용했다.

## 정상/오류 및 경쟁

- 같은 실행/context에서 25초마다 재분석해도 episode ID/확정 누적 유지. pending 동안 시간/후보 동결, 대기 시간 비소급, GENTLE 180초 초과 도달.
- 각 강도 연속 조건 직전/동일/초과, rolling clipping·열린 구간 1회, metadata UNRELATED 제외.
- 확정 UNCERTAIN/RELATED/EXCLUDED/PRIVACY_BLOCKED는 현재 episode 종료. 늦은 완료는 최신 보호를 덮지 않음.
- 실제 AnalysisRunStore prepare/complete 연결에서 pending 보존, 최신 보호·구형 legacy 순서 불명 이후 번호 복구의 새 episode 검증.
- 서버 시계 역행·snapshot 복구 시 대기 시간 가산/잘못된 재개 없음.
- 동시 heartbeat 생성 1회, 동시/반대 ack 첫 결정/actedAt 유지.
- 조회는 전송/사용자 반응 시각을 만들지 않음. 30초 만료·EXPIRED 늦은 ack null actedAt·전송 미확인 재생성 금지.
- 인증 없음/타인 세션·개입/기기 헤더 없음/미정 DISPLAYED action 거절. 원문 응답 없음.
- 실행 세대 변경 뒤 PENDING 표시 차단, 정책 평가 실패 뒤에도 heartbeat의 유효 누적/생존 commit 보존.
- 기본 foreground adapter 없음은 추측 생성/정책 상태 생성을 하지 않고 빈 배열 반환.

## 남은 제한

- 전경 context/소속/유효성 및 전송 receipt 계약·소비자 구현 없음. production 생성은 보류가 기본이다. #182만으로 실제 앱 알림이 동작한다고 주장하지 않는다.
- #154 실제 AI 응답 연결 및 팀원 종료/연장 API·향후 리포트 통합 QA는 별도다.
- Notion 원문 복구는 확인했으나 사용자 승인된 rolling/만료 신규 정책 정본 반영은 미완료다. 개입 미존재 404 code/EXPIRED actedAt null 상세도 최종 정본 대조가 필요하다.
- PR 생성·원격 CI·봇 리뷰·병합·Issue 종료를 수행하지 않았다.
