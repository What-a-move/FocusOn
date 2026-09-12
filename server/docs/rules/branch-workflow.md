# Backend 기능 구현 워크플로우 (상세)

> `docs/WORKFLOW.md`, `docs/BRANCH_STRATEGY.md`(루트, 팀 전체 공통 규칙)를 대체하지 않는다. 이 문서는 그 두 문서를 Backend(Server) 작업에 맞춰 단계별로 더 상세히 풀어쓴 것이다. 두 문서와 내용이 어긋나면 루트 문서를 우선한다.
> FocusOn 저장소의 실제 브랜치는 `main`/`dev` 2단계다(GONE의 `main`/`dev`/`staging` 3단계 구조와 다르다). `staging` 관련 규칙은 이 프로젝트에 적용하지 않는다.
> CI(GitHub Actions)는 현재 이 저장소에 구성되어 있지 않다. 도입되기 전까지 "CI 통과 확인" 단계는 로컬 빌드·테스트 확인으로 대체한다.

## 🚨 한 번에 하나의 기능만 + 진행 상태 추적

- 항상 "Issue 1개 ↔ Branch 1개 ↔ 기능 1개"만 동시에 진행한다. 진행 중인 Issue가 있으면 새 Issue의 기획서 작성·브랜치 생성·구현을 시작하지 않는다.
- 진행 상태는 대화 기록이 아니라 **Issue 본문의 체크리스트**로 관리한다. 아래 0~11단계를 Issue 생성 시 체크박스로 포함시키고, 각 단계가 끝날 때마다 갱신한다. 세션이 끊겨도 이 체크박스를 읽어서 이어간다.
- QA 중 발견된 버그는 새 Issue를 파지 않고 같은 브랜치에서 고친다. 새 Issue는 ① 머지 후 발견된 회귀, ② 기획서 범위 밖의 스코프 크립, 이 두 경우에만 만든다.

## 🚫 dev 직접 push 금지

