# #182 오류·보완 기록

1. Notion 화면 분석 5.2 rich text 입력 중 문장 순서가 뒤섞였다. 후속 교체는 자동 승인 심사로 차단됐다. 사용자에게 정확한 복구 범위를 설명하고 원문 복구만 승인받았으며 UI에서 원문 복구를 확인했다. 새 정책은 Notion에 추가하지 않았다.
2. 격리 리뷰 P1: 순서 불명 관측의 정책 전이가 누락됐다. 정책 current/AI latest를 분리하고 복구 시 새 episode를 검증했다. 독립 재검토 완료.
3. 실제 PostgreSQL에서 pending_slot=NULL이 CHECK의 UNKNOWN으로 통과했다. 신규 미배포 V16에 IS NOT NULL을 보강하고 실제 제약 회귀로 검증한다. 기존 dev V1~V15는 수정하지 않았다.
4. PG 테스트용 JDBC Instant 바인딩 2건 실패는 Timestamp.from으로 보완했다.
5. 초기 Checkstyle 줄 길이·import 순서·record 생성자 Javadoc 지적을 보완했다. 최종 결과는 QA 문서를 따른다.

원문·토큰·사용자 데이터·DB 자격 증명은 이 기록에 포함하지 않는다.

## 실제 알림 연결 확장 중 발견/보완

- dev #194의 V16 세션 연장과 미배포 개입 migration 번호가 충돌했다. 최신 dev를 통합하고 개입 V17·연결 V18로 분리했다. 배포된 V1~V16은 변경하지 않았다.
- 격리 리뷰 P2: context 없는 호환 관측의 현재 정책 간섭. context 범위 findCurrent와 이력 전용 경계를 보강하고 회귀/재검토했다.
- 첫 PostgreSQL 확장 QA에서 신규 테스트 fixture의 JDBC Instant 바인딩 실패9건과 안전 컬럼 허용 목록 누락1건이 발생했다. Timestamp.from과 observation_context_id 허용 목록을 보강했다. 운영 코드를 추측 변경하지 않았다. 수정 후129개 전부 통과.
- 최신 dev0ca256e 통합 후 전체 검사 중 기존 실제 HTTP CORS preflight가1회404였다. 단독 재실행은 성공했으며 원인은 미확정이다. 코드/보안 설정은 변경하지 않았다. 전체267개 재실행 및 최종 PostgreSQL131개는 통과했으며 원인은 여전히 미확정이다. 최종 결과는 QA를 따른다.

## PR205 원격 CI의 시각 정밀도 보완

- cb2d530 원격 전체 build에서 동시 ack의 최초 응답/재조회 응답 동등성 및 lease 종료 시각 assertion이 실패했다. 로컬 Mac 전체/PG는 통과했지만 원격 실패를 숨기지 않는다.
- ack 최초 Instant를 PostgreSQL 마이크로초로 고정해 첫 응답과 DB 재조회 시각을 일치시킨다. lease 테스트의 JDBC 입력도 처음부터 마이크로초로 고정해 DB 반올림 차이를 제거한다.
- 원격 PostgreSQL job의 기존 검사 범위에 신규 intervention/heartbeat/SessionApi 회귀를 추가한다. job 이름/DB 설정/권한은 유지한다. 최종 재검증 결과는 PR/QA를 따른다.

## CodeRabbit 실제 경계 피드백 보완

- 일반 prepare가 후보를 무조건 만료해 receipt 미확인 보류로 이후 알림도 막을 수 있었다. 일반 pending은 보존/표시 동결, TTL/확정 보호에서만 만료하도록 분리했다. NOT_SENT 후보가 남아 있으면 같은 후보만 재시도한다.
- context 없는 높은 관측 번호가 context 안의 늦은 보호 관측 처리를 막는 선택 경계를 수정했다. 실제 서버 receivedAt과 완료 시각을 분리하고 늦은 receipt의 cooldown 표시도 차단했다.
- heartbeat 부모 transaction 정리 전에 새 transaction이 연결을 기다리는 경계를 execute 반환 이후로 이동했다. max pool1에서 실제 평가 성공을 검증했다. 필수 동의 철회/재동의도 같은 user/session 잠금에서 중단 이력을 보존한다. 선택 카메라 철회는 화면 정책에 영향을 주지 않는다.
- 전체279개 실패0·생략14, 격리 Flyway+validate PostgreSQL152개 모두 통과. 개인정보/원문·키는 기록하지 않았다.

## 병합 전 추가2건

동의 철회가 지운 pending을 완료 단계의 fallback이 다시 만들어 episode를 열 수 있었다. 완료는 동일 pending과 일치할 때만 반영한다. 이전 분석 완료 차단/새 관측 허용을 필수 동의3종에 검증했다. 이미 받은 GET은 늦은 receipt로 취소할 수 없으므로 소비자 직렬 coordinator/outbox barrier가 필수임도 명시했다. 서버 alone/GET 재조회 alone으로 표시 경쟁이 해결됐다고 주장하지 않는다.
