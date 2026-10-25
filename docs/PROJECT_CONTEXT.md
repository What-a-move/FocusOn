# FocusOn 프로젝트 컨텍스트

이 문서는 새 채팅이나 새 담당자가 FocusOn의 목적과 문서 구조를 빠르게 파악하기 위한 시작점이다. 2026-10-25 저장소와 사용자가 확정·정정한 계약을 반영했다. 관련 Notion을 대조한 범위와 남은 충돌은 `docs/NOTION_SYNC_AUDIT.md`를 확인한다. 상세 요청·응답, DB 컬럼, 화면 흐름은 각 정본 문서에서 확인한다. 설계 문서에 적혀 있어도 코드로 구현됐다고 간주하지 않는다.

## 1. 서비스와 MVP 흐름

FocusOn은 macOS Desktop 앱과 Chrome Extension으로 사용자의 학습 활동을 관찰하고, 세션 시작 전에 정한 **하나의 학습 목표**와 현재 활동의 관련성을 판단해 지속적인 이탈을 알려주는 학습 보조 서비스다. 목표 달성 여부나 집중력 점수를 채점하지 않는다.

```text
Google 로그인 → 온보딩(닉네임 → Extension 연결 → 필수 권한·동의 → 분석 제외 사이트 설정 또는 빈 목록 건너뛰기)
→ `onboardingStatus: COMPLETED` → Desktop 대시보드
→ `오늘 학습`에서 목표·시간·개입 설정
→ 집중 세션 시작 → 화면 관련성 분석·개입
→ 사용자 종료 또는 시간 연장 → 세션 리포트·일간 리포트·학습 정리
```

노션의 현재 MVP는 선택 동의 기반 로컬 카메라 분석을 포함한다. 일부 영역의 보류·MediaPipe 제안은 아래 검토 항목과 구분한다. 거부하거나 권한을 철회해도 화면 분석 기반 세션을 유지하며, 화면·카메라 원본을 장기 저장하거나 외부로 전송하지 않는다.

MVP의 조회 범위는 세션 상세, 일간 리포트, 집중 달력, 학습 정리다. 긴 세션 목록과 주간 리포트는 P1이다. 학습 정리는 `overview`, `keyConcepts`, `nextStartPrompt`를 제공하며, 핵심 개념은 `{name,briefExplanation,sessionEvidence}` 객체 최대 5개다. 자동 복습 질문·퀴즈는 MVP에서 제외한다.

## 2. 구성과 책임

| 영역 | 위치 | 책임과 현재 기술 |
| --- | --- | --- |
| Desktop | `apps/desktop/` | Electron·Next.js·React. 세션 UI, monotonic 타이머, macOS 권한·화면 수집, 로컬 카메라 분석 |
| Extension | `apps/extension/` | Chrome Manifest V3·Next.js·React. 현재 활성 탭 관찰, 허용된 DOM 수집과 viewport OCR 경로, 제외 도메인 적용 |
| Backend | `server/` | Java 25·Spring Boot 4.1.1. 인증·기기·세션·정책·리포트의 신뢰 원본, PostgreSQL 17·Flyway |
| AI | `AI/` | Python 서비스 설계 및 scaffold. 목표 보조와 관련성 판단; 실행 경계와 내부 상태는 구현 전 검토 중 |
| 공유 타입 | `packages/shared-types/` | Desktop·Extension에서 공유할 공개 TypeScript 계약. Server DTO의 런타임 검증을 대체하지 않음 |

Desktop과 Extension은 각각 독립된 token family로 Backend에 HTTPS 요청을 보낸다. 최초 연결에만 일회용 pairing code를 사용한다. AI 판단은 Backend의 검증과 Policy Engine을 거친 뒤 개입으로 이어진다. AI가 직접 타이머나 세션 상태를 변경하지 않는다.

Desktop Google 인증은 macOS 기본 시스템 브라우저와 임시 loopback callback을 사용한다. Electron Main이 OAuth·Token·인증 API 요청을 담당하고, Renderer에는 Preload의 타입이 정해진 IPC를 통해 Token 없는 인증 상태와 사용자 요약만 전달한다. Main의 OAuth·Backend 인증 요청·Keychain 보관과 Renderer의 로그인·온보딩 시작·홈 화면 전환은 구현했으며, 실제 Google 계정·Backend 왕복 검증은 Backend 인증 API 병합 후 진행한다.

