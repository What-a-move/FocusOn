# 분석 요청 연동 인계 (#171)

## 이번 Server 작업

POST /api/v1/sessions/{sessionId}/analysis-runs를 기존 클라이언트 필드명으로 구현했다. Bearer와 X-Device-Id가 필요하고 X-Trace-Id는 계속 clientRunId를 사용한다. 새 Frontend 요청 필드는 없다. PR #172 리뷰 중이며 dev 병합 전이다.

- 200 data: analysisRunId(문자열), analysisStatus, relation, confidence(숫자/null), reasonCode(문자열/null), observedAt, policyVersion(해당 세션 값).
- guardStatus=EXCLUDED/PRIVACY_BLOCKED이면 metadata/contentPayload를 함께 보내지 않는다. AI 없이 SKIPPED다.
- guard 없는 TAB_META와 CHROME_DOM 본문은 유효한 metadata.urlHost가 필수다. metadata/host 누락·빈 값은400 VALIDATION_ERROR이며 AI로 보내지 않는다. guard만 보내는 요청은 host가 없어도 처리한다.
- APP_META/TAB_META의 본문은 받지 않는다. CHROME_DOM/DESKTOP_APP_OCR 텍스트 최대2,000자. viewport OCR/카메라 입력은 이번 경로의 MVP 범위가 아니다.
- 중복 clientRunId는409 IDEMPOTENCY_CONFLICT. 분당9번째 요청은429이며 Retry-After를 따른다. AI 장애를 자동 재시도하는 POST를 만들지 않는다.
- AI 호출 장애/미설정/수신 확인만 반환하면200 UNCERTAIN, confidence:null, reasonCode:ANALYSIS_UNAVAILABLE다. 자동 로그아웃 대상이 아니다.

## AI·세션 담당과 맞출 부분

- AI는 정본대로 /internal/v1/analysis-runs에서 decision/confidence/reasonCode/modelVersion/promptVersion을 반환해야 한다. 현재 dev의 RECEIVED는 서버가 실패 fallback으로 안전 처리한다. #154가 병합된 뒤 실제 판정 QA한다.
- 서버 환경변수 AI_ANALYSIS_URL(서비스 origin), FOCUSON_AI_API_KEY(AI와 같은 서비스 키)를 설정한다. Desktop/Extension에 서비스 키를 넣지 않는다. Docker는 컨테이너에서 접근할 수 있는 AI 주소여야 한다.
- 후속 Policy Engine은 AnalysisRunRepository의 기기별 source 무관 observedAt 최신 조회와 effectiveRelation을 사용한다. metadata UNRELATED는 이탈 시간이 아니다. 지연 AI 결과의 완료 시각/DB 수정 시각을 현재 화면 순서로 사용하면 안 된다.
- heartbeat가 세션 version을 갱신해도 정상 분석 결과는 버리지 않는다. pause/resume의 lastResumedAt이 바뀐 결과는 현재 구간에 반영하지 않는다.
- 이번에는 interventions/acknowledgements/리포트가 추가되지 않았다. 포키 알림까지 전체 연동 완료라는 의미가 아니다.

- observedAt은 기기 관측 원본이며 서버 시작·재개 시각과 직접 비교하지 않는다. 서버 수신 시각 receivedAt과 구분한다. 기존 공개 필드나 Frontend 변경은 없다. 같은 기기 내 관측 순서는 기존 observedAt 기준을 유지한다. 기기 시계가 세션 중 뒤로 바뀌는 문제나 구간 식별자 없는 재개 전 지연 요청을 완전히 검증했다고 해석하지 않는다.

## 관측 번호 보완 (사용자 판단 위임 후 확정)

새 observationSequence는 선택 양의 정수이며 기기 전체 source를 합친 순서다. 기존 요청의 시계 역행은 AI 보류·보호 상태 유지로 처리한다. 정확한 capture 시점 배정·원자적 영속 counter·재시작·전환 규칙은 [전용 인계](analysis-observation-order-handoff.md)를 따른다. 앞의 관측 시각만 사용하던 설명과 미해결 기록은 이 계약으로 보완됐다. Frontend/공유 타입의 실제 적용은 해당 담당자의 후속이다.
