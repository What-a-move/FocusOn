# #182 오류·보완 기록

1. Notion 화면 분석 5.2 rich text 입력 중 문장 순서가 뒤섞였다. 후속 교체는 자동 승인 심사로 차단됐다. 사용자에게 정확한 복구 범위를 설명하고 원문 복구만 승인받았으며 UI에서 원문 복구를 확인했다. 새 정책은 Notion에 추가하지 않았다.
2. 격리 리뷰 P1: 순서 불명 관측의 정책 전이가 누락됐다. 정책 current/AI latest를 분리하고 복구 시 새 episode를 검증했다. 독립 재검토 완료.
3. 실제 PostgreSQL에서 pending_slot=NULL이 CHECK의 UNKNOWN으로 통과했다. 신규 미배포 V16에 IS NOT NULL을 보강하고 실제 제약 회귀로 검증한다. 기존 dev V1~V15는 수정하지 않았다.
4. PG 테스트용 JDBC Instant 바인딩 2건 실패는 Timestamp.from으로 보완했다.
5. 초기 Checkstyle 줄 길이·import 순서·record 생성자 Javadoc 지적을 보완했다. 최종 결과는 QA 문서를 따른다.

원문·토큰·사용자 데이터·DB 자격 증명은 이 기록에 포함하지 않는다.
