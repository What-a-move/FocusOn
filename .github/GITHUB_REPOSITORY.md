# FocusOn GitHub 저장소 정보

> 팀원 GitHub 계정과 저장소 운영 정보를 기록하는 문서다.
> 실제 GitHub 권한과 Branch 보호 설정을 확인한 뒤 체크한다.

## 기본 저장소

-   Organization 또는 Owner: `What-a-move`
-   Repository: `FocusOn`
-   Repository URL: `https://github.com/What-a-move/FocusOn`
-   최종 Branch: `main`
-   GitHub 기본 Branch: `dev`
-   통합 개발 Branch: `dev`
-   작업 Branch: `Issue별 생성`

## Branch 운영 전략

FocusOn은 아래 구조로 운영한다.

```text
main
└── dev
    └── Issue별 작업 Branch
```

작업 순서:

```text
Issue 생성
→ dev 최신화
→ Issue별 Branch 생성
→ 개발·Commit·Push
→ Issue Branch → dev PR
→ Review·Merge
→ dev 안정화
→ dev → main PR
→ 최종 Merge
```

Branch 이름 형식:

```text
종류/이슈번호-영역-작업명
```

예시:

```text
feat/12-desktop-study-timer
fix/24-server-session-error
docs/40-common-workflow
```

자세한 규칙은 [`docs/BRANCH_STRATEGY.md`](../docs/BRANCH_STRATEGY.md)를 확인한다.

## 팀원 GitHub 계정

| 이름   | GitHub ID     | GitHub URL                       | 담당 영역           |
| ------ | ------------- | -------------------------------- | ------------------- |
| 허재원 | `ja2x0n`      | `https://github.com/ja2x0n`      | Desktop / Extension |
| 양원우 | `wonu1016`    | `https://github.com/wonu1016`    | Server              |
| 박진욱 | `parkjinuk09` | `https://github.com/parkjinuk09` | Server              |
| 김성현 | `kshi3430`    | `https://github.com/kshi3430`    | AI                  |
| 이재빈 | `lyg123d`     | `https://github.com/lyg123d`     | AI                  |

## 저장소 구조

```text
FocusOn/
├── apps/
│   ├── desktop/
│   └── extension/
├── packages/
│   └── shared-types/
├── server/
├── AI/
├── docs/
└── .github/
```

## 담당 영역별 작업 위치

| 담당 영역        | 작업 경로                | 담당자          |
| ---------------- | ------------------------ | --------------- |
| Desktop          | `apps/desktop/`          | 허재원          |
| Chrome Extension | `apps/extension/`        | 허재원          |
| Shared Types     | `packages/shared-types/` | 허재원          |
| Server           | `server/`                | 박진욱 / 양원우 |
| AI               | `AI/`                    | 김성현 / 이재빈 |
| 공통 문서        | `docs/`                  | 허재원          |

## GitHub 권한 및 보호 설정 확인

-   [ ] 팀원 전원이 저장소에 초대되었다.
-   [ ] 팀원 전원이 자신의 GitHub ID로 초대받았다.
-   [ ] `main` Branch 보호 규칙이 적용되었다.
-   [ ] `dev` Branch 보호 규칙이 적용되었다.
-   [ ] `main` 직접 Push가 제한되었다.
-   [ ] `dev` 직접 Push가 제한되었다.
-   [ ] PR 생성과 Review 권한을 확인했다.
-   [ ] 저장소 URL을 루트 `README.md`와 팀 문서에 공유했다.

## GitHub 저장소 설정 기준

### 최초 Branch 설정

1. `main`을 생성한다.
2. `main`에서 `dev`를 생성한다.
3. GitHub 저장소의 기본 Branch를 `dev`로 설정한다.
4. 최종 발표·배포 시에는 `dev → main` PR을 생성한다.

### Branch 보호 설정 방법

GitHub 저장소에서 `Settings → Branches` 또는 `Settings → Rules → Rulesets`로 이동해 `main`과 `dev`에 각각 보호 규칙을 추가한다.

-   `main`: PR 없이 직접 Merge 금지, Review 후 Merge, 직접 Push 금지
-   `dev`: Issue Branch에서 PR로만 반영, Review 후 Merge, 직접 Push 금지
-   두 Branch 모두 관리자 우회 금지를 권장한다.
-   CI가 준비되면 테스트 통과를 필수 상태 검사로 추가한다.

### 일상 작업

```bash
git checkout dev
git pull origin dev
git checkout -b feat/12-desktop-study-timer
git push -u origin feat/12-desktop-study-timer
```

작업 Branch의 PR은 `dev`를 대상으로 생성하고, 최종 통합 PR만 `main`을 대상으로 생성한다.

### `main` 보호

-   PR 없이 직접 Merge 금지
-   최소 1명 이상의 Review 필요
-   상태 검사 통과 후 Merge
-   관리자도 보호 규칙 우회 금지 권장

### `dev` 보호

-   Issue Branch에서 PR로만 반영
-   최소 1명 이상의 Review 필요
-   상태 검사 통과 후 Merge
-   직접 Push 금지

## Issue Label

Issue에는 아래 3종류의 Label을 적용한다.

-   영역: `area: frontend`, `area: backend`, `area: ai`, `area: common`
-   유형: `type: feature`, `type: bug`, `type: refactor`, `type: docs`
-   우선순위: `priority: high`, `priority: medium`, `priority: low`

상세 목록은 [`ISSUE_LABELS.md`](ISSUE_LABELS.md)를 확인한다.

## PR 제목 Prefix

| 영역              | Prefix     | 예시                         |
| ----------------- | ---------- | ---------------------------- |
| Desktop·Extension | `[FE]`     | `[FE] 학습 세션 타이머 구현` |
| Server            | `[BE]`     | `[BE] 학습 세션 API 구현`    |
| AI                | `[AI]`     | `[AI] 화면 관련성 분석 연결` |
| 공통              | `[COMMON]` | `[COMMON] 공통 타입 추가`    |

## main 직접 Push 금지 설정

문서 작성만으로 GitHub 보호가 적용되지는 않는다. 저장소 관리자가 GitHub에서 아래 설정을 적용한다.

1. `Settings → Rules → Rulesets → New branch ruleset`으로 이동한다.
2. 대상 Branch에 `main`을 지정한다.
3. `Require a pull request before merging`을 켜고 승인 인원을 1명 이상으로 설정한다.
4. `Block force pushes`, `Restrict deletions`, 관리자 우회 금지를 설정한다.
5. `dev`에도 같은 방식으로 보호 규칙을 적용한다.
6. CI가 준비되면 통과해야 하는 상태 검사도 필수로 지정한다.

최종 규칙은 `main`과 `dev` 모두 PR을 통해서만 변경되도록 한다.
