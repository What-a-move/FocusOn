# FocusOn 프로젝트 컨텍스트

이 문서는 새 채팅이나 새 담당자가 FocusOn의 목적과 문서 구조를 빠르게 파악하기 위한 시작점이다. 현재 상태는 2026-10-12 저장소 문서를 기준으로 요약했다. 상세 요청·응답, DB 컬럼, 화면 흐름은 각 정본 문서에서 확인한다. 설계 문서에 적혀 있어도 코드로 구현됐다고 간주하지 않는다.

## 1. 서비스와 MVP 흐름

FocusOn은 macOS Desktop 앱과 Chrome Extension으로 사용자의 학습 활동을 관찰하고, 세션 시작 전에 정한 **하나의 학습 목표**와 현재 활동의 관련성을 판단해 지속적인 이탈을 알려주는 학습 보조 서비스다. 목표 달성 여부나 집중력 점수를 채점하지 않는다.

```text
Google 로그인 → 동의·권한·Extension 연결 → 분석 제외 사이트 설정(등록 또는 빈 목록 건너뛰기)
→ 목표 설정
→ 집중 세션 시작 → 화면 관련성 분석·개입
→ 사용자 종료 또는 시간 연장 → 세션 리포트·일간 리포트·학습 정리
```

카메라 신호의 출시 포함 여부는 검토 중이다. 기능이 포함되면 사용자의 별도 동의를 받고, 거부하거나 권한을 철회해도 화면 분석 기반 세션을 유지한다. 화면·카메라 원본을 장기 저장하거나 외부로 전송하지 않는다.

MVP의 조회 범위는 세션 상세, 일간 리포트, 집중 달력, 학습 정리다. 긴 세션 목록과 주간 리포트는 P1이다. 학습 정리는 `overview`, `keyConcepts`, `nextStartPrompt`를 제공하며 자동 복습 질문·퀴즈는 MVP에서 제외한다.

## 2. 구성과 책임

| 영역 | 위치 | 책임과 현재 기술 |
| --- | --- | --- |
| Desktop | `apps/desktop/` | Electron·Next.js·React. 세션 UI, monotonic 타이머, macOS 권한·화면 수집, 로컬 카메라 분석 |
| Extension | `apps/extension/` | Chrome Manifest V3·Next.js·React. 현재 활성 탭 관찰, 허용된 DOM 수집과 viewport OCR 경로, 제외 도메인 적용 |
| Backend | `server/` | Java 25·Spring Boot 4.1.1. 인증·기기·세션·정책·리포트의 신뢰 원본, PostgreSQL 17·Flyway |
| AI | `AI/` | Python 서비스 설계 및 scaffold. 목표 보조와 관련성 판단; 실행 경계와 내부 상태는 구현 전 검토 중 |
| 공유 타입 | `packages/shared-types/` | Desktop·Extension에서 공유할 공개 TypeScript 계약. Server DTO의 런타임 검증을 대체하지 않음 |

Desktop과 Extension은 각각 독립된 token family로 Backend에 HTTPS 요청을 보낸다. 최초 연결에만 일회용 pairing code를 사용한다. AI 판단은 Backend의 검증과 Policy Engine을 거친 뒤 개입으로 이어진다. AI가 직접 타이머나 세션 상태를 변경하지 않는다.

Desktop Google 인증은 macOS 기본 시스템 브라우저와 임시 loopback callback을 사용한다. Electron Main이 OAuth·Token·인증 API 요청을 담당하고, Renderer에는 Preload의 타입이 정해진 IPC를 통해 Token 없는 인증 상태와 사용자 요약만 전달한다. 실제 구현은 후속 작업이다.

## 3. 구현할 때 놓치기 쉬운 확정 계약

### 세션과 시간

- 세션의 활성 상태는 사용자당 하나다. `RUNNING`, `PAUSED`, `AUTO_PAUSED`, `AWAITING_END_CONFIRMATION`, `ENDING`이 활성 세션 제약에 포함된다. `ENDED`와 `ABANDONED`는 최종 상태다.
- Desktop은 로컬 monotonic clock으로 진행 시간을 계산하고, Backend는 상태와 마지막으로 검증한 `accumulatedActiveMs`를 저장한다.
- MVP의 첫 세션 시간 선택지는 `30분`, `1시간`, `2시간`, `직접 입력`이다. 직접 입력은 `5~300분`이며, 무기한 세션은 `targetDurationMs = null` API 계약으로 유지하지만 온보딩 선택지로 제공하지 않는다.
- `targetDurationMs = null`은 종료 시간 없는 세션이다. 시간이 찼다는 이유로 자동 종료하지 않고 사용자가 직접 종료한다.
- 세션 생성·일시정지·재개·연장·종료에는 `Idempotency-Key`를 사용한다. 기존 세션 상태 명령에는 `If-Match-Version`도 사용한다.
- `RUNNING` 중 heartbeat는 10초마다 보낸다. 헤더는 `Authorization`, `X-Device-Id`만 사용한다. 과거 `clientObservedAt`은 무시하고 감소한 `accumulatedActiveMs`는 거절한다.
- 마지막 유효 heartbeat 또는 해당 RUNNING 구간의 시작 기준 시각 뒤 60초 무신호는 `ABANDONED` 후보, 총 3분 무신호는 `ABANDONED` 확정이다. 자동 종료는 마지막 서버 검증 누적값과 `endReason: HEARTBEAT_TIMEOUT`을 사용한다. 확정 뒤 도착한 heartbeat로 세션을 되살리지 않는다.
- Desktop 재실행으로 monotonic 기준점이 사라지면 마지막 서버 확정값으로 `AUTO_PAUSED`, `pauseReason: APP_RESTART` 처리한다. 사용자가 다시 시작해야 시간이 누적된다.