## 3. 구현할 때 놓치기 쉬운 확정 계약

### 세션과 시간

- 세션의 활성 상태는 사용자당 하나다. `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING`이 활성 세션 제약에 포함된다. `ENDED`와 `ABANDONED`는 최종 상태다.
- Desktop은 로컬 monotonic clock으로 진행 시간을 계산하고, Backend는 자신의 시계와 상태로 검증한 `accumulatedActiveMs`를 저장한다. 시작·종료·일시정지 시각은 서버 UTC로 확정한다.
- MVP의 첫 세션 시간 선택지는 `30분`, `1시간`, `2시간`, `직접 입력`이다. API도 5~300분의 분 단위 정수를 필수로 받으며 null·누락을 거절한다. 이전 무기한 세션 계약은 현재 MVP에서 사용하지 않는다.
- 세션 생성·일시정지·재개·연장·종료에는 `Idempotency-Key`를 사용한다. 기존 세션 상태 명령에는 `If-Match-Version`도 사용한다.
- `RUNNING` 중 heartbeat는 10초마다 보낸다. 헤더는 `Authorization`, `X-Device-Id`만 사용한다. 과거 `clientObservedAt`은 무시하고 감소한 `accumulatedActiveMs`는 거절한다.
- 마지막 유효 heartbeat 또는 해당 RUNNING 구간의 시작 기준 시각 뒤 60초 무신호는 `ABANDONED` 후보, 총 3분 무신호는 `ABANDONED` 확정이다. 자동 종료는 마지막 서버 검증 누적값과 `endReason: HEARTBEAT_TIMEOUT`을 사용한다. 확정 뒤 도착한 heartbeat로 세션을 되살리지 않는다.
- Desktop 재실행으로 monotonic 기준점이 사라지면 마지막 서버 확정값으로 `AUTO_PAUSED`, `pauseReason: APP_RESTART` 처리한다. 사용자가 다시 시작해야 시간이 누적된다.

### 분석과 개인정보

