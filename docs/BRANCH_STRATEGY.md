# Branch 전략

FocusOn은 `main → dev → Issue별 작업 Branch` 구조로 운영한다.

## Branch 역할

- `main`: 최종 발표·배포 가능한 안정 Branch
- `dev`: 팀원들의 기능을 통합하는 개발 Branch
- Issue별 Branch: 특정 Issue 하나를 개발하는 작업 Branch

```text
main
└── dev
    ├── feat/12-desktop-study-timer
    ├── feat/18-extension-tab-tracking
    ├── fix/24-server-session-error
    └── feat/31-ai-screen-relevance
```

## 작업 흐름

```text
Issue 생성
  ↓
dev 최신화
  ↓
Issue별 Branch 생성
  ↓
개발·Commit·Push
  ↓
Issue Branch → dev Pull Request
  ↓
Review·수정·Merge
  ↓
dev 안정화
  ↓
dev → main Pull Request
  ↓
최종 Merge
```

## Branch 이름

```text
종류/이슈번호-영역-작업명
```

예시:

```text
feat/12-desktop-study-timer
feat/18-extension-tab-tracking
fix/24-server-session-error
feat/31-ai-screen-relevance
docs/40-common-workflow
```

## 종류

| 종류 | 용도 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `docs` | 문서 작성·수정 |
| `chore` | 설정·의존성·기타 작업 |
| `test` | 테스트 추가·수정 |

## Issue Branch 생성

작업 시작 전 `dev`를 최신 상태로 만든 후 Issue 번호를 포함한 Branch를 생성한다.

```bash
git checkout dev
git pull origin dev
git checkout -b feat/12-desktop-study-timer
```

## 작업 중 최신화

작업 중 `dev`에 변경 사항이 많이 반영되었다면 담당자와 확인 후 작업 Branch를 최신화한다.

```bash
git checkout dev
git pull origin dev
git checkout feat/12-desktop-study-timer
git merge dev
```

## 운영 규칙

- `main`과 `dev`에 직접 개발하지 않는다.
- `main`과 `dev`에 직접 Push하지 않는다.
- 하나의 Issue에는 하나의 작업 Branch만 사용한다.
- 하나의 Branch에는 하나의 Issue 작업만 포함한다.
- Issue Branch의 PR 대상은 `dev`다.
- `dev`의 PR 대상은 `main`이다.
- Review가 끝난 PR만 Merge한다.
- Branch 이름은 소문자와 하이픈을 사용한다.
- 다른 영역 파일을 함께 수정하면 PR 설명에 이유를 남긴다.

