# 분석 요청 코드 리뷰

- Issue: #171 / 기준: 최신 dev 91cd524 대비 Server 변경
- 방식: server/docs/rules/code-review-isolation.md에 따라 구현 대화를 넘기지 않은 별도 에이전트가 diff·PLAN·컨벤션을 정적 검토했다.
- 결과: 확정 가능한 P1/P2 결함 없음.
- 확인: Bearer/기기/runner 소유권과 완료 시 재검증, 사용자 잠금·transaction 분리, 중복 제약, schema/FK/CHECK/index, Privacy Guard·원문 비저장, 내부 AI envelope·서비스 키·trace·응답 schema/크기/timeout·fallback·confidence, observedAt 순서·지연 결과·pause/resume/unlink 경계.
- 한계: 리뷰자는 테스트나 PostgreSQL·실제 AI를 실행하지 않았다. 테스트 결과는 QA 문서에 별도로 기록한다. 후속 추가 테스트와 CI 필터는 구현자가 재확인한다.
- 실제 AI 판정 통합은 #154의 정식 경로가 판정을 반환한 뒤 추가 QA가 필요하다.

- 본문 stall 재현 뒤 변경한 비동기 전체5초 제한·미완료 요청 cancel·응답8KB 상한도 같은 격리 에이전트가 재검토했다. 확정 P1/P2는 없고 실제 socket 자원 해제 계측은 수행하지 않았다.

## PR #172 CodeRabbit 후속 검토

- 위 P1/P2 없음은 최초 격리 정적 리뷰 결과다. 이후 CodeRabbit은 CHROME_DOM host 누락에 따른 제외 도메인 검사 우회를 지적했다. 실제 코드와 대조해 유효한 결함으로 판단하고 저장·AI 호출 전400 차단과 회귀를 추가했다.
- 과대 응답 테스트의 유효 JSON 사용, timeout 후 cleanup 전 실제 연결 종료 확인 지적도 반영한다. 기존 테스트가 이 보호 로직의 제거까지 검출했다고 주장하지 않는다.
- 복잡한 검증·인증 재확인·AI envelope 구성 helper에는 이유와 경계를 설명하는 Javadoc을 추가했다. CodeRabbit 자체 coverage 재산정은 재리뷰 결과로 확인한다.

- 이번 리뷰 수정 diff도 별도 컨텍스트에서 재검토했다. host 없는 본문 차단·보호 상태 보존·유효 JSON 과대응답·cleanup 전 연결 종료 검증에서 P1/P2 없음. 리뷰자는 테스트를 실행하지 않았다.

## 외부 P2 host·고정 시차 후속

- TAB_META host 누락과 기기 시각/서버 시각 직접 비교 지적은 실제 코드에서 확인해 수정했다.
- 이번 diff의 별도 정적 재검토는 TAB_META 차단과 AI 중 실행 구간 변경 검사에 추가 P1/P2 없음으로 보고했다.
- 다만 기기 시계가 실행 도중 뒤로 보정되면 기존 observedAt 최신 순서가 막힐 수 있다는 추가 P2가 나왔다. 최신 검토가 전체 P1/P2 없음이라는 뜻은 아니다. 현 계약에서의 한계와 별도 관측 순서 계약 검토를 PLAN/QA에 기록한다.

## 전역 관측 순서 보완 최종 정적 리뷰

- 사용자 위임 후 새 PLAN·diff만 전달한 별도 컨텍스트에서 observationSequence, 번호 모드/downgrade/작은 번호, 구형 순서 불명/보호 유지, V15를 검토했다.
- 확정 가능한 P1/P2 없음. 선택 번호의 범위와 session/device scope·지연 완료의 자기 이력 갱신·nullable 업그레이드가 기획과 일치한다.
- 리뷰자는 테스트·외부 댓글·수정을 수행하지 않았다. 실제 AI/Frontend 적용을 검증했다는 의미가 아니다. 앞의 시계 보정 P2는 이 설계와 검증으로 보완됐다.
