# 확장 접근 정책 QA (2026-11-08)

## 검증 결과

- 기준: 최신 dev 71247cc + #151 변경분. 기본 H2 전체 build·Checkstyle 통과:135 tests/failures0/errors0/skipped4(기존 PG 조건부 테스트).
- 분리 PostgreSQL17(전용 컨테이너·55441) Flyway V1~V11·Hibernate validate 및 PairingApiTests25개 + ExtensionCorsTests5개 총30개 통과(failures0/errors0/skipped0).
- 실제 루프백 HTTP에서 허용 origin preflight200·Access-Control-Allow-Origin, 토큰 없는 실제GET401 확인. MockMvc로 허용된 전체 요청헤더·Retry-After 노출·불허origin/PATCH/미허용헤더403 및 일반OPTIONS401 검증.
- 설정의 wildcard/null/웹origin/확장origin trailing slash 거절을 확인했다. local은 개발ID 기본, docker는 env 미지정 시 불허다.
- 새 설치 연결 시 기존 device/family/refresh 폐기, REPLACED 사유, 이전 Access에 LINK_TOKEN_REVOKED, 새 토큰 유효, 이전 Refresh 거절, 같은 설치 deviceId 재사용, 타 사용자 격리를 검증했다.
- 같은 사용자·다른 Desktop에서 발급한 두 코드로 동시 연결해 최종 유효 토큰·ACTIVE 확장 각각1개를 확인했다. 실제 DB lock 대기 시간은 계측하지 않았다.

## 기존 데이터 마이그레이션

- 별도 QA DB에서 V1~V10 SQL을 순서 적용하고 합성 사용자2명·기기5개·family3개·refresh3개를 넣었다. 사용자1의 확장2개 중 최신 연결 하나, 사용자2의 확장1개를 준비했다.
- V11을 단일transaction으로 적용해 기기행5개 보존, 이전 확장REVOKED·family REPLACED·활성refresh REVOKED, 최신 확장과 다른계정 확장ACTIVE 유지 확인.
- 이전 기기를 다시ACTIVE로 바꾸려는 시도가 unique_violation으로 거절됨을 확인했다. 이 경로는 직접 SQL 업그레이드이며 Flyway 히스토리 업그레이드 테스트로 표현하지 않는다. 빈 DB 전체 Flyway 적용은 위 통합테스트에서 별도로 검증했다.
- 기존 데이터가 여러 연결을 가진 경우 최신ACTIVE family 생성시각(없으면device 생성시각), id 순으로1개만 유지한다. 나머지 연결은 다음 인증부터 끊긴다. 되돌리기는 index삭제만으로 복구되지 않으므로 운영 적용 전 백업 및 영향계정 확인이 필요하다.

## 남은 검증

- 실제 Chrome 사이트 접근 권한을 끈 뒤 요청 전송과 팝업 동작, local-events에 의한 자동 멈춤은 미검증이다. CORS만으로 Chrome host permission 제약을 우회할 수 있다고 주장하지 않는다.
- 사용자 8080 서버·개발 DB에는 새 코드를 적용하지 않았다. 테스트용 컨테이너는 종료했다.
- Google은 합성 대체다. 실제 앱 OAuth·독립리뷰·CodeRabbit·새PR CI는 미실행이며, 사용자 제공 정책을 적용했고 Notion 최신 원문은 이번 턴에 재확인하지 않았다.
