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
