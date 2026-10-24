# Notion ↔ 로컬 Markdown 문서 대조 기록

- 최근 재검증일: 2026-10-25
- 코드 기준: `dev`의 PR #45 병합 커밋 `0b88bb8`과 현재 로컬 문서 변경분
- 과거 전체 대조: 2026-10-12, [Issue #23](https://github.com/What-a-move/FocusOn/issues/23). 당시 제품·정책·디자인·QA 32개, API 42개, 로컬 Markdown 109개를 대조했다. 이 수치는 현재 전체 재검증 건수가 아니다.
- 정본: 제품 정책은 현재 FocusOn Notion 문서, endpoint 계약은 Notion `FocusOn API 명세서` 데이터베이스다. 로컬 MD는 개발 요약·규칙·초안이다.
- 생성물·작업 기록인 `.handoff`, `build`와 Git 메타데이터는 소스 문서 목록에서 제외했다.

## 이번 재검증 요약

**로컬 계약 정리와 실제 기능 구현은 별개다.** Google 로그인 API는 아직 구현 전이고, 아래 남은 항목이 해결됐다고 간주하지 않는다.

| 구분 | 확인 결과 | 처리 |
| --- | --- | --- |
| 노션 ↔ 로컬 일치 | 동의 필드·현재 버전, pairing 성공 응답·오류 이름, heartbeat 헤더·60/180초, studyDate, 학습 정리 개념 객체 | 유지 |
| 로컬 오류 수정 | UUID 기본 규칙, 조회 timeZone 설명, DRAFT의 서버 상태 혼입, 인증 저장 모델·Extension 구현 상태의 오래된 표기 | 현재 계약·코드에 맞춰 수정 |
| 정본 문서 보완 필요 | AI 분석 계약 구버전·ERD 이미지·과거 null 허용·일간 meta 표기 | 아래 과거 대조와 오늘 재검증을 구분. 노션 수정은 수행하지 않음 |
| 기능 구현 전 확인 | pairing 실패 횟수 문맥, 4번째 기기 복구 인증, rolling window, heartbeat 시각 처리, family 만료 | 해당 기능의 계약 검토 항목으로 유지. 알림 책임은 오늘 정본에서 해결 |

현재 로컬 API 목록은 Method + 경로 기준 **43개**다. 아래 과거 42개 목록과 비교하면 `GET /api/v1/users/me/onboarding-readiness` 1개가 추가돼 있고 기존 경로의 누락은 없다. 이는 로컬 목록과 과거 스냅샷의 비교 결과이며, 현재 Notion DB 전 행을 다시 센 수치는 아니다.

### 실제 구현 상태

| 영역 | 코드에서 확인한 것 | 아직 없는 것·미검증 |
| --- | --- | --- |
| Backend | 공통 응답·오류·requestId, AccessJwtService, 인증 Entity·Repository 5개, Flyway V1·V2 | Google exchange·refresh·auth/me·logout HTTP API와 요청 인증 필터 |
| Desktop | Main 인증·온보딩·목표 클라이언트, 리포트 IPC와 달력·일간 응답 파서 | 실제 Backend 로그인·온보딩·리포트 왕복 QA |
| Extension | installationId, pairing 요청·오류 파서, token 저장 | 실제 Desktop 발급 코드 왕복, 후속 refresh·unlink |
| 세션·분석·리포트 | 로컬·노션 설계 계약 | Backend Controller·Service·migration 구현 |

코드 근거: `server/src/main/java/com/capstone/backend/`, `server/src/main/resources/db/migration/`, Desktop의 `electron/main/{auth-api,onboarding-service,report-ipc}.cts`·`electron/shared/report-contract.cts`, Extension의 `features/pairing/{pairing-api.ts,extension-auth.ts}`.

이번 검증은 문서·스키마·파서의 정적 대조다. 서버 구동·실제 Google 로그인·DB 재적용·전체 기능 테스트의 통과를 뜻하지 않는다.

### 문서 검사 결과

| 검사 | 결과 |
| --- | --- |
| 변경 Markdown·로그인 PLAN 초안 | 29개 확인, 코드 블록 닫힘·충돌 마커 오류 없음 |
| JSON 예제 | 24개 파싱 성공 |
| 상대 경로 Markdown 링크 | 40개 대상 존재 확인 |
| 공통 API ↔ AI 개입 수치 표 | GENTLE·NORMAL·ACTIVE의 연속 시간·반복 시간·횟수 일치 |
| API 경로 목록 | 로컬 43개, 과거 42개 대비 readiness 조회 1개 추가·기존 경로 누락 없음 |
| 개인정보·보관 기간 | 최신 열람한 노션 6.1과 로컬 DATA_PRIVACY의 구현 기간 일치. 출시 전 법률 검토와 구분 |
| 변경 범위 | Markdown만 변경. 실행 코드·SQL 변경 없음 |

링크 검사는 로컬 파일 존재 확인이며 외부 링크 전체의 접근 성공을 보장하지 않는다. 이 검사 결과는 현재 변경분 기준이고 이후 문서 추가·수정 시 다시 확인한다.

## 2026-10-25 최신 계약·로그인 적합성 재검증

### 해결된 항목과 이번 수정

| 항목 | 직접 확인한 현재 기준 | 로컬 처리 |
| --- | --- | --- |
| 일간 sessions | 일간 API에 확정한 5개 필드가 반영됨 | 이전의 배열 항목 누락 표기를 해소. meta.requestId 차이는 유지 |
| 세션 goalId | 생성 API 예시가 문자열 `12`로 정정됨 | 이전 UUID 예시 문제 해소 |
| MVP 목표 시간 | 세션 생성 API는 5~300분·1분 단위, null 불가 | 공통 계약·아키텍처·Backend 규칙·시작 문서의 과거 null 허용 정정 |
| 알림 책임 | 생성 API는 Desktop이 시작 전 확인하고 서버는 NOTIFICATION_PERMISSION을 반환하지 않음 | 입력 신호 미정 항목을 해소. 로컬 권한 안내·과거 결정의 변경 표시 반영 |
| 목표 확인 | previousAnswers·clarificationSessionId, Backend AI 재시도 1초→3초 최대 2회 | 공통·AI 요약에 반영. 내부 재시도로 질문 수·턴을 중복 확정하지 않도록 명시 |
| 로그인 만료 오류 | Desktop은 AUTH_ACCESS_TOKEN_EXPIRED에만 refresh 수행. 현재 JWT 서비스는 실패 원인을 하나의 예외로 감쌈 | 로그인 PLAN의 필수 구현·검증 항목 보강. JWT 내부 서비스 자체를 완성된 요청 인증으로 취급하지 않음 |
| 로그인 유지 | refresh 30일·rotation은 확정. family와 token에는 각각 만료 시각 존재 | 무기한 로그인 보장으로 표현하지 않음. family 만료 연장 방식은 후속 구현 전 확인 |

이번에 직접 읽은 정본: [refresh](https://app.notion.com/p/bacabe41fdc543f9a2575fc05e2e1f81), [세션 생성](https://app.notion.com/p/bb8189baf319482d99a86a527234ce8b), [목표 확인](https://app.notion.com/p/cdb9b3b55d414dd7911b7774ac5affaa), [일간 리포트](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7).

### 남은 항목의 영향

- **현재 로그인 구현에 반영:** 만료 오류 구분, DB 사용자·기기·family 검증, 같은 Desktop 재로그인 시 기존 family 처리. 기존 인증 5개 테이블과 V1·V2는 변경하지 않는다.
- **refresh 구현 전:** family의 고정/연장 만료와 새 Refresh Token 만료 관계를 정의한다. 기존 family의 초기 30일을 고정한 채 새 token만 연장하면 계속 사용해도 family 만료로 로그인 종료될 수 있다. 서버가 만료를 확인하는 것 자체를 없애는 방식으로 해결하지 않는다.
- **세션 구현 전:** ERD·세션 상태 정책에 남은 과거 null 허용 설명을 최신 API와 맞춰야 한다. 아직 세션 테이블 migration은 없으므로 V1·V2에 섞어 수정하지 않는다.
- **Desktop 소비자의 관대한 파서:** 목표 응답 파서는 clarificationSessionId 누락을 null로 허용한다. 이는 서버가 필수 ID를 생략해도 된다는 뜻이 아니다. 서버 계약 검사에서는 필수 반환을 검증한다.
- **이전부터 남은 항목:** pairing 실패 횟수 문맥, 4번째 기기 복구 인증, rolling window 기준, heartbeat 관측/수신 시각 분리, 일간 meta 표기. 아래 2026-10-24 기록에서 해결됐다고 덮지 않는다.

아래 2026-10-24 절의 `누락`·`미정` 표기는 당시 관측이다. 일간 항목·goalId 예시·알림 책임은 이 절의 최신 결과로 대체한다.

## 2026-10-24 추가 대조 — Frontend 공유 계약

이 절은 이번 사용자 공유·최종 정정, 최신 `dev`의 Frontend 소비 코드, 아래 관련 Notion을 브라우저로 읽어 대조한 기록이다. 아래의 기존 2026-10-12 전체 대조는 당시 스냅샷이며 현재 API 개수·구현 현황을 보장하지 않는다. 이번 작업은 로컬 문서·기획서를 수정했으며 Notion·실제 DB·API 구현은 수정하지 않았다.

| 항목 | 직접 확인한 근거 | 로컬 반영 결과 |
| --- | --- | --- |
| Extension 연결 201 | [pairing API](https://app.notion.com/p/b0f5534f1c4b4e3f9f44225fb2ee1408), `apps/extension/features/pairing/pairing-api.ts` | 토큰 3개 필드·UTC ISO 8601·deviceId·공개 EXTENSION. 내부 CHROME_EXTENSION은 응답 변환으로 유지 |
| 코드 오입력 | 같은 API 및 사용자 공유 최종 예시 | 400 LINK_006_CODE_MISMATCH·숫자 remainingAttempts, 세 번째 410. 자동 재전송은 false로 정리. 3회 제한의 귀속 문맥은 아래 미해결 항목 |
| 동의 | [consents API](https://app.notion.com/p/eea06d0fde624e079eac5fec713c0bb7), `onboarding-service.cts` | currentVersion과 consentType·isGranted·consentVersion·grantedAt·withdrawnAt을 사용. 앞선 type/agreed/version/agreedAt 예시는 폐기 |
| 닉네임·polling | 사용자 공유, Frontend 결정 017·019 | 저장 시 중복 409. readiness 3초 polling은 기본 20회/분이며 인증 API 60회/분을 다른 호출과 공유 |
| 권한·제외 | 사용자 공유, Frontend 결정 018·019, [ERD 4.4](https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17) | 알림은 Desktop 필수 확인이지만 readiness API·DB에는 새 필드 없음. 제외 앱 저장은 MVP 밖 |
| 개입 v1 | [화면 분석 5.2·5.3](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8), [세션 생성 API](https://app.notion.com/p/bb8189baf319482d99a86a527234ce8b) | 강도별 연속/반복 OR와 엄격한 초과, UNCERTAIN 구간 종료. 서버 선택 v1을 세션·분석·개입에 고정 |
| 시간대·날짜 | 같은 세션 생성 API, [리포트 설계 3.3](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca), ERD 5.2 | 기기 IANA·잘못된 값은 Asia/Seoul, 서버 UTC 시각·monotonic 누적 검증, 시작일 고정·NOT NULL·출시 전 백필 없음 |
| 학습 정리 | 리포트 설계 5.3·5.4, [learning-summary API](https://app.notion.com/p/d6c152f985d64d65b3aaa690f46b7455) | 개념 객체 3개 필드·최대 5개, 서로 다른 근거 2개 미만은 INSUFFICIENT_DATA |
| 일간 sessions[] | 사용자 마지막 공유, [현재 일간 API](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7) | sessionId·goalTextSnapshot·startedAt·endedAt·accumulatedActiveMs. API Notion에는 아직 sessions: array까지만 명시 |

### 사용자 공유의 충돌을 처리한 기준

- `LINK_006_CODE_MISMATCH`가 정식 이름이다. 붙여넣기의 별표(`CODE*MISMATCH`)는 code에 넣지 않는다. `retryable`의 true/false 예시 중에는 뒤의 false와 공통 400 입력 수정 원칙을 따른다. false가 사용자 수정 후 재입력까지 금지하는 것은 아니다. Notion 표에는 이 플래그의 정확한 값이 아직 생략돼 있다.
- 학습 정리·동의 필드는 마지막 정정과 Notion의 현재 정의가 일치한다.
- 서버 UTC로 모든 누적값을 새로 계산한다는 의미로 해석하지 않는다. 최신 리포트 설계 3.3은 Desktop monotonic 계산과 서버 검증·확정을 명시하므로 heartbeat·무응답 시간 비추정 정책을 유지한다.
- `확인했어` 뒤 5분은 Client UI의 재집중 상태다. 서버 cooldown은 실제 전송부터 5분이다. 두 시계를 같은 필드나 세션 상태로 합치지 않는다.
- `study_date`의 미래 migration을 NOT NULL로 설계한다. 출시 전 백필 생략 결정은 데이터 삭제 명령이 아니며 실제 데이터는 삭제하지 않았다.

### Notion에 남아 있는 차이

| 항목 | 현재 정본의 차이 | 적용 기준·남은 작업 |
| --- | --- | --- |
| AI 분석 계약 4.4·7장 | GENTLE 120초, NORMAL 반복 조건만, UNCERTAIN 30초 보류, `focus-intervention-v1` 예시 | 사용자 확정과 최신 화면 분석 5.2·5.3의 `v1`을 적용. [AI 분석 계약](https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c) 정리 필요 |
| 일간 리포트 | `sessions: array`만 있고 항목 필드 5개가 없음. `meta.requestId` 표기도 공통 성공 응답과 다름 | 사용자 확정 5개 필드는 로컬·Desktop 파서에서 일치. [일간 API](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7) 세부 스키마 보완 필요. 공통 래퍼에 meta를 임의 추가하지 않음 |
| ERD 준비 상태 4.4 | `notification_ready`는 MVP 미사용으로 표시. `analysisExcludedDomainsStepCompleted`의 영속 marker 컬럼은 표에 없음 | 알림 컬럼을 새로 구현하지 않음. Issue #38에서 제외 단계 완료 marker 저장 정의 필요. 빈 목록으로 방문 여부를 추정하지 않음 |
| ERD 이미지 | 페이지가 study_date·캐시 키 이미지 갱신 필요를 명시 | [ERD 상세 텍스트](https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17)가 현재 논리 설계 기준. 텍스트·카탈로그와 이미지 동기화 필요 |
| 세션 생성 goalId 예시 | [세션 생성 API](https://app.notion.com/p/bb8189baf319482d99a86a527234ce8b)는 UUID 예시, ERD goals.id는 BIGINT | 공개 리소스 ID는 BIGINT의 10진 문자열. 예시를 수정해야 하며 DB PK를 UUID로 변경할 근거로 삼지 않음 |
| Heartbeat 시각 표현 | [상태 정책 5.2](https://app.notion.com/p/3de0b6e5e64c80cc8140f659abd82371)는 마지막 수신 시각과 비교, API는 마지막 유효 관측 시각과 비교 | 클라이언트 관측 순서와 서버 수신 시각을 구분해야 함. 아래 구현 전 확인 항목에서 추적 |

### 관련 기능 구현 전에 해결할 실제 계약 경계

| 경계 | 현재 계약으로 생기는 문제 | 구현 전에 필요한 정리 |
| --- | --- | --- |
| 연결 실패 횟수의 귀속 | 요청은 pairingCode·installationId뿐이다. 존재하지 않는 코드를 입력하면 대상 Desktop의 pairing record를 찾을 수 없어 어떤 코드의 attempt_count를 올리고 세 번째에 폐기할지 알 수 없다. | 시도 문맥을 식별하는 방식 또는 Extension별 입력 시도 제한으로 정책을 정리한다. 사용자 합의 없이 새 필드를 만들거나 임의 Desktop의 코드를 폐기하지 않는다. |
| 서버의 알림 준비 판정 | 알림 필수지만 readiness에는 값이 없고 서버는 macOS 확인 결과를 직접 관찰할 수 없다. | 세션 생성의 startReadiness에서 받을 정확한 신호·신뢰 경계를 명시한다. 신호 없이 서버 검증 완료로 취급하지 않는다. |
| 4번째 Desktop의 기기 폐기 | Google exchange는 409로 토큰을 발급하지 않는데 기기 폐기 API에는 Bearer 인증이 필요하다. | 기존 로그인 기기에서 해제할지, 새 기기의 제한된 복구 인증을 둘지 기기 관리 계약에서 정한다. 임시 토큰·endpoint를 임의 추가하지 않는다. |
| rolling 10분의 구간 기준 | 초과 임계값은 확정됐지만 window 경계의 구간 포함 기준과 진행 중 구간의 반복 횟수 산입이 생략돼 있다. | Policy Engine 구현 시 구간의 시작·종료·임계 통과 중 기준 시각을 하나로 정하고 경계 검증 사례를 작성한다. |
| Heartbeat 관측·수신 시각 | `clientObservedAt` 순서 비교값과 생존 기준인 서버 수신 `lastHeartbeatAt`은 의미가 다르다. ERD 5.2 표에는 별도 마지막 클라이언트 관측 시각이 명시되지 않았다. | 비교용 값을 어디에 저장할지 정하고, 같은 시각의 재전송이 생존을 갱신하는지·감소 누적값과 과거 시각이 함께 오면 무엇을 먼저 적용하는지 정의한다. 기존 lastHeartbeatAt을 클라이언트 시계로 덮지 않는다. |

위 경계는 Google 교환·auth/me 기획 준비를 막지는 않는다. pairing·세션·기기 관리 구현 때 정리할 항목이며 미구현 API를 완료했다고 표시하지 않는다. GitHub의 다른 담당자에게 별도 메시지는 보내지 않았다.

### 경계값 대조

| 규칙 | 경계에서의 기대값 |
| --- | --- |
| 신뢰도 | 0.75 미만은 UNCERTAIN. 0.75는 신뢰도 기준을 통과하지만 다른 게이트도 필요 |
| 짧은 전환 | 15초 미만 제외. 정확히 15초인 구간은 길이 때문에 제외하지 않음 |
| 개입 v1 | GENTLE 180초·NORMAL 60초·ACTIVE 30초와 같은 값은 연속 조건 미충족. 각각 초과해야 충족 |
| 반복 조건 | 60초·30초·20초와 같은 길이는 해당 강도의 반복 횟수에 포함하지 않음. window 포함 시각은 아직 미정 |
| Heartbeat | 생존 기준 뒤 60초는 후보, 180초는 종료 작업 대상. PAUSED·종료 확정 상태는 worker 대상 아님 |
| 집중 달력 | 종료 확정 합계 59,999ms는 비활동일, 60,000ms는 활동일 |
| 학습 정리 | 서로 다른 근거 0~1개는 INSUFFICIENT_DATA. 2개 이상도 출력 안전·생성 성공을 보장하지 않음 |

이 표는 계약에서 도출한 검증 기대값이며 실행 테스트 결과가 아니다.

## 2026-10-12 전체 대조 기록

### Notion 제품·정책·디자인·QA 페이지

| Notion 페이지 | 로컬 대응 문서 | 대조 상태 |
| --- | --- | --- |
| [🎯 FocusOn 최상위 기획서](https://app.notion.com/p/3de0b6e5e64c80c5abdfd3ef861fe701) | `README.md`<br>`docs/PROJECT_CONTEXT.md`<br>`docs/ARCHITECTURE.md`<br>`AI/README.md` | 대응 문서 대조 |
| [🔎 서비스 개요 · 문제 정의 · 페르소나](https://app.notion.com/p/3de0b6e5e64c80539a95dfcb79f9a0c1) | `README.md`<br>`docs/README.md` | 대응 문서 대조 |
| [🗺️ 전체 사용자 플로우](https://app.notion.com/p/3de0b6e5e64c80909dcad80ef9f6c519) | `apps/docs/AUTH_SESSION_FLOW.md`<br>`apps/desktop/docs/CONTEXT.md`<br>`apps/extension/docs/CONTEXT.md` | 대응 문서 대조 |
| [🗂️ 기능 카탈로그 · 우선순위](https://app.notion.com/p/3de0b6e5e64c803abd87d3cee9be54a3) | `docs/README.md`<br>`AI/docs/README.md`<br>`server/docs/NEXT_TASK.md` | 대응 문서 대조 |
| [📚 문서 인덱스](https://app.notion.com/p/3de0b6e5e64c80cd95cff031038ff972) | `docs/README.md`<br>`docs/DOCUMENTATION_RULES.md` | 대응 문서 대조 |
| [📈 기능 진행 현황](https://app.notion.com/p/3de0b6e5e64c806aa827fefa710f9743) | `apps/desktop/docs/CONTEXT.md`<br>`apps/extension/docs/CONTEXT.md`<br>`server/docs/CONTEXT.md`<br>`AI/docs/CONTEXT.md` | 대응 문서 대조 |
| [🎯 로그인 → 학습 목표 설정](https://app.notion.com/p/3de0b6e5e64c80b6b067f5547e7dbcfd) | `apps/docs/AUTH_SESSION_FLOW.md`<br>`AI/docs/features/goal-assistance-PLAN.md`<br>`docs/API_CONTRACT.md` | 대응 문서 대조 |
| [🖥️ 화면 분석](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8) | `docs/ARCHITECTURE.md`<br>`apps/extension/docs/EXTENSION_ARCHITECTURE.md`<br>`AI/docs/content_acquisition_spec.md`<br>`AI/docs/analysis_rules.md` | 대응 문서 대조 |
| [📷 카메라 분석](https://app.notion.com/p/3de0b6e5e64c806ba051ef88e8621cfe) | `apps/desktop/docs/MACOS_PERMISSION_FLOW.md`<br>`docs/DATA_PRIVACY.md`<br>`AI/docs/state_model.md` | 대응 문서 대조 |
| [⏱️ 학습 세션 · 타이머 · 휴식 제안](https://app.notion.com/p/3de0b6e5e64c8029853acfb9c1d75178) | `docs/API_CONTRACT.md`<br>`server/docs/DECISION_RECORD.md`<br>`AI/docs/features/personalized-break-recommendation-PLAN.md` | 대응 문서 대조 |
| [📊 집중 기록 · 학습 정리 · 리포트](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca) | `docs/API_CONTRACT.md`<br>`AI/docs/session_note_spec.md`<br>`AI/docs/goal_session_spec.md` | 대응 문서 대조 |
| [🔐 인증 · 토큰 · 디바이스 정책](https://app.notion.com/p/3de0b6e5e64c80c89c6cfe0b703fd964) | `apps/docs/AUTH_SESSION_FLOW.md`<br>`server/docs/DECISION_RECORD.md`<br>`docs/API_CONTRACT.md` | 대응 문서 대조 |
| [🔑 Chrome · macOS 권한 정책](https://app.notion.com/p/3de0b6e5e64c805fafa4d03587b0bace) | `apps/desktop/docs/MACOS_PERMISSION_FLOW.md`<br>`apps/extension/docs/MANIFEST_PERMISSION_POLICY.md` | 대응 문서 대조 |
| [🛡️ 개인정보 · 동의 · 데이터 보관 정책](https://app.notion.com/p/3de0b6e5e64c80c0b63cf0514a06f36a) | `docs/DATA_PRIVACY.md`<br>`AI/docs/data_lifecycle.md`<br>`apps/docs/FRONTEND_SECURITY.md` | 대응 문서 대조 |
| [🔄 세션 상태 전이 정책](https://app.notion.com/p/3de0b6e5e64c80cc8140f659abd82371) | `docs/API_CONTRACT.md`<br>`server/docs/DECISION_RECORD.md`<br>`AI/docs/state_model.md` | 대응 문서 대조 |
| [🚨 공통 Error Code 정책](https://app.notion.com/p/3de0b6e5e64c80f6bac6d980ba0c1df5) | `docs/API_CONTRACT.md`<br>`apps/docs/ERROR_HANDLING.md`<br>`server/docs/DEVELOPMENT_RULES.md` | 대응 문서 대조 |
| [🧩 ERD](https://app.notion.com/p/3de0b6e5e64c80a4bf5ef7ff3ac01f17) | `server/docs/CONTEXT.md`<br>`server/docs/DECISION_RECORD.md` | 대응 문서 대조 |
| [🧠 AI 분석 계약](https://app.notion.com/p/3de0b6e5e64c80fbb0bccb985e38002c) | `AI/docs/api_spec.md`<br>`AI/docs/prompt_guide.md`<br>`AI/docs/analysis_rules.md`<br>`AI/docs/DEVELOPMENT_RULES.md` | 대응 문서 대조 |
| [🛠️ 기술 구조](https://app.notion.com/p/3de0b6e5e64c80f997c5ed36af9f661a) | `docs/ARCHITECTURE.md`<br>`apps/docs/FRONTEND_ARCHITECTURE.md`<br>`apps/desktop/docs/ELECTRON_ARCHITECTURE.md`<br>`apps/extension/docs/EXTENSION_ARCHITECTURE.md` | 대응 문서 대조 |
| [📖 용어 · 필드명 사전](https://app.notion.com/p/3de0b6e5e64c80ec8a87cab66ec4dfc4) | `packages/shared-types/README.md`<br>`apps/docs/TYPESCRIPT_CONVENTION.md`<br>`AI/docs/state_model.md` | 대응 문서 대조 |
| [📌 DECISION_RECORD](https://app.notion.com/p/3de0b6e5e64c80bda58dffad654f31d3) | `AI/docs/DECISION_RECORD.md`<br>`apps/desktop/docs/DECISION_RECORD.md`<br>`apps/extension/docs/DECISION_RECORD.md`<br>`server/docs/DECISION_RECORD.md` | 대응 문서 대조 |
| [🧱 화면 구조 · 정보 구조](https://app.notion.com/p/3de0b6e5e64c80c4a01df1044424034a) | `apps/docs/UI_STATE_GUIDE.md`<br>`apps/desktop/docs/WINDOW_LIFECYCLE.md` | 대응 문서 대조 |
| [🧬 디자인 시스템](https://app.notion.com/p/3de0b6e5e64c80729b80d4249bb791b8) | `apps/docs/DESIGN_SYSTEM_RULES.md` | 대응 문서 대조 |
| [💬 UX 문구 · 알림 문구 정책](https://app.notion.com/p/3de0b6e5e64c8082836cc7a377b1f4c3) | `docs/SENTENCE_WRITING.md`<br>`apps/docs/UI_STATE_GUIDE.md` | 대응 문서 대조 |
| [🔍 디자인 레퍼런스 · 벤치마크](https://app.notion.com/p/3de0b6e5e64c80678972dee0284683bb) | 없음 | Notion 전용 |
| [🕓 디자인 변경 이력](https://app.notion.com/p/3de0b6e5e64c80959ba3cb51ade4c528) | 없음 | Notion 전용 |
| [📏 FocusOn · Figma 화면 설계 가이드](https://app.notion.com/p/3e50b6e5e64c806aa023cd76895139cc) | `apps/docs/DESIGN_SYSTEM_RULES.md`<br>`apps/desktop/docs/WINDOW_LIFECYCLE.md` | 대응 문서 대조 |
| [🧪 기능별 QA 체크리스트](https://app.notion.com/p/3de0b6e5e64c81269241c1ffedc5f0f6) | `docs/QA_CONVENTION.md`<br>`apps/docs/TESTING_STRATEGY.md` | 대응 문서 대조 |
| [🧪 통합 사용자 시나리오](https://app.notion.com/p/3de0b6e5e64c80b5bec7da51da940e62) | `docs/QA_CONVENTION.md`<br>`apps/docs/TESTING_STRATEGY.md` | 대응 문서 대조 |
| [⚠️ 권한 · 예외처리 테스트](https://app.notion.com/p/3de0b6e5e64c808ab648fc74d8863a3f) | `docs/QA_CONVENTION.md`<br>`apps/desktop/docs/MACOS_PERMISSION_FLOW.md` | 대응 문서 대조 |
| [🔒 보안 · 개인정보 점검](https://app.notion.com/p/3de0b6e5e64c80a68f1ad689174f0187) | `docs/DATA_PRIVACY.md`<br>`apps/docs/FRONTEND_SECURITY.md`<br>`apps/docs/LOGGING_POLICY.md` | 대응 문서 대조 |
| [🚀 출시 전 체크리스트](https://app.notion.com/p/3de0b6e5e64c80a58d90ee4b7c88425b) | `docs/WORKFLOW.md`<br>`apps/docs/CODE_REVIEW_CHECKLIST.md` | 대응 문서 대조 |

### API 명세 42개 (2026-10-12 당시)

당시 `docs/API_CONTRACT.md`와 **Method + 경로 42개가 모두 일치**했다. 당시 경로 누락·추가는 0건이었으며 42개 항목의 속성 입력 여부를 확인했다. 현재 로컬 목록 수와 세부 스키마 누락은 위 재검증 결과를 따른다.

- 상태: `READY` 38개, `REVIEW` 4개
- `REVIEW` 상태는 팀 검토가 남아 있어 그대로 유지했다.

- [POST /api/v1/sessions](https://app.notion.com/p/bb8189baf319482d99a86a527234ce8b?pvs=204) — `READY`
- [POST /api/v1/auth/refresh](https://app.notion.com/p/bacabe41fdc543f9a2575fc05e2e1f81?pvs=204) — `READY`
- [POST /api/v1/goals/clarify](https://app.notion.com/p/cdb9b3b55d414dd7911b7774ac5affaa?pvs=204) — `REVIEW`
- [POST /api/v1/pairing-codes](https://app.notion.com/p/fc99d9b103484f84b521823a23ac1296?pvs=204) — `READY`
- [GET /api/v1/users/me](https://app.notion.com/p/3301b13a9fa7485ab9bb1f7d475d4be7?pvs=204) — `READY`
- [POST /api/v1/goals](https://app.notion.com/p/25a143525b604493af5a83c0f2788ab0?pvs=204) — `READY`
- [GET /api/v1/devices](https://app.notion.com/p/ee1776d4fe5148bfb9cee875dfcb1ddc?pvs=204) — `READY`
- [PUT /api/v1/users/me/profile](https://app.notion.com/p/a48c8a203a61411798f6de9de04e66ac?pvs=204) — `READY`
- [GET /api/v1/reports/weekly](https://app.notion.com/p/e6cc689b017d42fa9cc272f44a6bb023?pvs=204) — `REVIEW`
- [GET /api/v1/goals/{goalId}](https://app.notion.com/p/6ddc890df1ec42029dc603828a765784?pvs=204) — `READY`
- [POST /api/v1/interventions/{interventionId}/acknowledgements](https://app.notion.com/p/fb8626ab4897450d95a6bfeddba674e6?pvs=204) — `READY`
- [POST /api/v1/extension-pairings](https://app.notion.com/p/b0f5534f1c4b4e3f9f44225fb2ee1408?pvs=204) — `READY`
- [GET /api/v1/auth/me](https://app.notion.com/p/2ad306d88bfe42c094c900ceb3e7aff4?pvs=204) — `READY`
- [GET /api/v1/reports/daily](https://app.notion.com/p/784a84e7e62e4d7d9d30b225f12e78c7?pvs=204) — `REVIEW`
- [GET /api/v1/sessions/active](https://app.notion.com/p/ae40117948584658b7fc638dc32afdfc?pvs=204) — `READY`
- [GET /api/v1/sessions/{sessionId}](https://app.notion.com/p/fc5cab61786c4038a0de5b48daa0a1ed?pvs=204) — `READY`
- [GET /api/v1/users/me/consents](https://app.notion.com/p/eea06d0fde624e079eac5fec713c0bb7?pvs=204) — `READY`
- [GET /api/v1/sessions/{sessionId}/interventions](https://app.notion.com/p/24690cf40b1149419938913acd67d2fd?pvs=204) — `READY`
- [PATCH /api/v1/goals/{goalId}](https://app.notion.com/p/56500ee7deab4dc4967f6a79d42e61eb?pvs=204) — `READY`
- [POST /api/v1/auth/logout](https://app.notion.com/p/1bff2fbe87234dd5917de681bb9b348f?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/end](https://app.notion.com/p/7f7c6ba7e1394e00b8ad2d89d804f148?pvs=204) — `READY`
- [GET /api/v1/sessions/{sessionId}/summary](https://app.notion.com/p/4ac529e866c54cdd88c03ce158fa171f?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/analysis-runs](https://app.notion.com/p/58cf7aade6614fc9a00e248affb052f9?pvs=204) — `READY`
- [DELETE /api/v1/extension-pairings/{extensionDeviceId}](https://app.notion.com/p/b30f071c1a454e88a263619fe6d8b2a4?pvs=204) — `READY`
- [DELETE /api/v1/goals/{goalId}](https://app.notion.com/p/ce99321b03a94224a5ce760a1647f75d?pvs=204) — `READY`
- [GET /api/v1/sessions/{sessionId}/learning-summary](https://app.notion.com/p/d6c152f985d64d65b3aaa690f46b7455?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/local-events](https://app.notion.com/p/ee5857108428428cb6f6c7c039d01a79?pvs=204) — `READY`
- [DELETE /api/v1/devices/{deviceId}](https://app.notion.com/p/2f151d782dfb42a09a37feef0cfca5e6?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/resume](https://app.notion.com/p/bef2b1225f1845bf87b23c10f0da2502?pvs=204) — `READY`
- [PUT /api/v1/users/me/onboarding-readiness](https://app.notion.com/p/3c7d51a848fe4361bbab82e162189360?pvs=204) — `READY`
- [GET /api/v1/users/me/analysis-excluded-domains](https://app.notion.com/p/3eb0b6e5e64c80f690f0f318f7387b75?pvs=204) — `READY`
- [GET /api/v1/sessions/{sessionId}/analysis-runs](https://app.notion.com/p/d6db8ba5fb1e44d8b3eb6cb08a43a2b1?pvs=204) — `READY`
- [POST /api/v1/auth/google/exchange](https://app.notion.com/p/00f3391cae454614bf51bffbe995f0dc?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/extend](https://app.notion.com/p/f6018f30577144349fe96cbfae9f51ea?pvs=204) — `READY`
- [PUT /api/v1/users/me/focus-settings](https://app.notion.com/p/8031378425694f568e920b90dd39241e?pvs=204) — `READY`
- [GET /api/v1/users/me/focus-settings](https://app.notion.com/p/3e8cee55cf7b40dabb01ac1c97ddb7ce?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/pause](https://app.notion.com/p/723db6ed20044abfba21dede91ca4319?pvs=204) — `READY`
- [PUT /api/v1/users/me/consents/{consentType}](https://app.notion.com/p/37896d8d5911444484b60fd4795318c2?pvs=204) — `READY`
- [GET /api/v1/reports/calendar](https://app.notion.com/p/3e70b6e5e64c800298a7c62768d10853?pvs=204) — `REVIEW`
- [GET /api/v1/sessions](https://app.notion.com/p/e2c500f5a9074ef49573d32635aaea85?pvs=204) — `READY`
- [PUT /api/v1/users/me/analysis-excluded-domains](https://app.notion.com/p/3eb0b6e5e64c806a9554d905b74e3b57?pvs=204) — `READY`
- [POST /api/v1/sessions/{sessionId}/heartbeats](https://app.notion.com/p/17afeb0a3c6c482fb9d2ffabd68f2e59?pvs=204) — `READY`

### Notion 개인 메모·아카이브

- 팀 개인 페이지 5개와 하위 메모 13개: 개인 작업·과거 FocusPath 기획 메모로, 현재 제품/API 정본과 로컬 MD 대응 문서는 없다.
- Archive의 폐기 설계서 3개와 이전 API 명세 1개: 현재 개발 계약에서 제외했다.
- 디자인 시스템, 디자인 레퍼런스, 디자인 변경 이력 Notion 페이지는 비어 있다. 해당 주제의 로컬 문서는 구현 지침으로만 유지한다.

### 로컬 전용 Markdown

아래 60개는 Notion과 같은 제목의 정본이 없는 개발 프로세스·템플릿·영역 작업 기록·후속 탐색 문서다. 계약값을 담은 문서는 이번 대조에서 Notion 현재 정책에 맞게 고쳤거나 후속 초안임을 표시했다.

- `.github/GITHUB_REPOSITORY.md`
- `.github/ISSUE_LABELS.md`
- `.github/ISSUE_TEMPLATE.md`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `AGENTS.md`
- `AI/docs/NEXT_TASK.md`
- `AI/docs/evaluation_spec.md`
- `AI/docs/features/README.md`
- `AI/docs/feedback_personalization_spec.md`
- `AI/docs/focus_session_agent_spec.md`
- `AI/docs/templates/DEVELOPMENT_ERROR_TEMPLATE.md`
- `AI/docs/templates/FEATURE_PLAN_TEMPLATE.md`
- `AI/docs/templates/RESULT_REPORT_TEMPLATE.md`
- `apps/desktop/README.md`
- `apps/desktop/docs/DEVELOPMENT_GUIDE.md`
- `apps/desktop/docs/IPC_CONTRACT.md`
- `apps/desktop/docs/NEXT_TASK.md`
- `apps/desktop/docs/README.md`
- `apps/desktop/docs/features/README.md`
- `apps/desktop/docs/templates/DEVELOPMENT_ERROR_TEMPLATE.md`
- `apps/desktop/docs/templates/FEATURE_PLAN_TEMPLATE.md`
- `apps/desktop/docs/templates/RESULT_REPORT_TEMPLATE.md`
- `apps/docs/API_CLIENT_RULES.md`
- `apps/docs/COMPONENT_CONVENTION.md`
- `apps/docs/DEPENDENCY_POLICY.md`
- `apps/docs/FOLDER_STRUCTURE.md`
- `apps/docs/FORM_VALIDATION.md`
- `apps/docs/MOCKING_GUIDE.md`
- `apps/docs/README.md`
- `apps/docs/STATE_MANAGEMENT.md`
- `apps/extension/README.md`
- `apps/extension/docs/DEVELOPMENT_GUIDE.md`
- `apps/extension/docs/MESSAGE_CONTRACT.md`
- `apps/extension/docs/NEXT_TASK.md`
- `apps/extension/docs/README.md`
- `apps/extension/docs/SERVICE_WORKER_LIFECYCLE.md`
- `apps/extension/docs/features/README.md`
- `apps/extension/docs/templates/DEVELOPMENT_ERROR_TEMPLATE.md`
- `apps/extension/docs/templates/FEATURE_PLAN_TEMPLATE.md`
- `apps/extension/docs/templates/RESULT_REPORT_TEMPLATE.md`
- `docs/BRANCH_STRATEGY.md`
- `docs/COMMIT_CONVENTION.md`
- `docs/ENVIRONMENT.md`
- `server/HELP.md`
- `server/docs/README.md`
- `server/docs/features/README.md`
- `server/docs/rules/api-design.md`
- `server/docs/rules/branch-workflow.md`
- `server/docs/rules/code-review-isolation.md`
- `server/docs/rules/code-review-template.md`
- `server/docs/rules/code-style.md`
- `server/docs/rules/migration-convention.md`
- `server/docs/rules/notion-api-spec.md`
- `server/docs/rules/progress-tracking.md`
- `server/docs/rules/test-convention.md`
- `server/docs/templates/BACKEND_ISSUE_TEMPLATE.md`
- `server/docs/templates/DEVELOPMENT_ERROR_TEMPLATE.md`
- `server/docs/templates/FEATURE_PLAN_TEMPLATE.md`
- `server/docs/templates/RESULT_REPORT_TEMPLATE.md`
- `server/src/main/resources/db/migration/README.md`

## 발견해 수정한 계약 충돌

1. `EXCLUDED`에서는 화면 콘텐츠 분석만 중지하고 타이머와 동의된 카메라 분석을 유지한다. `PRIVACY_BLOCKED`에서는 카메라도 중지한다.
2. 제외 도메인의 정본은 사용자별 Server 목록이며 Extension `chrome.storage.local`은 캐시다. 목록 동기화 실패 시 화면 콘텐츠 수집을 보류한다.
3. 목표 명확화 endpoint를 `POST /api/v1/goals/clarify`로 맞추고 `CLEAR`, `AMBIGUOUS`, `MULTI_GOAL`, `UNKNOWN_TERM`, 질문 최대 2회를 반영했다.
4. 카메라 로컬 분석은 선택 동의 MVP다. 최근 7일 개인화 휴식 추천은 P1 후보이며 사용자 AI 판정 수정·저장은 MVP API에 없다.
5. heartbeat는 10초 주기, 60초 무응답 후보, 기준 시각부터 총 180초 무응답 시 `ABANDONED` 확정으로 통일했다.
6. 일간 리포트·달력은 `ENDED`와 서버가 확정한 `ABANDONED`의 검증된 시간을 고정 `studyDate`에 귀속한다.
7. MVP 학습 정리에서는 자동 복습 질문, 편집·삭제·재생성을 제외했다.
8. Notion QA에서 Google OAuth 재인증과 FocusOn refresh 실패를 분리했고, 주간 리포트 `X-Device-Id`, 일간 리포트 종료 상태·저장 테이블 표현도 수정했다.

## 남은 검토 상태

- Notion 제품·정책 상세 문서 다수가 `REVIEW`다. 문서 간 충돌을 고쳤지만 이 상태는 팀 검토나 구현 검증을 대신하지 않는다.
- API `POST /api/v1/goals/clarify`, `GET /api/v1/reports/daily`, `GET /api/v1/reports/calendar`, `GET /api/v1/reports/weekly`는 `REVIEW`다.
- 빈 디자인 페이지, 개인 메모, 아카이브 문서는 내용상 1:1 동기화 대상이 아니다.
