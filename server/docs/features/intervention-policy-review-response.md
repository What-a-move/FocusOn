# PR205 코드리뷰 피드백 처리

기준: CodeRabbit의7ab8f6e 리뷰(actionable9건·테스트 제안1건). 각 지적을 실제 코드/계약과 대조했고 모두 유효해 아래처럼 보완했다. 공개 API/확정 정책 변경은 없으며 최종 봇 재검토 완료를 뜻하지 않는다.

| 리뷰 | 확인된 문제 | 보완 | 검증 근거 |
| --- | --- | --- | --- |
| [리뷰4509099353](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099353) | AI 인계의 0.75 수정 요청 | AI0.6/#190 병합 완료를 현재 상태로 명시하고 이전 요청은 과거 이력으로 분리 | handoff 현재/이력 대조 |
| [리뷰4509099354](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099354) | 이슈 문서의 빈 adapter/receipt 미구현 표기 | 서버 context/receipt 구현·정본 완료와 남은 소비자/실표시 QA를 분리 | issue 기록 현재 상태/이력 분리 |
| [리뷰4509099358](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099358) | NEXT_TASK의 PR 생성 승인 재요청 | PR205 생성·push 완료를 명시하고 현재 작업을 리뷰 후속 검증으로 교체 | NEXT_TASK 현재/과거 구분 |
| [리뷰4509099362](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099362) | context 없는 높은 순번이 보호 run의 정책 처리를 막음 | policyCurrent와 AI latest를 context 소속 findCurrent로 선택 | contextProtectedRunIsCurrentEvenAfterHigherUnboundObservation |
| [리뷰4509099363](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099363) | AFTER_COMMIT에서 부모 connection 보유 중 REQUIRES_NEW 대기 | TransactionTemplate.execute 반환/cleanup 뒤 신호를 발행해 별도 평가 | heartbeatEvaluationWorksAfterCleanupWithSingleConnectionPool |
| [리뷰4509099368](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099368) | 일반 재분석 prepare가 후보를 만료 | 후보 유효성/표시 가능성을 분리해 보존·표시 동결. NOT_SENT 후보는 TTL 안 새 후보 대신 같은 후보 재시도 | ordinaryReanalysisHidesButPreservesCandidateAndNotSentRetry |
| [리뷰4509099371](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099371) | heartbeat 사이 철회/재동의 시 중단 구간 유실 | 동의 저장 transaction의 사용자→세션 잠금 아래 즉시 interrupt/후보 만료 | withdrawalAndRegrantBetweenHeartbeatsCannotAccrueOrRestoreCandidates (필수 동의3종, 후보 유/무) |
| [리뷰4509099374](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099374) | A의 늦은 SENT 뒤 B를 cooldown 중 표시 | candidateState에서 활성 cooldown의 표시를 차단 | lateSentForEarlierEventSuppressesNewPendingDuringCooldown |
| [리뷰4509099378](https://github.com/What-a-move/FocusOn/pull/205#discussion_r4509099378) | cooldown 전에 접수된 분석이 늦은 완료로 새 관측 취급 | snapshot.latestReceivedAt에 실제 서버 접수 시각을 저장해 freshness를 판단 | observationReceivedBeforeCooldownCannotQualifyByCompletingAfterward |

- 추가 테스트 제안: 실행 세대 변경 없이 필수 동의3종 각각의 철회/재동의를 검증했다. 선택 CAMERA_ANALYSIS 철회는 화면 후보를 끊지 않는 경계도 별도로 검증했다.
- 독립 리뷰: 구현 대화 없는 새 리뷰 에이전트가 diff/계약/신규 listener/회귀를 읽었고 추가 actionable finding 없음. 리뷰자는 코드를 수정하거나 테스트를 실행하지 않았다.
- 실행 결과: 전체 build·Checkstyle279개 실패0·오류0·생략14. CI와 같은 Flyway+validate 및 정확한 원격 목록의 격리 PostgreSQL152개 실패0·생략0. 원격 결과는 최종 push의 체크에서 별도로 확인한다.
- 실제 AI/학교 모델·Desktop/Extension/shared-types 연결·포키 실표시는 미완료다. 기존 개발 DB5432/8080·IDE staged/stash는 보존한다. PR 병합·Issue 종료는 별도 승인이다.