### 분석과 개인정보

- 공개 `relation`은 `RELATED`, `UNRELATED`, `UNCERTAIN`, `EXCLUDED`, `PRIVACY_BLOCKED` 5개 값을 사용한다. AI 관련성 판정은 앞의 3개만 반환하며 `EXCLUDED`와 `PRIVACY_BLOCKED`는 AI 호출 전 정책 차단 결과다. AI 내부 `SUPPORTING`은 공개 상태를 늘리지 않고 `RELATED`로 매핑한다.
- 신뢰도 `0.75` 미만은 `UNCERTAIN`으로 정규화한다. `UNCERTAIN`은 이탈 시간에 더하지 않고 30초까지 기존 구간을 보류하며, 30초를 넘으면 그 구간을 종료한다.
- 현재 로컬 API 요약과 Backend 규칙은 `RELATED` 확인 시 이탈 구간과 카메라 알림 후보를 즉시 초기화한다. 15초 미만 화면 전환은 이탈 구간으로 집계하지 않는다.
- 개입 기준은 `GENTLE` 연속 120초, `NORMAL` 최근 10분 안의 30초 이상 이탈 구간 2회, `ACTIVE` 연속 30초다. 같은 세션·개입 유형의 cooldown은 5분이다.
- 개입 정책 버전은 세션 시작 시 `interventionPolicyVersion`으로 고정하고 분석·개입 이력에 같은 `policyVersion`을 기록한다.
- 분석 제외 목록에는 정규화된 도메인 `host`만 저장한다. 경로·query·페이지 내용은 저장하지 않는다. 온보딩에서 제외 사이트 설정 단계를 반드시 거치며, 사이트를 등록하거나 빈 목록으로 건너뛰면 해당 단계와 온보딩이 완료된다. 완료 후 설정에서 추가·삭제할 수 있다. 제외 화면에서는 콘텐츠 분석을 중단하고 타이머는 유지한다.
- DOM·OCR 원문, URL 전문, 스크린샷, 카메라 프레임, token·비밀번호 같은 비밀값은 DB·로그·오프라인 큐에 보관하지 않는다. 민감 화면은 콘텐츠 추출과 AI 요청 전에 차단한다.

### 리포트와 데이터 수명

- 세션 시작 시 당시 사용자 시간대의 `studyDate`를 고정한다. 자정을 넘어도 세션 전체를 시작일에 귀속한다.
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

- Backend는 Spring Boot·PostgreSQL·Docker Compose·Flyway 기반이 마련됐지만 Controller·Service·Repository·Entity와 도메인 API는 아직 구현 전이다. 우선순위는 공통 응답·오류 처리, 첫 migration, 인증, 세션 MVP API다.
- Desktop은 기본 UI, Electron Main·Preload 실행 기반, 개발·정적 화면 로딩, 기본 Window 생명주기와 인증 IPC 경계가 있다. 실제 Google OAuth·Token·Backend 연동, Extension의 현재 탭 추적, pairing, 세션 동기화와 분석 연결은 후속 구현 대상이다.
- AI는 설계 문서와 scaffold 중심이다. 최근 AI v1.1 문서에는 목표 보조 상태, 분석 제외 모드, 개인화 휴식, 학습 노트 등 추가 제안이 있다. API 정본과 합의되지 않은 항목을 기존 MVP 계약으로 취급하지 않는다.
- **검토 필요:** 카메라의 출시 범위와 구현 기술 표기가 문서마다 다르다. 공통 정책·제품 기획은 선택 동의 카메라를 MVP에 포함하고 macOS 로컬 분석을 설명한다. 일부 앱·AI 문서는 MediaPipe 또는 출시 보류를 적고 있다. 카메라 구현 작업 전에 해당 정본과 팀 결정을 다시 확인한다.
- **검토 필요:** AI v1.1의 `ANALYSIS_AND_TIME` 제외 모드, 학습 노트 재생성, 사용자 피드백 개인화와 공개 세션 모델은 현재 로컬 공통 API 요약과 차이가 있다. 관련 작업에서는 AI 초안을 그대로 공개 API나 ERD에 반영하지 않는다.

작업을 마칠 때 이 문서의 **제품 범위·확정 계약·문서 경로**가 달라졌다면 함께 갱신한다. 일상적인 진행 상태는 각 영역의 `CONTEXT.md`와 `NEXT_TASK.md`에서 갱신한다.
