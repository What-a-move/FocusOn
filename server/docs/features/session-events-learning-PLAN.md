# 로컬 이벤트·학습 정리 조회 계획

- Issue #206, feat/206-session-events-learning, 기준dev6a05692. Server, MVP 핵심. POST /api/v1/sessions/{sessionId}/local-events, GET /api/v1/sessions/{sessionId}/learning-summary.
- 기존 Desktop/Extension 요청 계약의 localEventCode·occurredAt과 Authorization·X-Device-Id를 검증한다.
- 화면 권한 철회(Desktop) 및 host 권한 유실(연결 Extension)을 저장하고 RUNNING만 AUTO_PAUSED로 전환한다. 저장된 마지막 확정 누적값 유지, version증가. 다른 멈춤 상태는 그대로 유지하며 terminal 상태의 새 이벤트는409.
- 사용자 행→세션 행 순으로 잠그고 token family/연결 Desktop/runner를 재확인한다. 동일 session/device/code/occurredAt의 재전송은 이력1건이며 재개 후에도 다시 멈추지 않는다.
- occurredAt은 기기 시각이므로 서버 시각과 신선도를 비교하지 않는다. 중복 판단에는 원래 문자열의 정규화 시각을 해시하여 DB microsecond 반올림 충돌을 방지한다. 전송세대 정보가 없는 서로 다른 지연 이벤트는 구분할 수 없는 제한을 기록한다.
- learning_summaries에 5개 상태와 공개 정리 결과를 저장할 스키마를 제공하고 본인 Desktop 조회를 구현한다. 행이 없으면404 NOT_FOUND, 존재 시 5개 상태를 실제값으로 반환한다.
- 자동 정리 생성/근거 수집/AI 호출은 기존 #29 선행 작업이다. 현재 AI에 제품용 생성 endpoint가 없어 이번 두 API의 범위에 추가하지 않는다. 조회를 임의 READY/INSUFFICIENT_DATA로 위장하지 않는다.
- V19 후속 migration(최신dev V18 뒤), 기존 데이터 무변경. JSON 출력은 최대5개 핵심개념·1200자 제한, 세션당1개 및 상태/내용 CHECK.
- 테스트: 헤더/인증/권한/기기/상태/동시 이벤트·resume replay/5상태와 실계정HTTP·PostgreSQL·build/Checkstyle. 원본DB와 기존8080 보존.
