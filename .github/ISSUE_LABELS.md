# GitHub Issue Label 지침

> Issue를 생성하는 사람 또는 AI는 기능 기획서를 먼저 확인하고 아래 기준으로 Label을 선택한다.
> 이 문서 자체가 GitHub Label을 생성하거나 Issue에 Label을 붙이는 것은 아니다.

## 작업 순서

1. 관련 기능의 PLAN 문서를 확인한다.
2. 기획서의 담당 영역, 기능 범위, 개발 우선순위를 확인한다.
3. 예상 변경 파일과 작업 목적을 확인한다.
4. 영역·유형·우선순위 Label을 각각 선택한다.
5. Issue 생성 후 선택한 Label이 실제로 붙었는지 확인한다.

기획서가 없으면 먼저 담당 영역의 docs/templates/FEATURE_PLAN_TEMPLATE.md를 사용해 기획서를 작성한다.

## 필수 Label

모든 Issue에는 영역·유형·우선순위 Label을 각각 하나 이상 지정한다.

| 분류 | Label | 선택 기준 |
| --- | --- | --- |
| 영역 | area:desktop | apps/desktop 또는 Electron·macOS 작업 |
| 영역 | area:extension | apps/extension 또는 Chrome Extension 작업 |
| 영역 | area:server | server 또는 Spring Boot·DB·API 작업 |
| 영역 | area:ai | ai 또는 AI 분석·모델 작업 |
| 영역 | area:shared | packages 또는 여러 영역이 공유하는 작업 |
| 유형 | type:feature | 새로운 기능 추가 |
| 유형 | type:bug | 기존 기능의 오류 수정 |
| 유형 | type:refactor | 동작 변경 없는 구조 개선 |
| 유형 | type:docs | 문서 작성·수정 |
| 유형 | type:test | 테스트 추가·수정 |
| 유형 | type:chore | 설정·의존성·빌드 관련 작업 |
| 우선순위 | priority:mvp-core | MVP 핵심 기능 |
| 우선순위 | priority:mvp-basic | MVP 기본 기능 |
| 우선순위 | priority:enhancement | 개선·2차 개발·추가 작업 |

## 선택 규칙

- 영역 Label은 기획서의 담당 영역과 예상 변경 파일을 기준으로 선택한다.
- 여러 영역이 동시에 변경되면 영역 Label을 여러 개 붙인다.
- 유형 Label은 하나만 선택한다.
- 우선순위 Label은 하나만 선택한다.
- 다른 작업이 완료되어야 시작할 수 있으면 status:blocked를 추가한다.
- Label이 저장소에 없으면 임의로 이름을 바꾸지 말고 관리자에게 생성을 요청한다.

## 기획서에서 확인할 항목

| 기획서 항목 | 판단할 Label |
| --- | --- |
| 담당 영역 | 영역 Label |
| 기능 범위 | 유형 Label |
| 개발 우선순위 | 우선순위 Label |
| 예상 수정 파일 | 영역 Label 검증 |
| 선행 조건 | status:blocked 여부 |

## 예시

Desktop의 MVP 핵심 기능이면 다음과 같이 붙인다.

area:desktop
type:feature
priority:mvp-core

Spring Boot API 버그 수정이면 다음과 같이 붙인다.

area:server
type:bug
priority:mvp-basic

Desktop 화면, Server API, AI 분석이 함께 필요한 기능이면 영역 Label을 모두 붙인다.

area:desktop
area:server
area:ai
type:feature
priority:mvp-core

## 자동 적용의 한계

- Markdown 문서에 Label을 적는 것만으로는 GitHub가 자동 실행하지 않는다.
- 실제 Label은 저장소의 Issues → Labels에서 먼저 생성되어 있어야 한다.
- YAML Issue Form의 labels 속성을 사용하면 Issue 생성 시 기존 Label을 자동으로 붙일 수 있다.
- 현재 Markdown Issue 템플릿의 체크박스는 Label 자동 적용 기능이 아니다.
