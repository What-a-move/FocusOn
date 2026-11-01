# 학습 세션 시작·활성 조회 구현 계획

> 상태: 시작·조회 API 구현. 사용자가 Swagger 요청·응답 및 상태·오류 코드를 확인했으며, 전체 빌드·자동 테스트·Checkstyle과 PostgreSQL V9 적용을 확인했다.

## 기본 정보

- 관련 Issue: [#100](https://github.com/What-a-move/FocusOn/issues/100)
- Branch: `feat/100-session-start-active` (기준 `dev` 7ff9494)
- 기준: Notion FocusOn API 명세서·ERD·세션 상태 전이 정책, `docs/API_CONTRACT.md`
- 선행: #87 목표 저장·조회, 기존 인증·기기 연결·온보딩 상태

## 기능 목적 및 API

- `POST /api/v1/sessions`: 인증된 Desktop의 확정 목표를 사용해 세션을 시작한다. Authorization·X-Device-Id·Idempotency-Key가 필요하며 생성에 If-Match-Version을 요구하지 않는다.
- `GET /api/v1/sessions/active`: 현재 사용자의 활성 세션을 조회한다. 없으면 204 빈 응답이다. 인증은 적용했으며 Notion 상세 헤더 정책은 재확인한다.
- 공통 성공·오류 형식과 Swagger 요청·응답·오류 문서를 적용한다.

### 시작 요청 예시 (로컬 계약 요약 기준)

```json
{
  "goalId": "12",
  "targetDurationMs": 1800000,
  "timeZone": "Asia/Seoul",
  "sessionOptions": {
    "interventionLevel": "NORMAL",
    "notificationChannels": ["MOLE_UI"],
    "cameraAnalysisEnabled": false
  },
  "startReadiness": { "screenAnalysisReady": true }
}
```

응답의 정확한 필드와 오류 세부는 Notion API 정본을 재확인한다. 성공은 생성 201, 활성 조회 200 또는 204다.

## 데이터 및 처리 계획

- 최신 dev의 V7 다음 migration 번호를 작업 직전 재확인한다. 세션 사용자·기기·목표 소유 관계, 목표 문구 스냅샷, 상태·버전·시간·옵션을 ERD에 맞춘다.
- 서버 UTC 시작 시각과 유효한 IANA 시간대 스냅샷으로 `studyDate`를 고정한다. 누락·유효하지 않은 시간대는 기존 계약의 Asia/Seoul을 사용한다.
- 사용자당 활성 세션 1개를 PostgreSQL DB 제약과 트랜잭션으로 보호한다. 활성 상태 집합 및 경쟁 조건은 상태 정책과 ERD를 확인한다.
- V9 복합 외래 키는 세션의 목표·실행 기기가 세션 사용자에게 속하도록 DB에서 강제한다. 기존 데이터가 위반하면 마이그레이션이 실패하므로 임의로 소유자를 바꾸지 않는다.
- 동일 Idempotency-Key 및 동일 요청은 원래 응답을 재현하고 다른 요청과의 충돌을 처리한다. 사용자·시작 명령 범위로 24시간 보관하며 오류 상세는 정본과 추가 대조한다.
- 서비스 수준의 시간·채널 검증보다 기존 멱등 기록을 먼저 대조한다. 필수 필드 등의 DTO 검증은 HTTP 계층에서 먼저 실행된다.
- 본인 목표·기기 및 Extension 연결·필수 동의·요청의 화면 준비 상태를 확인한다. 기존 `OnboardingReadinessService`의 검증과 잠금 순서를 분석하고, 서버가 확인할 수 없는 조건을 성공으로 위장하지 않는다.
- 로그·응답에 인증 토큰이나 민감한 원문을 남기지 않는다.
- 카메라 사용 시 서버는 저장된 동의를 확인한다. 현재 시작 계약은 OS 카메라 권한을 전달하지 않으므로 Desktop 권한 검증 여부는 서버 검증 완료로 표현하지 않는다.

## 구현 순서

1. Notion의 두 API 상세·ERD·상태 및 멱등성 정책을 확인해 불명확한 항목을 확정한다.
2. 승인 후 migration·Entity·Repository와 활성 중복 제약을 구현한다.
3. 시작·조회 Service, DTO·Controller, 오류 및 Swagger를 구현한다.
4. H2 자동 테스트와 PostgreSQL migration·경쟁 요청을 검증하고 실제 HTTP 결과를 구분해 기록한다.

## 제외 및 위험

- 일시정지·재개·연장·종료, Heartbeat·ABANDONED, 세션 상세·리포트, 목표 수정·삭제는 후속 이슈다.
- 종료 및 Heartbeat가 없어 시작된 세션이 활성으로 남을 수 있다. 이 이슈만으로 전체 흐름이나 운영 준비가 완료됐다고 보지 않는다.
- 목표 수정·삭제 #97과 세션 시작의 동시성은 공통 잠금 순서를 합의하고 후속 통합 검증한다.
- Notion의 세션 시작 목록과 세션 ERD를 확인했다. 시작 상세 오류·활성 조회·상태 정책과 멱등성 처리 세부는 추가 대조가 필요하다.

## 완료 조건

- [ ] 시작·활성 조회 요청·응답·헤더·오류 및 소유권을 정본과 대조한다.
- [ ] 사용자당 활성 1개, 멱등 재시도·충돌 및 동시 시작을 PostgreSQL에서 검증한다.
- [ ] 인증·기기·목표·시작 조건과 본인 조회·없음 204를 테스트한다.
- [x] 시간·목표 스냅샷과 PostgreSQL migration을 검증한다.
- [x] Swagger·실제 HTTP·Backend 테스트·빌드·Checkstyle 결과를 기록한다. Swagger 수동 확인은 사용자 수행이다.
- [x] ERROR·REPORT·CONTEXT·NEXT_TASK를 실제 결과로 갱신한다.
