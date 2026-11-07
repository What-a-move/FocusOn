# 화면 분석 요청 구현 결과 (#171)

2026-11-08, feat/171-analysis-runs. 기준 dev91cd524. 다음 기능은 POST /api/v1/sessions/{sessionId}/analysis-runs 한 개다.

## 변경

- 공개 계약·기기/세션 소유권·runner/연결 Extension·동의·제외 도메인·guard를 검증한다. clientRunId 중복409와 세션+기기8회/분 제한을 적용한다.
- V13 analysis_runs에 안전한 결과만 저장한다. AI 입력·제목·URL·traceId는 영속화하지 않는다. 추가 내부 컬럼과 보관 기준은 PLAN/DECISION_RECORD에 명시했다.
- AI 전에 안전 UNCERTAIN 이력을 예약하고 DB transaction 밖에서 고정 내부 서비스에 호출한다. 완료 시 권한·실행 구간을 다시 확인한다. 기기별 source 무관 observedAt 최신 조회로 지연 결과 역전을 방지한다.
- AI_ANALYSIS_URL/서비스 키 미설정·장애·receipt·schema 오류는200 UNCERTAIN/ANALYSIS_UNAVAILABLE다. 최신 Notion의 베타 confidence 예외도 반영한다. 본문 stall까지5초·응답8KB로 제한하고 취소한다.
- Swagger에 공개 API가 노출되고 .env.example/Compose에 내부 AI 주소·공유 키 전달을 추가했다. 실제 .env는 변경하지 않았다.
- Backend CI의 기존 PostgreSQL job 이름은 유지하고 분석14개·migration9개를 실행 선택에 추가했다.

## 검증

- Backend build/Checkstyle:160개 실패0/오류0/생략6.
- PostgreSQL:34개 전부 통과(분석15+분석 migration9+확장 migration9+닉네임1).
- 격리 정적 리뷰와 timeout 보완 재리뷰에서 확정 P1/P2 없음. 상세는 QA·code-review·ERROR 문서다.

## 상태와 한계

- Server 구현·로컬 QA 완료. PR #172 생성 후 초기 head의 원격 CI2개·CodeRabbit 성공을 확인했고, 리뷰의 host 검증1건·테스트 보강2건을 반영했다. 수정 커밋의 원격 CI·재리뷰는 push 후 확인하며 병합/이슈 종료는 하지 않았다.
- 현재 dev AI는 정식 경로에서 판정 대신 수신 확인만 반환한다. 실제 AI 성공은 #154 후 연동 QA가 필요하다. 항상 RELATED인 가짜 성공으로 대체하지 않았다.
- 개입 생성·조회·확인, heartbeat Policy Engine, 리포트·90일 삭제 worker는 이 Issue에 포함하지 않았다. 팀원 세션 명령·Frontend·AI·Notion은 수정하지 않았다.
- 임시 PG는 QA 후 중지하고 기존 개발 DB/8080은 그대로 둔다. 다음 개발자는 frontend-handoff와 NEXT_TASK를 참고한다.
