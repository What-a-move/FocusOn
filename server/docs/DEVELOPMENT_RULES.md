# Backend 개발 불변 규칙

규칙이 바뀌면 이 문서와 `DECISION_RECORD.md`를 함께 갱신한다.

## BE-001 계약 원본

- 엔드포인트별 최종 계약은 Notion `FocusOn API 명세서`를 따른다.
- 공통 오류와 세션 전이는 Notion `공통 Error Code`, `세션 상태 정책`을 따른다.
- 로컬 요약은 루트 `docs/API_CONTRACT.md`에서 관리한다.
- 폐기된 예전 Notion `API 명세` 페이지는 사용하지 않는다.

## BE-002 응답과 오류

- 성공 응답은 `{ "data": ... }` 형식이며 `204`는 본문이 없다.
- 실패 응답은 `code`, `message`, `retryable`, `retryAfterSeconds`, `requestId`, `details`를 최상위에 둔다.
- 클라이언트는 `code`로 분기한다.
- 새 오류 코드는 Notion 공통 정책에 먼저 정의하고 구현한다.

## BE-003 계층 책임

- Controller: 요청·응답 변환과 입력 검증
- Service: 비즈니스 로직과 트랜잭션 경계
- Repository: 데이터 접근

Controller에 상태 전이와 정책 판단을 작성하지 않는다.

## BE-004 인증과 동시성

- 인증이 필요한 API는 명세에 따라 `Authorization: Bearer {token}`을 사용한다. Google 교환·Extension 최초 연결은 인증 전 요청이며, refresh는 body의 Refresh Token으로 인증한다.
- Desktop·Extension 요청은 명세에 따라 `X-Device-Id`를 함께 사용한다.
- 세션 생성·일시정지·재개·연장·종료는 `Idempotency-Key`를 사용한다.
- 기존 세션 상태 변경은 `If-Match-Version`도 검증한다.
- Heartbeat는 두 동시성 헤더를 사용하지 않는다.

## BE-005 세션 시간

- 서버가 세션 상태와 기준 시간을 관리한다.
- 시작·종료·일시정지 시각은 서버 UTC를 사용한다. Desktop monotonic 누적값을 서버가 검증·확정하고, PC 벽시계 변경이나 무응답 시간을 누적값에 임의로 반영하지 않는다.
- 활성 시간은 `accumulatedActiveMs`, 목표 시간은 `targetDurationMs`로 표현한다.
- MVP 세션 생성의 `targetDurationMs`는 5~300분, 1분 단위의 필수 정수(ms)이며 null·누락을 거절한다. 이전 무기한 세션 정의는 현재 MVP에 적용하지 않는다.
- Heartbeat는 10초 간격, 무신호 60초를 `ABANDONED` 후보 기준으로 사용한다. 마지막 유효 heartbeat(첫 heartbeat 전은 `lastResumedAt`, 최초 시작은 `startedAt`) 뒤 총 3분까지 복구되지 않으면 마지막 서버 확정 누적 시간과 `HEARTBEAT_TIMEOUT` 사유로 원자적 자동 종료한다. 3분 전에 신호가 복구되면 후보를 해제한다.
- 한 사용자에게 `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING` 상태 세션은 하나만 허용한다.
- `DRAFT`는 클라이언트 생성 전 상태이며 DB에 저장하지 않는다. `ABANDONED` 후보는 `RUNNING`의 생존 기준 시각으로 계산하고 별도 상태값을 만들지 않는다.
- 재개 후 생존 기준은 현재 `lastResumedAt`과 마지막 유효 `lastHeartbeatAt` 중 최신 시각이다. 관측 순서 비교용 클라이언트 시각과 서버 수신 시각을 구분하며, 저장값·동일 시각 재전송 정책은 세션 구현 전에 정리한다.
- Desktop 재실행 시 마지막 서버 확정 `accumulatedActiveMs`만 인정하고 `AUTO_PAUSED`, `pauseReason: APP_RESTART`로 복구한다. 사용자 확인 없는 자동 재개는 금지한다.

## BE-006 분석·개입 정책

- `confidence < 0.75`는 `UNCERTAIN`으로 정규화한다.
- 정책 `v1`의 `UNCERTAIN`은 이탈 시간에 합산하지 않고 현재 연속 구간을 즉시 끊는다. 30초 보류·이어붙이기는 이전 정책이다.
- `RELATED`가 확인되면 기존 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만의 짧은 화면 전환은 이탈 구간으로 집계하지 않는다.
- 각 강도에서 연속·반복 중 하나를 충족하면 후보가 된다. `GENTLE`: 연속 180초 초과 또는 rolling 10분 내 60초 초과 구간 3회. `NORMAL`: 연속 60초 초과 또는 30초 초과 구간 2회. `ACTIVE`: 연속 30초 초과 또는 20초 초과 구간 2회. 경계값은 `>=`로 바꾸지 않는다.
- 서버 cooldown은 `sessionId + interventionType`별 5분이며 실제 전송부터 계산한다. 만료 뒤에는 새 관측에서 다시 평가한다. `확인했어` 뒤 5분 재집중은 별도의 Desktop UI 상태다.
- 현재 정책 `v1`은 서버가 세션 시작 시 `interventionPolicyVersion`으로 고정하고 `analysis_runs.policy_version`, `intervention_events.policy_version`에 같은 값을 저장한다. 클라이언트가 버전을 정하거나 진행 중 세션의 버전을 변경하지 않는다.

