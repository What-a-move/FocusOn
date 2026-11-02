# FocusOn 프로젝트 컨텍스트

이 문서는 새 채팅이나 새 담당자가 FocusOn의 목적과 문서 구조를 빠르게 파악하기 위한 **시작용 지도**다. 잘 바뀌지 않는 서비스 흐름·구성·문서 위치만 둔다. 계약 세부는 Notion 정본과 `docs/API_CONTRACT.md`에서, 현재 구현 상태는 각 영역의 `CONTEXT.md`·`NEXT_TASK.md`에서 확인한다(2026-10-26 축소: 금방 낡는 계약 요약과 진행 상태를 이 문서에서 뺐다). 설계 문서에 적혀 있어도 코드로 구현됐다고 간주하지 않는다.

## 1. 서비스와 MVP 흐름

FocusOn은 macOS Desktop 앱과 Chrome Extension으로 사용자의 학습 활동을 관찰하고, 세션 시작 전에 정한 **하나의 학습 목표**와 현재 활동의 관련성을 판단해 지속적인 이탈을 알려주는 학습 보조 서비스다. 목표 달성 여부나 집중력 점수를 채점하지 않는다.

```text
Google 로그인 → 온보딩(닉네임 → Extension 연결 → 필수 권한·동의 → 분석 제외 사이트 설정 또는 빈 목록 건너뛰기)
→ `onboardingStatus: COMPLETED` → Desktop 대시보드
→ `오늘 학습`에서 목표·시간·개입 설정
→ 집중 세션 시작 → 화면 관련성 분석·개입
→ 사용자 종료 또는 시간 연장 → 세션 리포트·일간 리포트·학습 정리
```

노션의 현재 MVP는 선택 동의 기반 로컬 카메라 분석(AVFoundation + Apple Vision)을 포함한다. 거부하거나 권한을 철회해도 화면 분석 기반 세션을 유지하며, 화면·카메라 원본을 장기 저장하거나 외부로 전송하지 않는다.

MVP의 조회 범위는 세션 상세, 일간 리포트, 집중 달력, 학습 정리다. 긴 세션 목록과 주간 리포트는 P1이다. 학습 정리는 `overview`, `keyConcepts`, `nextStartPrompt`를 제공하며, 핵심 개념은 `{name,briefExplanation,sessionEvidence}` 객체 최대 5개다. 자동 복습 질문·퀴즈는 MVP에서 제외한다.

## 2. 구성과 책임

| 영역 | 위치 | 책임과 현재 기술 |
| --- | --- | --- |
| Desktop | `apps/desktop/` | Electron·Next.js·React. 세션 UI, monotonic 타이머, macOS 권한·화면 수집, 로컬 카메라 분석 |
| Extension | `apps/extension/` | Chrome Manifest V3·Next.js·React. 현재 활성 탭 관찰, 허용된 DOM 수집, 제외·민감 페이지 차단 (viewport OCR은 P1) |
| Backend | `server/` | Java 25·Spring Boot 4.1.1. 인증·기기·세션·정책·리포트의 신뢰 원본, PostgreSQL 17·Flyway |
| AI | `AI/` | Python 서비스 설계 및 scaffold. 목표 보조와 관련성 판단; 실행 경계와 내부 상태는 구현 전 검토 중 |
| 공유 타입 | `packages/shared-types/` | Desktop·Extension에서 공유할 공개 TypeScript 계약. Server DTO의 런타임 검증을 대체하지 않음 |

Desktop과 Extension은 각각 독립된 token family로 Backend에 HTTPS 요청을 보낸다. 최초 연결에만 일회용 pairing code를 사용한다. AI 판단은 Backend의 검증과 Policy Engine을 거친 뒤 개입으로 이어진다. AI가 직접 타이머나 세션 상태를 변경하지 않는다.

Desktop Google 인증은 macOS 기본 시스템 브라우저와 임시 loopback callback을 사용한다. Electron Main이 OAuth·Token·인증 API 요청을 담당하고, Renderer에는 Preload의 타입이 정해진 IPC를 통해 Token 없는 인증 상태와 사용자 요약만 전달한다.

## 3. 정본과 읽는 순서

새 채팅에서 노션 페이지를 다시 찾을 때는 아래 주소를 사용한다. 세부 정책이 필요한 작업에서는 링크의 현재 내용을 직접 읽는다.

| Notion 정본 바로가기 | 주소 |
| --- | --- |
| FocusOn 프로젝트 허브 | https://app.notion.com/p/FocusOn-3dc0b6e5e64c80db8224e86c188e7d67 |
| 문서 인덱스 | https://app.notion.com/p/3de0b6e5e64c80cd95cff031038ff972 |
| FocusOn API 명세서 | https://app.notion.com/p/3e00b6e5e64c80e89eb1e64017029210 |
| ERD | https://app.notion.com/p/ERD-3de0b6e5e64c80a4bf5ef7ff3ac01f17 |
| 세션 상태 전이 정책 | https://app.notion.com/p/3de0b6e5e64c80cc8140f659abd82371 |
| AI 분석 계약 | https://app.notion.com/p/AI-3de0b6e5e64c80fbb0bccb985e38002c |
| 모듈 연동 로깅 규칙 | https://app.notion.com/p/4070b6e5e64c8156abc9d1bff00aef88 |

| 필요한 정보 | 우선 확인할 곳 |
| --- | --- |
| 제품 목표·MVP 사용자 경험 | Notion `FocusOn` 프로젝트 허브와 기능별 설계서 |
| 엔드포인트의 경로·Method·필드·권한·오류 | Notion `FocusOn API 명세서` 데이터베이스 |
| 상태 전이·오류·ERD·개입 의미 | Notion `공통 정책 · 기술 계약`의 해당 문서 |
| 로컬 공통 계약 요약 | `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PRIVACY.md` |
| 코드 현황·다음 작업·확정 또는 제안 결정 | 작업 영역의 `docs/CONTEXT.md`, `docs/NEXT_TASK.md`, `docs/DECISION_RECORD.md` |
| Git·문서 운영 | `AGENTS.md`, `docs/WORKFLOW.md`, `docs/BRANCH_STRATEGY.md`, `docs/DOCUMENTATION_RULES.md` |

같은 항목이 충돌하면 해당 항목의 정본과 최근 **확정** 결정을 다시 확인한다. Notion 접근이 되지 않거나 최신 여부를 확인하지 못했으면 로컬 요약만으로 계약 변경을 확정하지 않는다. 제안·실험·초안은 명시적으로 분리한다. 코드 구현 여부는 파일과 실행 경로에서 직접 확인한다.

## 4. 현재 상태는 어디서 보나

진행 상황은 이 문서에 적지 않는다. 같은 내용을 두 곳에 두면 한쪽이 낡기 때문이다.

| 알고 싶은 것 | 볼 곳 |
| --- | --- |
| 영역별 구현 상태·남은 QA | `apps/desktop/docs/CONTEXT.md`, `apps/extension/docs/CONTEXT.md`, `server/docs/CONTEXT.md`, `AI/docs/CONTEXT.md` |
| 다음 작업 | 각 영역의 `NEXT_TASK.md` |
| 확정·제안 결정 | 각 영역의 `DECISION_RECORD.md` |
| Notion과 로컬 문서의 남은 충돌 | `docs/NOTION_SYNC_AUDIT.md` |

이 문서는 **서비스 흐름·영역 구성·문서 위치**가 바뀔 때만 갱신한다.