- `docs/BRANCH_STRATEGY.md`에 이미 명시된 규칙이다: `dev`에는 어떤 커밋도 직접 push하지 않는다. 코드 변경이든 문서(기획서)뿐인 변경이든 예외 없이 PR을 거친다.
- 문서만 바뀌는 변경도 "리뷰가 필요 없다"고 임의로 판단해 `dev`에 직접 커밋하지 않는다. 별도 `docs/#{이슈번호}-{slug}` 브랜치를 분기해 PR로 머지한다(현재 Issue #4 → `docs/#4-server-development-rules` 브랜치가 이 규칙을 따른 예시다).

## 0. 사전 준비 — 기존 코드·문서 읽기

- 작업 전 `server/docs/CONTEXT.md` → `NEXT_TASK.md` → `DECISION_RECORD.md` 순으로 읽는다(`AGENTS.md`에 이미 명시된 순서).
- 관련 도메인 패키지(예: `auth`, `session`, `goal`)가 이미 있으면 기존 Controller/Service/DTO/Exception 구조를 먼저 읽고 재사용한다. 새 패턴을 임의로 만들지 않는다.
- 직전 Issue의 문서 반영 단계(아래 10~11단계)가 실제로 끝났는지 확인한다.

## 1. Issue 생성

- `.github/ISSUE_TEMPLATE.md` 기반으로 생성한다(FocusOn은 `feature.md`/`bug.md` 분리 없이 템플릿 하나를 공용으로 쓴다).
- Label은 `.github/ISSUE_LABELS.md`의 "AI 자동 적용 규칙"에 따라 영역·유형·우선순위를 함께 지정한다.
- Issue 본문에 0~11단계 체크리스트를 포함시킨다.
- **Issue 생성은 GitHub에 실제로 올라가는 행위이므로, 작업자에게 제목·라벨·본문 초안을 먼저 보여주고 승인받은 뒤에만 생성한다.**

## 2. 기획서 작성 (`server/docs/features/{기능명}-PLAN.md`)

- `server/docs/templates/FEATURE_PLAN_TEMPLATE.md`를 기준으로 작성한다.
- 엔드포인트를 설계하기 전 `server/docs/rules/api-design.md`의 6원칙을 검토하고, 기획서의 관련 절에 판단 근거를 남긴다.
- 기능명은 kebab-case를 쓴다(`docs/DOCUMENTATION_RULES.md` 규칙). 예: `study-session-timer-PLAN.md`.
- Issue 1개당 엔드포인트 1~2개, 또는 기존 로직 수정 1건으로 범위를 좁힌다. 범위가 넘치면 Issue를 분리한다.

## 3~6. 기획서 검토 → 수정 → 승인

- 기획서 작성 후 작업자 또는 지정된 리뷰어의 검토를 요청한다. 검토 전에는 구현을 시작하지 않는다.
- 지적 사항은 즉시 반영하고 재검토를 요청한다.
- 명시적 승인이 있어야 다음 단계(브랜치 생성)로 진행한다.

## 7. 브랜치 생성

- `dev`에서 분기한다.
- 네이밍: `{종류}/#{이슈번호}-{짧은-영문-slug}` (예: `feat/#12-study-session-timer`, `fix/#24-session-sync-error`, `docs/#4-server-development-rules`).
- 종류는 `docs/COMMIT_CONVENTION.md`의 type과 동일한 집합을 쓴다: `feat`/`fix`/`refactor`/`docs`/`chore`/`test`.

## 8. 기능 구현

- 기획서에 정의된 범위를 벗어나지 않는다. 범위 밖 리팩터링·개선은 별도 Issue로 분리 제안한다.
- 작은 단위로 커밋한다(예: Entity/마이그레이션 → Service → Controller → 테스트 순서).
- 커밋 규칙: `docs/COMMIT_CONVENTION.md`. 코드 스타일: `server/docs/rules/code-style.md`. 테스트: `server/docs/rules/test-convention.md`.
- **기획서와 실제 코드가 달라지는 경우**:
  - 구현 세부사항 변경(DTO 필드 통합, 내부 메서드 시그니처 등 — 승인된 API 계약에 영향 없음): 기획서를 즉시 수정해 실제 구현과 일치시키고 계속 진행한다.
  - 설계·정책 변경(엔드포인트 경로·메서드, 요청·응답 스키마, 인증 요구사항, ErrorCode 정책 등): 구현을 중단하고 4단계로 되돌아가 재승인을 받는다. 애매하면 설계·정책 변경으로 간주한다.

## 9. 코드 리뷰 (자체 점검)

- 기능 구현이 끝나면 QA 전에 diff를 리뷰한다. 컨텍스트가 격리된 방식으로 진행한다 — 상세: `server/docs/rules/code-review-isolation.md`.
- 결과는 QA와 합치지 않고 `server/docs/features/{기능명}-code-review.md`로 문서화한다 — 형식: `server/docs/rules/code-review-template.md`.

## 10. QA

- `./server/gradlew -p server build test` 로컬 통과를 확인한다(`docs/ENVIRONMENT.md` 실행 명령 기준).
- CI가 도입되면 CI 통과 여부도 함께 확인한다(현재는 로컬 통과로 대체).
- 기획서에 정의된 엔드포인트별 정상·에러 케이스를 직접 검증한다. 체크리스트는 `docs/QA_CONVENTION.md`(공통) + `server/docs/templates/RESULT_REPORT_TEMPLATE.md`를 따른다.
- 발견된 문제는 심각도별로 정리해 `server/docs/features/{기능명}-QA.md`에 기록한다: Critical(데이터 유실·보안/인증 우회) / High(핵심 플로우 실패) / Medium(예외 케이스 미처리) / Low(사소한 개선).

## 11. PR 생성 및 이후 문서 반영

- QA와 코드 리뷰가 끝난 뒤에만 PR을 생성한다. 상세 규칙: PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 그대로 쓴다.
- **PR 생성도 Issue 생성과 마찬가지로 GitHub에 실제로 올라가는 행위이므로, 작업자에게 제목·본문 초안을 먼저 보여주고 승인받은 뒤에만 생성한다. 승인 없이 Merge하지 않는다.**
- FocusOn 저장소의 default 브랜치는 `main`이고 feature PR은 `dev`로 머지되므로 GitHub의 "closes #N" 자동 종료는 이 시점에 발동하지 않는다. `dev`에 머지된 직후 관련 Issue를 직접 닫는다.
- PR이 `dev`에 머지된 게 확인되면, 이번에 추가·변경된 엔드포인트를 Notion `FocusOn API 데이터베이스`에 반영한다 — 형식: `server/docs/rules/notion-api-spec.md`.
- Postman은 이 프로젝트에서 아직 쓰지 않는다. API 수동 검증은 QA 단계에서 `curl` 또는 Swagger UI(`springdoc-openapi`, 서버 기동 후 `/swagger-ui`)로 진행한다. 팀이 Postman 도입을 결정하면 이 절을 갱신하고 `postman-collection-structure.md`를 새로 만든다.

## 관련 문서

- `docs/WORKFLOW.md`, `docs/BRANCH_STRATEGY.md` (루트, 팀 전체 공통 — 이 문서보다 우선)
- `server/docs/rules/progress-tracking.md`
- `server/docs/rules/code-review-isolation.md`, `code-review-template.md`
- `server/docs/rules/test-convention.md`
- `server/docs/rules/notion-api-spec.md`