## BE-007 재시도와 속도 제한

- 상태 명령은 동일한 idempotency key가 있을 때만 재시도한다.
- 분석 요청은 `clientRunId`로 중복을 막는다.
- `429`에는 `Retry-After`를 반환한다.
- 수치는 `docs/API_CONTRACT.md`의 표와 Notion 계약을 따른다.

## BE-008 개인정보와 로그

- `docs/DATA_PRIVACY.md`를 따른다.
- 토큰, API key, 연결 코드, 원본 화면·카메라 데이터, 원문 콘텐츠를 로그·오류 details·개발 오류 문서에 남기지 않는다.
- 로그에는 requestId, 안전한 리소스 식별자, 기능명, 오류 코드 정도만 남긴다.

## BE-009 온보딩·Extension 계약

- 동의 응답은 `currentVersion`, `consents[{consentType,isGranted,consentVersion,grantedAt,withdrawnAt}]`를 사용한다. 현재 문서 버전을 응답하고 클라이언트가 그대로 저장 요청에 보낸다.
- 닉네임 중복은 프로필 저장의 `409 PROFILE_NICKNAME_DUPLICATE`로 처리하며 별도 확인 API를 만들지 않는다.
- Extension 연결 응답은 토큰 3개 필드와 `device.deviceId`, 공개 `deviceType: EXTENSION`을 반환한다. 내부 `CHROME_EXTENSION` enum은 응답에서 변환한다.
- 틀린 코드 1~2회는 `400 LINK_006_CODE_MISMATCH`, 숫자 `details.remainingAttempts`, `retryable: false`; 3번째는 `410 LINK_004_ATTEMPTS_EXCEEDED`다. 자동 재시도와 사용자 수정 후 재입력을 구분한다. 실패는 Extension installationId별로 누적하며 3회면3분 입력을 잠근다. 잠금 중 올바른 코드도 거절하고 remainingAttempts=0·retryAfterSeconds·Retry-After를 반환한다. 성공/잠금 만료 시 초기화하고 타 Desktop 코드는 폐기하지 않는다.
- 정상 화면 닫기·Desktop 종료는 DELETE /pairing-codes/{pairingCodeId}로 취소하며 통지 불가는 발급 후5분 만료다. 재발급·logout·동일 Desktop 재로그인도 기존 ACTIVE 코드를 취소하되 이미 만료된 코드는 EXPIRED/terminal_at=expires_at을 유지한다.
- 같은 사용자·같은 설치는 deviceId 재사용·기존 family/refresh 폐기 후 새 family 발급이다. 다른 계정 활성 설치는409 LINK_007_INSTALLATION_ALREADY_LINKED로 거절하고 코드를 소비하지 않는다.
- 3초 polling은 기본 20회/분이며 다른 호출·재시도와 60회/분 한도를 공유한다. 429의 `Retry-After`를 지킨다.
- 알림은 Desktop에서 필수 확인하지만 readiness API·DB에는 알림 필드를 추가하지 않는다. 2026-10-25 정본에 따라 Desktop이 요청 전에 알림 준비를 확인·차단하며 서버는 `NOTIFICATION_PERMISSION`을 반환하지 않는다. 제외 앱 저장은 MVP 범위 밖이고 제외 도메인만 저장한다.

## BE-010 날짜·리포트·학습 정리

- 세션 생성 요청의 IANA `timeZone`을 검증해 snapshot으로 저장한다. 없거나 잘못된 값이면 `Asia/Seoul`을 사용하며 IP로 추정하지 않는다.
- `study_date`는 최초부터 `DATE NOT NULL`이며 시작일로 고정한다. 출시 전 세션 모델 백필은 하지 않으며, 이 결정만으로 데이터를 삭제하지 않는다.
- 일간 `sessions[]`는 `sessionId`, `goalTextSnapshot`, `startedAt`, `endedAt`, `accumulatedActiveMs`를 사용한다. UTC 시각과 문자열 ID, 종료 확정 세션만 집계하는 원칙을 유지한다.
- `ABANDONED`를 일간·달력 집계에는 포함하지만 자동 학습 정리 작업은 만들지 않는다.
- `keyConcepts`는 `{name,briefExplanation,sessionEvidence}` 객체 최대 5개다. 서로 다른 근거 2개 미만은 `INSUFFICIENT_DATA`, 정리 전체는 1,200자 이하이며 `reviewQuestions`는 없다.
- 2026-10-24 확인한 Notion 내 구버전·누락 항목과 아직 결정되지 않은 경계는 `docs/NOTION_SYNC_AUDIT.md`에서 확인한다.

## 확인표

| 규칙 | PR에서 확인할 내용 |
| --- | --- |
| BE-001 | Notion 최종 명세와 경로·필드·권한 일치 |
| BE-002 | 성공·오류 형식과 HTTP 상태 일치 |
| BE-003 | 계층 책임과 트랜잭션 경계 |
| BE-004 | 인증·기기·idempotency·version 헤더 |
| BE-005 | 세션 전이와 시간 누적 |
| BE-006 | 개입 임계값·구간 처리·정책 버전 |
| BE-007 | 재시도 가능 조건과 rate limit |
| BE-008 | 민감 정보 비노출 |
| BE-009 | 동의 버전·연결 응답·실패 횟수·온보딩 권한 경계 |
| BE-010 | 기기 시간대·studyDate·일간 세션 항목·학습 정리 객체 |
