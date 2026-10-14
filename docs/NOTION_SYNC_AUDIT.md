# Notion ↔ 로컬 Markdown 문서 대조 기록

- 기준일: 2026-10-12
- 작업 Issue: [#23](https://github.com/What-a-move/FocusOn/issues/23)
- 범위: FocusOn Notion 제품·정책·디자인·QA 문서 32개, API 명세 42개, 기존 로컬 Markdown 109개(이 감사 문서 제외)
- 정본: 제품 정책은 현재 FocusOn Notion 문서, endpoint 계약은 Notion `FocusOn API 명세서` 데이터베이스다. 로컬 MD는 개발 요약·규칙·초안이다.
- 생성물·작업 기록인 `.handoff`, `build`와 Git 메타데이터는 소스 문서 목록에서 제외했다.

## Notion 제품·정책·디자인·QA 페이지

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

## API 명세 42개

`docs/API_CONTRACT.md`와 **Method + 경로 42개가 모두 일치**한다. 경로 누락·추가는 0건이다. 42개 항목은 Request, Response, 권한·소유권, 출시 범위, 오류 처리가 채워져 있다.

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

## Notion 개인 메모·아카이브

- 팀 개인 페이지 5개와 하위 메모 13개: 개인 작업·과거 FocusPath 기획 메모로, 현재 제품/API 정본과 로컬 MD 대응 문서는 없다.
- Archive의 폐기 설계서 3개와 이전 API 명세 1개: 현재 개발 계약에서 제외했다.
- 디자인 시스템, 디자인 레퍼런스, 디자인 변경 이력 Notion 페이지는 비어 있다. 해당 주제의 로컬 문서는 구현 지침으로만 유지한다.

## 로컬 전용 Markdown

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