- 공개 `relation`은 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED` 5개 값을 사용한다. AI 관련성 판정은 앞의 3개만 반환하며 `EXCLUDED`와 `PRIVACY_BLOCKED`는 AI 호출 전 정책 차단 결과다. AI 내부 `SUPPORTING`은 공개 상태를 늘리지 않고 `RELATED`로 매핑한다.
- 신뢰도 `0.75` 미만은 `UNCERTAIN`으로 정규화한다. 새 정책 `v1`에서는 이탈 시간에 더하지 않고 현재 연속 구간을 끊는다. 30초 보류 후 이어붙이기는 이전 정책이다.
- 현재 로컬 API 요약과 Backend 규칙은 `RELATED` 확인 시 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만 화면 전환은 이탈 구간으로 집계하지 않는다.
- 개입 `v1`은 연속·반복 중 하나를 충족하면 후보가 된다. `GENTLE`: 연속 180초 초과 또는 최근 10분 내 60초 초과 구간 3회. `NORMAL`: 연속 60초 초과 또는 30초 초과 구간 2회. `ACTIVE`: 연속 30초 초과 또는 20초 초과 구간 2회. 숫자는 서버에서만 관리한다.
- 같은 세션·개입 유형의 서버 cooldown은 실제 알림 전송부터 5분이다. `확인했어` 후 5분 재집중 구간은 Desktop UI 상태이며 서버 상태나 AI 판정을 바꾸지 않는다. cooldown 후보를 나중에 일괄 발송하지 않는다.
- 서버가 세션 시작 시 현재 버전 `v1`을 `interventionPolicyVersion`으로 고정하고 분석·개입 이력에 같은 `policyVersion`을 기록한다. 배포·재개 때 진행 중 버전을 바꾸지 않는다.
- 분석 제외 목록에는 정규화된 도메인 `host`만 저장한다. 경로·query·페이지 내용은 저장하지 않는다. 온보딩에서 제외 사이트 설정 단계를 반드시 거치며, 사이트를 등록하거나 빈 목록으로 건너뛰면 해당 단계와 온보딩이 완료된다. 완료 후 설정에서 추가·삭제할 수 있다. 제외 화면에서는 콘텐츠 분석을 중단하고 타이머는 유지한다.
- 제외 앱 저장은 MVP 온보딩에 넣지 않는다. 동의 응답은 `currentVersion`, `consents[{consentType,isGranted,consentVersion,grantedAt,withdrawnAt}]`이며, 서버가 반환한 현재 버전을 저장 요청에 그대로 사용한다. 알림은 Desktop 필수 확인이지만 readiness API·DB에는 새 알림 필드를 추가하지 않는다.
- 온보딩은 닉네임, Extension 연결, 필수 권한·동의, 분석 제외 사이트 설정까지다. 목표·시간·개입 설정은 온보딩 완료 후 대시보드의 `오늘 학습` 흐름에서 진행하며 `onboardingStatus` 판정에 포함하지 않는다. 빈 제외 목록은 사용자가 건너뛰기를 선택한 경우에만 완료로 기록한다.
- DOM·OCR 원문, URL 전문, 스크린샷, 카메라 프레임, token·비밀번호 같은 비밀값은 DB·로그·오프라인 큐에 보관하지 않는다. 민감 화면은 콘텐츠 추출과 AI 요청 전에 차단한다.

### 리포트와 데이터 수명

- 세션 시작 요청의 기기 IANA `timeZone`을 snapshot으로 저장하고 그 시간대의 `studyDate`를 고정한다. 누락·잘못된 값은 `Asia/Seoul`이며 IP·VPN으로 추정하지 않는다. 자정을 넘어도 세션 전체를 시작일에 귀속한다. 세션용 `study_date`는 처음부터 NOT NULL이며 출시 전 기록 백필은 하지 않는다.
- 일간 리포트의 `sessions[]`는 `sessionId`, `goalTextSnapshot`, `startedAt`, `endedAt`, `accumulatedActiveMs`다. 현재 Notion 일간 API에도 이 배열 내부 필드가 반영돼 있다.
- 집중 달력은 종료 확정된 `ENDED`와 `ABANDONED`만 집계한다. 해당 날짜의 검증된 총 활성 시간이 `60,000ms` 이상이면 활동일이다.
- 학습 정리는 `ENDED` 이후 별도 비동기 작업이다. 정리 실패가 세션 종료나 기본 리포트 조회를 막지 않는다. `learningSummaryStatus`는 `QUEUED`, `GENERATING`, `READY`, `INSUFFICIENT_DATA`, `FAILED`다.
- MVP 구현 기준 보관 기간: 세션·리포트·복습 노트 1년, 분석 실행·복습 근거 90일, token family·pairing code 폐기·만료 후 30일, 보안 감사 로그 180일, 백업 최대 35일. 개인정보 처리방침과 법률 검토는 출시 전에 별도 확정한다.

## 4. 정본과 읽는 순서

새 채팅에서 노션 페이지를 다시 찾을 때는 아래 주소를 사용한다. 세부 정책이 필요한 작업에서는 링크의 현재 내용을 직접 읽는다.

| Notion 정본 바로가기 | 주소 |
| --- | --- |
| FocusOn 프로젝트 허브 | https://app.notion.com/p/FocusOn-3dc0b6e5e64c80db8224e86c188e7d67 |
| 문서 인덱스 | https://app.notion.com/p/3de0b6e5e64c80cd95cff031038ff972 |
| FocusOn API 명세서 | https://app.notion.com/p/3e00b6e5e64c80e89eb1e64017029210 |
| ERD | https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17 |
| 세션 상태 전이 정책 | https://app.notion.com/p/3de0b6e5e64c80cc8140f659abd82371 |
| AI 분석 계약 | https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c |

| 필요한 정보 | 우선 확인할 곳 |
| --- | --- |
| 제품 목표·MVP 사용자 경험 | Notion `FocusOn` 프로젝트 허브와 기능별 설계서 |
| 엔드포인트의 경로·Method·필드·권한·오류 | Notion `FocusOn API 명세서` 데이터베이스 |
| 상태 전이·오류·ERD·개입 의미 | Notion `공통 정책 · 기술 계약`의 해당 문서 |
| 로컬 공통 계약 요약 | `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md` |
| 코드 현황·다음 작업·확정 또는 제안 결정 | 작업 영역의 `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md` |
| Git·문서 운영 | `AGENTS.md`, `docs/WORKFLOW.md`, `docs/BRANCH_STRATEGY.md`, `docs/DOCUMENTATION_RULES.md` |

같은 항목이 충돌하면 해당 항목의 정본과 최근 **확정** 결정을 다시 확인한다. Notion 접근이 되지 않거나 최신 여부를 확인하지 못했으면 로컬 요약만으로 계약 변경을 확정하지 않는다. 제안·실험·초안은 명시적으로 분리한다. 코드 구현 여부는 파일과 실행 경로에서 직접 확인한다.

## 5. 현재 개발 상태와 검토 필요 항목

- Backend의 공통 응답·오류·Access JWT·인증 5개 테이블은 `dev`에 있다. Issue #49 / PR #51의 Google 교환·auth/me·요청 인증·초기 토큰 발급은 dev에 병합됐다. #59 작업 브랜치에는 refresh·회전·재사용 감지·logout을 구현하고 QA했다(dev 병합 전). 성공마다 token·family를 30일 연장하는 사용자 결정을 적용한다. pairing·기기 관리 API와 실제 Google 실계정 성공 왕복은 후속이다. 사용자·설정·목표는 Issue #38의 별도 담당 범위다.
- Desktop은 인증·온보딩 클라이언트와 홈 대시보드·리포트 IPC·목표 설정 클라이언트까지 구현했다(PR #42·#45, `0b88bb8`). Extension도 최초 pairing 요청·오류 처리·token 저장을 구현했다. 실제 Backend 왕복 QA, Extension의 현재 탭 추적, 후속 refresh·unlink, 세션 동기화와 분석 연결이 남아 있다.
- AI는 설계 문서와 scaffold 중심이다. 최근 AI v1.1 문서에는 목표 보조 상태, 분석 제외 모드, 개인화 휴식, 학습 노트 등 추가 제안이 있다. API 정본과 합의되지 않은 항목을 기존 MVP 계약으로 취급하지 않는다.
- **검토 필요:** 카메라의 출시 범위와 구현 기술 표기가 문서마다 다르다. 공통 정책·제품 기획은 선택 동의 카메라를 MVP에 포함하고 macOS 로컬 분석을 설명한다. 일부 앱·AI 문서는 MediaPipe 또는 출시 보류를 적고 있다. 카메라 구현 작업 전에 해당 정본과 팀 결정을 다시 확인한다.
- **검토 필요:** AI v1.1의 `ANALYSIS_AND_TIME` 제외 모드, 학습 노트 재생성, 사용자 피드백 개인화와 공개 세션 모델은 현재 로컬 공통 API 요약과 차이가 있다. 관련 작업에서는 AI 초안을 그대로 공개 API나 ERD에 반영하지 않는다.
- **정본 문서 보완 필요:** Notion AI 분석 계약 4.4·7장의 이전 개입 규칙, 일간 API의 meta 표기와 ERD·세션 정책의 과거 null 허용 표현은 [대조 기록](NOTION_SYNC_AUDIT.md)에서 추적한다. 새 세션 정책은 최신 확정 `v1`을 따른다.
- **관련 기능 구현 전 확인:** 연결 실패 횟수의 시도 문맥, 4번째 기기의 폐기 인증, rolling window 기준, heartbeat 관측·수신 시각 구분이 남아 있다. Google 교환·auth/me 준비와 별개이며 미정 값을 추측해 구현하지 않는다.

작업을 마칠 때 이 문서의 **제품 범위·확정 계약·문서 경로**가 달라졌다면 함께 갱신한다. 일상적인 진행 상태는 각 영역의 `CONTEXT.md`와 `NEXT_TASK.md`에서 갱신한다.
